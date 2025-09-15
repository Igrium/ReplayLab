package com.igrium.replaylab.ui;

import com.igrium.replaylab.playback.RealtimeScenePlayer;
import com.igrium.replaylab.scene.ReplayScene;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplayModReplay;
import imgui.type.ImInt;
import lombok.Getter;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages replay lab editor state. Implemented to try to separate editor global logic from UI for code cleanliness.
 * Scene-level operations are implemented in {@link ReplayScene}
 */
public class ReplayLabEditorState {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReplayLabEditorState.class);


    private static @Nullable ReplayHandler getReplayHandler() {
        return ReplayModReplay.instance.getReplayHandler();
    }

    private static ReplayHandler getReplayHandlerOrThrow() {
        var handler = getReplayHandler();
        if (handler == null) {
            throw new IllegalStateException("No replay handler.");
        }
        return handler;
    }


    @Getter
    private final ImInt playheadRef = new ImInt(0);

    public final int getPlayhead() {
        return playheadRef.get();
    }

    public final void setPlayhead(int playhead) {
        playheadRef.set(playhead);
    }

    @Getter @NonNull
    private ReplayScene scene = new ReplayScene();

    @Getter @Nullable
    private String sceneName;

    public void setScene(@NotNull ReplayScene scene) {
        // TODO: update logic
        this.scene = scene;
    }



    @Nullable
    private RealtimeScenePlayer scenePlayer;

    public void onPreRender() {
        if (scenePlayer != null && scenePlayer.isActive()) {
            setPlayhead(scenePlayer.getTimePassed());
        }
    }

    public void doTimeJump() {
        if (isPlaying()) {
            stopPlaying();
        }

        int replayTime = scene.sceneToReplayTime(getPlayhead());
        replayTime = Math.min(replayTime, getReplayHandlerOrThrow().getReplayDuration());
        getReplayHandlerOrThrow().doJump(replayTime, true);

    }

    public boolean isPlaying() {
        return scenePlayer != null && scenePlayer.isActive();
    }

    public void startPlaying(int startTimestamp) {
        if (isPlaying()) {
            LOGGER.warn("Replay is already playing!");
            return;
        }

        scenePlayer = new RealtimeScenePlayer(getReplayHandlerOrThrow());
        scenePlayer.setStartTimestamp(startTimestamp);

        scenePlayer.start(scene);
    }

    public void stopPlaying() {
        if (scenePlayer == null || !scenePlayer.isActive()) {
            LOGGER.warn("Replay is not playing!");
            return;
        }

        scenePlayer.stop();
    }

}
