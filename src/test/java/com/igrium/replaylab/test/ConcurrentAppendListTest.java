package com.igrium.replaylab.test;

import com.igrium.replaylab.util.ConcurrentAppendList;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class ConcurrentAppendListTest {

    @Test
    public void sequentialAppendAndGet() {
        ConcurrentAppendList<Integer> list = new ConcurrentAppendList<>();
        // Enough elements to cross several block boundaries
        for (int i = 0; i < 100_000; i++) {
            assertTrue(list.compareAndAdd(i, i * 3), "add failed at " + i);
        }
        assertEquals(100_000, list.size());
        for (int i = 0; i < 100_000; i++) {
            assertEquals(i * 3, list.get(i));
        }
    }

    @Test
    public void staleExpectedSizeFails() {
        ConcurrentAppendList<Integer> list = new ConcurrentAppendList<>();
        assertTrue(list.compareAndAdd(0, 10));
        assertTrue(list.compareAndAdd(1, 20));

        assertFalse(list.compareAndAdd(0, 99));
        assertFalse(list.compareAndAdd(5, 99));
        assertFalse(list.compareAndCompute(1, i -> 99));
        assertEquals(2, list.size());
        assertEquals(10, list.get(0));
        assertEquals(20, list.get(1));
    }

    @Test
    public void boundsChecks() {
        ConcurrentAppendList<Integer> list = new ConcurrentAppendList<>();
        list.compareAndAdd(0, 1);
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(1));
    }

    @Test
    public void addAndSet() {
        ConcurrentAppendList<Integer> list = new ConcurrentAppendList<>();
        for (int i = 0; i < 1000; i++) {
            assertEquals(i, list.add(i));
        }
        assertEquals(500, list.set(500, -1));
        assertEquals(-1, list.get(500));
        assertEquals(1000, list.size());
        assertThrows(IndexOutOfBoundsException.class, () -> list.set(1000, 5));
        assertThrows(IndexOutOfBoundsException.class, () -> list.set(-1, 5));
    }

    @Test
    public void computeUpToFillsRange() {
        ConcurrentAppendList<Integer> list = new ConcurrentAppendList<>();
        assertEquals(5_000, list.computeUpTo(1_000, i -> i * 5));
        assertEquals(1_001, list.size());
        for (int i = 0; i <= 1_000; i++) {
            assertEquals(i * 5, list.get(i));
        }
        // Already-committed index: pure read, computeFn must not run
        assertEquals(2_500, list.computeUpTo(500, i -> fail("computeFn ran for committed index")));
        assertThrows(IndexOutOfBoundsException.class, () -> list.computeUpTo(-1, i -> 0));
    }

    /**
     * Threads race computeUpTo on overlapping, interleaved target indices; every index must be computed
     * consistently and committed exactly once.
     */
    @Test
    public void concurrentComputeUpTo() throws Exception {
        final int threads = 8;
        final int target = 100_000;

        ConcurrentAppendList<Integer> list = new ConcurrentAppendList<>();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);

        for (int t = 0; t < threads; t++) {
            final int stride = t + 1;
            pool.submit(() -> {
                start.await();
                for (int i = stride; i < target; i += stride) {
                    assertEquals(i * 7, list.computeUpTo(i, x -> x * 7));
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "test timed out");

        assertEquals(target, list.size());
        for (int i = 0; i < target; i++) {
            assertEquals(i * 7, list.get(i));
        }
    }

    @Test
    public void toArraySnapshot() {
        ConcurrentAppendList<Integer> list = new ConcurrentAppendList<>();
        for (int i = 0; i < 10_000; i++) {
            list.add(i * 2);
        }
        Integer[] arr = list.toArray(Integer[]::new);
        assertEquals(10_000, arr.length);
        for (int i = 0; i < arr.length; i++) {
            assertEquals(i * 2, arr[i]);
        }
        Object[] objs = list.toArray();
        assertEquals(10_000, objs.length);
        assertEquals(0, objs[0]);
        assertEquals(19_998, objs[9_999]);
    }

    /**
     * Unconditional adds from many threads: every value lands exactly once at some index.
     */
    @Test
    public void concurrentAdds() throws Exception {
        final int threads = 8;
        final int perThread = 25_000;

        ConcurrentAppendList<Integer> list = new ConcurrentAppendList<>();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);

        for (int t = 0; t < threads; t++) {
            final int base = t * perThread;
            pool.submit(() -> {
                start.await();
                for (int i = 0; i < perThread; i++) {
                    list.add(base + i);
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "test timed out");

        assertEquals(threads * perThread, list.size());
        boolean[] seen = new boolean[threads * perThread];
        for (Object o : list.toArray()) {
            int v = (Integer) o;
            assertFalse(seen[v], "duplicate value " + v);
            seen[v] = true;
        }
    }

    /**
     * N threads race to fill the list via compareAndCompute while also reading the committed prefix.
     * Every index must be committed exactly once with the winning thread's value.
     */
    @Test
    public void concurrentRacingAppends() throws Exception {
        final int threads = 8;
        final int target = 200_000;

        ConcurrentAppendList<Integer> list = new ConcurrentAppendList<>();
        AtomicInteger commits = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                start.await();
                while (true) {
                    int s = list.size();
                    if (s >= target) break;
                    if (list.compareAndCompute(s, i -> i * 7)) {
                        commits.incrementAndGet();
                    }
                    // Hammer reads on the committed prefix while writes are in flight
                    int sz = list.size();
                    if (sz > 0) {
                        assertEquals((sz - 1) * 7, list.get(sz - 1), "torn read");
                    }
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "test timed out");

        assertEquals(target, list.size());
        assertEquals(target, commits.get(), "an index was committed more or less than once");
        for (int i = 0; i < target; i++) {
            assertEquals(i * 7, list.get(i));
        }
    }
}
