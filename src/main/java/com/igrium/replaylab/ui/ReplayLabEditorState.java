package com.igrium.replaylab.ui;

import com.igrium.replaylab.playback.RealtimeScenePlayer;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.ReplayScenes;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplayModReplay;
import imgui.type.ImInt;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.minecraft.util.Util;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

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

    @Getter @Setter @Nullable
    private String sceneName;

    @Getter @Setter @Nullable
    private String selectedObject;

    @Setter @Nullable
    private Consumer<? super Throwable> exceptionCallback;

    /**
     * All the scenes in the current replay file.
     */
    @Getter
    private final List<String> scenes = Collections.synchronizedList(new ArrayList<>());

    public ReplayLabEditorState() {
        scene.setExceptionCallback(this::onException);
        refreshSceneListAsync();
    }

    public CompletableFuture<?> refreshSceneListAsync() {
        var handler = getReplayHandler();
        if (handler != null) {
            return ReplayScenes.listScenesAsync(handler.getReplayFile()).whenComplete((res, e) -> {
                if (res != null) {
                    scenes.clear();
                    scenes.addAll(res);
                } else if (e != null) {
                    LOGGER.error("Error loading scenes from replay file: ", e);
                    onException(e);
                }
            });
        } else {
            LOGGER.error("Unable to load scene list: no replay handler");
            return CompletableFuture.completedFuture(null);
        }
    }

    public void setScene(@NotNull ReplayScene scene) {
        if (this.scene == scene) return;

        this.scene.setExceptionCallback(null);
        this.scene = scene;
        this.scene.setExceptionCallback(this::onException);
    }

    public void setScene(@NonNull ReplayScene scene, String sceneName) {
        setScene(scene);
        setSceneName(sceneName);
    }

    public CompletableFuture<ReplayScene> newScene(String sceneName) {
        ReplayScene scene = new ReplayScene();
        setScene(scene, sceneName);
        return saveSceneAsync().thenCompose(v -> refreshSceneListAsync()).thenApply(v -> scene);
    }

    /**
     * Attempt to load a scene from the replay file
     * @param sceneName Scene to load.
     * @return The loaded scene, or <code>null</code> if the scene failed to load.
     */
    public @Nullable ReplayScene loadScene(String sceneName) {
        try {
            var scene = ReplayScenes.readScene(sceneName, getReplayHandlerOrThrow().getReplayFile(), this::onException);
            setScene(scene, sceneName);
            return scene;
        } catch (Exception e) {
            LOGGER.error("Error loading scene {}: ", sceneName, e);
            onException(e);
            return null;
        }
    }

    private void onException(Throwable e) {
        if (exceptionCallback != null) {
            exceptionCallback.accept(e);
        }
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

    /**
     * Save the active scene to file.
     * @throws IllegalStateException If the current scene doesn't have a name.
     * @throws IOException If an IO exception occurs saving the scene.
     */
    public void saveScene() throws IllegalStateException, IOException  {
        String name = getSceneName();
        if (name == null) {
            throw new IllegalStateException("Scene does not have a name!");
        }

        ReplayScenes.saveScene(scene, name, getReplayHandlerOrThrow().getReplayFile());
        refreshSceneListAsync();
    }


    public CompletableFuture<?> saveSceneAsync() {
        return CompletableFuture.runAsync(() -> {
            try {
                saveScene();
            } catch (Exception e) {
                LOGGER.error("Error saving scene {}", sceneName, e);
                onException(e);
            }
        }, Util.getIoWorkerExecutor());
    }
}
