package com.igrium.replaylab.playback;

import com.igrium.replaylab.scene.ReplayScene;
import com.replaymod.core.mixin.MinecraftAccessor;
import com.replaymod.core.mixin.TimerAccessor;
import com.replaymod.core.utils.Utils;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.EventRegistrations;
import com.replaymod.pathing.player.ReplayTimer;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplaySender;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderTickCounter;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Reimplementation of AbstractTimelinePlayer for ReplayLab.
 */
public abstract class AbstractScenePlayer extends EventRegistrations {
    private final ReplayHandler replayHandler;
    private ReplayScene scene;

    private boolean wasAsyncMode;

    private RenderTickCounter.Dynamic origTimer;

    /**
     * The previously processed replay time during timeline playback
     */
    private long prevReplayTime;

    /**
     * The maximum keyframe timestamp across all paths.
     */
    private int maxTimestamp;

    /**
     * A future that completes once we reach the end of the timeline.
     */
    @Getter @Nullable
    private CompletableFuture<Void> future;

    public AbstractScenePlayer(@NonNull ReplayHandler replayHandler) {
        this.replayHandler = replayHandler;
    }

    public CompletableFuture<Void> start(@NonNull ReplayScene scene) {
        this.scene = scene;
        maxTimestamp = scene.getLength();

        wasAsyncMode = replayHandler.getReplaySender().isAsyncMode();
        replayHandler.getReplaySender().setSyncModeAndWait();

        register();
        prevReplayTime = 0;

        var mcA = (MinecraftAccessor) MinecraftClient.getInstance();
        origTimer = mcA.getTimer();
        ReplayTimer timer = new ReplayTimer();
        mcA.setTimer(timer);

        TimerAccessor timerA = (TimerAccessor) timer;
        timerA.setTickLength(Utils.DEFAULT_MS_PER_TICK);
        timer.tickDelta = timer.ticksThisFrame = 0;

        return future = new CompletableFuture<>();
    }

    public void stop() {
        if (future != null) {
            future.cancel(false);
        }
    }

    /**
     * <code>true</code> if the player has started playing and hasn't reached the end.
     */
    public boolean isActive() {
        return future != null && !future.isDone();
    }

    { on(ReplayTimer.UpdatedCallback.EVENT, this::onTick); }

    public void onTick() {
        if (future != null && future.isDone()) {
            // Restore state
            var mcA = (MinecraftAccessor) MinecraftClient.getInstance();
            mcA.setTimer(origTimer);
            replayHandler.getReplaySender().setReplaySpeed(0);
            if (wasAsyncMode) {
                replayHandler.getReplaySender().setAsyncMode(true);
            }
            unregister();
            return;
        }

        int timestamp = getTimePassed();
        if (timestamp > maxTimestamp) {
            timestamp = maxTimestamp;
        }


        // Apply timestamp to game
        ReplaySender sender = replayHandler.getReplaySender();
        int replayTime = scene.sceneToReplayTime(timestamp);

        replayTime = Math.min(replayTime, replayHandler.getReplayDuration());

        if (sender.isAsyncMode()) {
            sender.jumpToTime(replayTime);
        } else {
            sender.sendPacketsTill(replayTime);
        }

        scene.applyToGame(timestamp);

        if (prevReplayTime == 0) {
            prevReplayTime = replayTime; // first frame
        }

        float timeInTicks = replayTime / 50f;
        float prevTimeInTicks = prevReplayTime / 50f;
        float passedTicks = timeInTicks - prevTimeInTicks;

        RenderTickCounter rTickCounter = ((MinecraftAccessor) MinecraftClient.getInstance()).getTimer();
        if (rTickCounter instanceof ReplayTimer timer) {
            timer.tickDelta += passedTicks;
            timer.ticksThisFrame = (int) timer.tickDelta;
            timer.tickDelta -= timer.ticksThisFrame;
        }

        prevReplayTime = replayTime;

        // Complete playback
        if (timestamp >= maxTimestamp) {
            future.complete(null);
        }
    }

    public abstract int getTimePassed();
}
