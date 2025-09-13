package com.igrium.replaylab.playback;

import com.igrium.replaylab.scene.ReplayScene;
import com.replaymod.replay.ReplayHandler;
import lombok.NonNull;
import lombok.Setter;
import net.minecraft.client.MinecraftClient;

import java.util.concurrent.CompletableFuture;

/**
 * Scene player using the system time.
 */
public class RealtimeScenePlayer extends AbstractScenePlayer {

    /**
     * Whether the next frame is the first frame.
     * We only start measuring time from the second frame
     * as the first might have to jump in time which might take time.
     */
    private boolean firstFrame;
    private boolean secondFrame;

    private long startSystemTime;

    private boolean loadingResources;
    private int timeBeforeResourceLoading;

    @Setter
    private int startTimestamp;

    public RealtimeScenePlayer(@NonNull ReplayHandler replayHandler) {
        super(replayHandler);
        if (startTimestamp < 0) {
            throw new IllegalArgumentException("startTimestamp must not be negative");
        }
    }

    @Override
    public CompletableFuture<Void> start(@NonNull ReplayScene scene) {
        firstFrame = true;
        loadingResources = false;
        return super.start(scene);
    }

    @Override
    public void onTick() {
        if (secondFrame) {
            secondFrame = false;
            startSystemTime = System.currentTimeMillis() - startTimestamp;
        }

        if (MinecraftClient.getInstance().getOverlay() != null) {
            if (!loadingResources) {
                timeBeforeResourceLoading = getTimePassed();
                loadingResources = true;
            }
            super.onTick();
            return;
        } else if (loadingResources && !firstFrame) {
            startSystemTime = System.currentTimeMillis() - timeBeforeResourceLoading;
            loadingResources = false;
        }

        super.onTick();

        if (firstFrame) {
            firstFrame = false;
            secondFrame = true;
        }
    }

    @Override
    public int getTimePassed() {
        if (firstFrame) return startTimestamp;
        if (loadingResources) return timeBeforeResourceLoading;
        return (int) (System.currentTimeMillis() - startSystemTime);
    }
}
