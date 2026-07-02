package com.igrium.replaylab.editor;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.igrium.replaylab.ReplayLab;
import com.igrium.replaylab.camera.RollProvider;
import com.igrium.replaylab.math.Transform3;
import com.igrium.replaylab.mixin.AccessorReplayHandler;
import com.igrium.replaylab.operator.CommitObjectUpdateOperator;
import com.igrium.replaylab.operator.PasteKeyframesOperator;
import com.igrium.replaylab.operator.PasteObjectsOperator;
import com.igrium.replaylab.operator.ReplayOperator;
import com.igrium.replaylab.playback.AbstractScenePlayer;
import com.igrium.replaylab.playback.RealtimeScenePlayer;
import com.igrium.replaylab.render.VideoRenderer;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.ReplayScenes;
import com.igrium.replaylab.scene.key.KeyChannel;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.scene.obj.ReplayObject3D;
import com.igrium.replaylab.scene.obj.SerializedReplayObject;
import com.igrium.replaylab.scene.obj.TransformProvider;
import com.igrium.replaylab.ui.util.QuickModeInitCallback;
import com.igrium.replaylab.util.RenderUtils;
import com.replaymod.replay.QuickReplaySender;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplayModReplay;
import com.replaymod.replaystudio.replay.ReplayFile;
import imgui.type.ImInt;
import it.unimi.dsi.fastutil.floats.FloatConsumer;
import it.unimi.dsi.fastutil.ints.IntSet;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.Util;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Math;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Manages replay lab editor state. Implemented to try to separate editor global logic from UI for code cleanliness.
 * Scene-level operations are implemented in {@link ReplayScene}
 */
public final class EditorState {

    /// ===== Static Members =====

    private static final Logger LOGGER = ReplayLab.getLogger("ReplayLabEditorState");

    public static @Nullable ReplayHandler getReplayHandler() {
        return ReplayModReplay.instance.getReplayHandler();
    }

    public static ReplayHandler getReplayHandlerOrThrow() {
        var handler = getReplayHandler();
        if (handler == null) {
            throw new IllegalStateException("No replay handler.");
        }
        return handler;
    }

    // Replay mod does this so I have no choice
    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    private static void removeScene(ReplayFile file, String sceneName) throws IOException {
        synchronized (file) {
            file.remove(ReplayScenes.getSceneName(sceneName));
        }
    }

    /**
     * Get the current get the currently-open editor.
     * @apiNote Prefer passing editorState directly; primarily for use in mixins.
     */
    public static @Nullable EditorState getInstance() {
        var app = ReplayLab.getInstance().getAppInstance();
        return app != null ? app.getEditorState() : null;
    }

    /// ===== Fields =====

    private final MinecraftClient mc = MinecraftClient.getInstance();

    @Getter
    private final ImInt playheadRef = new ImInt(0);

    @Getter @NonNull
    private ReplayScene scene = new ReplayScene();

    @Getter @Setter @Nullable
    private String sceneName;

    /**
     * The current "active" object: what shows in the properties panel
     */
    @Getter @Setter @Nullable
    private String activeObject;

    /**
     * All selected objects in the scene: some operators will use these.
     */
    @Getter
    private final Set<String> selectedObjects = new HashSet<>();

    @Setter @Nullable
    private Consumer<? super Throwable> exceptionCallback;

    @Setter
    private @Nullable Consumer<? super ReplayOperator> operatorCallback;

    @Nullable
    private RealtimeScenePlayer scenePlayer;

    @Nullable
    private ScrubbingScenePlayer scrubbingScenePlayer;

    @Getter
    private boolean scrubbing;

    /**
     * When we're scrubbing without quick mode,
     * this is the position in the timeline that was last applied to the replay scene.
     */
    private int maxScrubForwardTime;

    @Getter
    private final List<String> scenes = Collections.synchronizedList(new ArrayList<>());

    /**
     * Whether the viewport is currently displaying the camera view
     */
    @Getter @Setter
    private boolean cameraView = true;

    /**
     * All keyframe (handles) which are currently selected
     */
    @Getter
    private final KeySelectionSet keySelection = new KeySelectionSet();

    /**
     * The video that's currently rendering.
     */
    @Getter @Setter @Nullable
    private VideoRenderer renderer;

    @Getter
    private boolean pilotingCamera;

    /**
     * If we're piloting the camera and rolling it
     */
    @Getter @Setter
    private boolean rollingCamera;

    @Getter
    @Accessors(fluent = true)
    private boolean wantsTimeJump;

    @Getter
    @Accessors(fluent = true)
    private boolean wantsApplyToGame;

    /**
     * If set, gizmos should display in local space rather than world space.
     */
    @Getter @Setter
    private boolean localGizmos;

    @Getter @Setter @Accessors(fluent = true)
    private boolean showGizmoPos = true;

    @Getter @Setter @Accessors(fluent = true)
    private boolean showGizmoRot;

    @Getter @Setter @Accessors(fluent = true)
    private boolean showGizmoScale;

    @Getter @Setter
    private boolean wantOpenInspector;

    @Setter @Nullable
    private QuickModeInitCallback quickModeInitCallback;

    /// ===== Constructors =====
    public EditorState() {
        scene.setExceptionCallback(this::onException);
    }

    /// ===== Getters/Setters =====

    public int getPlayhead() {
        return playheadRef.get();
    }

    public void setPlayhead(int playhead) {
        playheadRef.set(playhead);
    }

    public void queueTimeJump() {
        wantsTimeJump = true;
    }

    public void queueApplyToGame() {
        wantsApplyToGame = true;
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


    public boolean isObjectActive(String objId) {
        return activeObject != null && activeObject.equals(objId);
    }

    /**
     * Check if a given object is currently selected.
     * @param objId The object ID to test.
     * @return <code>true</code> if the object is selected.
     */
    public boolean isObjectSelected(String objId) {
        return selectedObjects.contains(objId);
    }

    /**
     * Check if a given object is currently selected.
     * @param replayObject The object to test.
     * @return <code>true</code> if the object is selected.
     * @apiNote This method also checks if the target object is in the current scene.
     */
    public boolean isObjectSelected(ReplayObject replayObject) {
        String id = replayObject.getId();
        return (id != null && replayObject.getScene() == getScene() && isObjectSelected(id));
    }

    /**
     * Set an object's selection state.
     * @param objId The object ID.
     * @param selected Selection state to set.
     * @return Whether the object was previously selected.
     */
    public boolean setObjectSelected(String objId, boolean selected) {
        if (selected) {
            return !selectedObjects.add(objId); // If add was successful, it wasn't previously selected.
        } else {
            return selectedObjects.remove(objId);
        }
    }

    /**
     * Toggles the visibility of position, rotation, and scale gizmos.
     * <p>
     * If the requested gizmo configuration matches the current state, all gizmos are hidden.
     * Otherwise, the specified gizmos are shown and others are hidden.
     *
     * @param pos   whether to show the position gizmo
     * @param rot   whether to show the rotation gizmo
     * @param scale whether to show the scale gizmo
     */
    public void toggleGizmos(boolean pos, boolean rot, boolean scale) {
        boolean turnOff = pos == showGizmoPos && rot == showGizmoRot && scale == showGizmoScale;

        showGizmoPos = pos && !turnOff;
        showGizmoRot = rot && !turnOff;
        showGizmoScale = scale && !turnOff;
    }

    public boolean isQuickMode() {
        // While this CAN be static, the fact that ReplayHandler is static is a hack, and I'd rather not expose that.
        ReplayHandler replayHandler = getReplayHandler();
        return replayHandler != null && replayHandler.isQuickMode();
    }

    public void setQuickMode(boolean quickMode) {
        ReplayHandler replayHandler = getReplayHandler();
        if (replayHandler == null) {
            LOGGER.warn("No replay handler found; unable to set quick mode.");
            return;
        }

        if (quickModeInitCallback != null) {
            quickModeInitCallback.openPopup();
        }

        replayHandler.getReplaySender().setSyncModeAndWait();

        ensureQuickModeEnabled(progress -> {
            if (quickModeInitCallback != null) quickModeInitCallback.onProgress(progress);
        }).thenRun(() -> {
            ReplayHandler handler = getReplayHandler();
            handler.setQuickMode(quickMode);
            handler.getReplaySender().setAsyncMode(true);
        }).whenCompleteAsync((result, e) -> {
            if (quickModeInitCallback != null) {
                quickModeInitCallback.closePopup();
            }
            if (e != null) {
                LOGGER.error("Failed to set quick mode.", e);
                onException(e);
            }
            doTimeJump();
        }, RenderUtils::onRenderThread);
    }


    /// ===== Scene Management =====

    public ReplayScene newScene(String sceneName) {
        ReplayScene scene = new ReplayScene();
        setScene(scene, sceneName);

        // TODO: create camera and teleport to player

        try {
            saveScene();
            LOGGER.info("Created new scene: {}", sceneName);
        } catch (IOException e) {
            LOGGER.info("Error saving scene.");
            onException(e);
        }
        refreshSceneListSync();
        return scene;
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
            String camId = scene.getSceneProps().getCameraObject();
            selectedObjects.add(camId);
            setActiveObject(camId);
            return scene;
        } catch (Exception e) {
            LOGGER.error("Error loading scene {}: ", sceneName, e);
            onException(e);
            return null;
        }
    }

    public List<String> refreshSceneListSync() {
        var handler = getReplayHandler();
        if (handler != null) {
            scenes.clear();
            try {
                scenes.addAll(ReplayScenes.listScenes(handler.getReplayFile()).toList());
            } catch (IOException e) {
                LOGGER.error("Error loading scenes from replay file: ", e);
                onException(e);
            }
        }
        return scenes;
    }

    public CompletableFuture<List<String>> refreshSceneListAsync() {
        return CompletableFuture.supplyAsync(this::refreshSceneListSync, Util.getIoWorkerExecutor());
    }

    public void renameScene(String oldName, String newName) throws IOException {
        if (oldName.equals(getSceneName())) {
            renameCurrentScene(newName);
            return;
        }
        if (newName.equals(getSceneName())) {
            throw new IllegalArgumentException("Can't rename scene to the current scene name.");
        }

        ReplayFile file = getReplayHandlerOrThrow().getReplayFile();
        synchronized (file) {
            var opt = file.get(ReplayScenes.getScenePath(oldName));
            if (!opt.isPresent()) {
                throw new FileNotFoundException("Unknown scene: " + oldName);
            }

            try (InputStream in = opt.get()) {
                try (OutputStream out = file.write(ReplayScenes.getScenePath(newName))) {
                    in.transferTo(out);
                }
            }
        }

        file.remove(ReplayScenes.getScenePath(oldName));
    }

    public void tryRenameScene(String oldName, String newName) {
        try {
            renameScene(oldName, newName);
            LOGGER.info("Renamed scene {} to {}.", oldName, newName);
        } catch (Exception e) {
            LOGGER.error("Error renaming scene", e);
            onException(e);
        }
        refreshSceneListSync();
    }

    public void renameCurrentScene(String newName) throws IOException {
        if (newName.equals(getSceneName())) return;
        ReplayFile file = getReplayHandlerOrThrow().getReplayFile();
        String oldName = getSceneName();

        setSceneName(newName);
        synchronized (file) {
            saveScene();

            if (oldName != null) {
                file.remove(ReplayScenes.getScenePath(oldName));
            }
            refreshSceneListSync();
        }
    }

    public void removeScene(String sceneName) {
        if (sceneName.equals(getSceneName())) {
            LOGGER.warn("Deleting current scene from disk. Will get replaced on save.");
        }
        try {
            removeScene(getReplayHandlerOrThrow().getReplayFile(), sceneName);
        } catch (Exception e) {
            LOGGER.error("Error deleting scene {}", sceneName, e);
            onException(e);
        }
        refreshSceneListSync();
    }

    /// ===== Playback =====

    public void onPreRender() {
        if (scenePlayer != null && scenePlayer.isActive()) {
            setPlayhead(scenePlayer.getTimePassed());
        }
        if (scene.getSceneCameraObject() == null) {
            setCameraView(false);
        }
        if (isCameraView()) {

            scene.spectateCamera();

            Entity cameraEnt = scene.getSceneCamera();
            ReplayObject cameraObj = scene.getSceneCameraObject();

            ClientPlayerEntity player = mc.player;

            // Pilot the camera if cursor is locked
            if (mc.mouse.isCursorLocked() && cameraObj instanceof ReplayObject3D cam3d && player != null) {
                pilotingCamera = true;
                double posX = player.getX();
                double posY = player.getEyeY();
                double posZ = player.getZ();

                float roll = cameraEnt instanceof RollProvider r ? r.getRoll() : 0;

                cam3d.position().set(posX, posY, posZ);
                cam3d.rotation().setEulerYXZ(Math.toRadians(-player.getYaw()), Math.toRadians(player.getPitch()), Math.toRadians(roll));
                cam3d.apply(getPlayhead());

            }
            else if (pilotingCamera && cameraObj != null) {
                // Apply camera move
                pilotingCamera = false;
                applyOperator(new CommitObjectUpdateOperator(false, cameraObj.getId()));
            }
            else if (isCameraView() && player != null && cameraObj instanceof ReplayObject3D cam3d) {
                // TP player to camera, taken directly from the replay object rather than going through camera ent,
                // which has been through quaternion.
                Vector3f euler = cam3d.rotation().getEulerYXZ(new Vector3f());
                player.refreshPositionAndAngles(cam3d.position().x,
                        cam3d.position().y - player.getStandingEyeHeight(), cam3d.position().z,
                        -Math.toDegrees(euler.y), Math.toDegrees(euler.x));
            }

        } else {
            pilotingCamera = false;
            MinecraftClient.getInstance().setCameraEntity(null);
        }
    }

    public void togglePlayback() {
        if (isPlaying()) {
            stopPlaying();
        } else {
            startPlaying(getPlayhead());
        }
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

    public void afterOpen() {
        var scenes = refreshSceneListSync();
        if (!scenes.isEmpty()) {
            loadScene(scenes.getFirst());
        } else {
            setSceneName("Scene");
        }

        MinecraftClient.getInstance().send(() -> {
            startPlaying(0);
        });
    }


    public void doTimeJump() {
        if (isPlaying()) {
            stopPlaying();
        }

        int replayTime = scene.sceneToReplayTime(getPlayhead());
        replayTime = Math.min(replayTime, getReplayHandlerOrThrow().getReplayDuration());
        if (replayTime < 0)
            replayTime = 0;

        getReplayHandlerOrThrow().doJump(replayTime, true);

        MinecraftClient.getInstance().send(this::applyToGame);
        wantsTimeJump = false;
    }

    /**
     * Called when the playhead is being scrubbed
     * @param drop If the playhead was dropped this frame
     */
    public void scrub(boolean drop) {

        if (drop) {
            // Scrub finished: tear down the player
            if (scrubbingScenePlayer != null) {
                scrubbingScenePlayer.stop();
                scrubbingScenePlayer = null;
            }
            maxScrubForwardTime = 0;
            queueTimeJump();
            queueApplyToGame();

        } else if (getPlayhead() >= maxScrubForwardTime) {
            // Scrubbing forward: playhead is at or ahead of the furthest position seen.
            if (!isScrubbing()) {
                doTimeJump();
            }
            if (scrubbingScenePlayer == null) {
                scrubbingScenePlayer = new ScrubbingScenePlayer();
                scrubbingScenePlayer.start(getScene());
            }
            maxScrubForwardTime = getPlayhead();

        } else {
            // Scrubbing backward: the scene player can't go backwards, so stop it
            if (scrubbingScenePlayer != null) {
                scrubbingScenePlayer.stop();
                scrubbingScenePlayer = null;
            }
            // If we're in quick mode, we can time-jump to immediately.
            if (isQuickMode()) {
                queueTimeJump();
                maxScrubForwardTime = getPlayhead();
            }
        }

        scrubbing = !drop;
    }

    public void jumpSceneStart() {
        setPlayhead(0);
        queueTimeJump();
    }

    public void jumpSceneEnd() {
        setPlayhead(getScene().getLength());
        queueTimeJump();
    }

    public void jumpPrevKeyframe() {
        // TODO: implement
    }

    public void jumpNextKeyframe() {
        // TODO: implement
    }

    /// ===== Game Integration =====

    /**
     * Sample and all animated properties to the game.
     */
    public void applyToGame() {
        getScene().applyToGame(getPlayhead());
        wantsApplyToGame = false;
    }

    /**
     * Apply all animated properties to the game.
     * @param shouldSample <code>true</code> if we should re-sample timelines
     */
    public void applyToGame(boolean shouldSample) {
        getScene().applyToGame(getPlayhead(), shouldSample);
    }

    /**
     * Apply all animated properties to the game.
     * @param shouldSample <code>true</code> if a given object should be sampled as it's applied.
     */
    public void applyToGame(Predicate<? super ReplayObject> shouldSample) {
        getScene().applyToGame(shouldSample, getPlayhead());
        wantsApplyToGame = false;
    }

    @Deprecated
    private void spectateCamera() {
        scene.spectateCamera();
    }

    /**
     * Get the entity responsible for providing the camera view on a given frame.
     * @param timestamp Timestamp to use.
     * @return The scene camera entity. if there is any at that timestamp.
     */
    @Deprecated
    public @Nullable Entity getSceneCamera(int timestamp) {
        return scene.getSceneCamera();
    }

    public void snapViewportToSelected() {
        ClientPlayerEntity player = mc.player;
        if (player == null || selectedObjects.isEmpty()) return;

        int count = 0;
        Vector3d center = new Vector3d();

        for (String objId : selectedObjects) {
            ReplayObject obj = scene.getObject(objId);
            if (obj instanceof TransformProvider tProv) {
                center.add(tProv.getTransform(new Transform3()).pos());
                count++;
            }
        }

        if (count == 0) return;
        center.div(count);

        snapViewportTo(center);
    }

    public void snapViewportTo(Vector3dc target) {
        snapViewportTo(target.x(), target.y(), target.z());
    }

    public void snapViewportTo(double x, double y, double z) {
        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        Vec3d forward = player.getRotationVector();
        Vector3d vec = new Vector3d(forward.x, forward.y, forward.z);

        vec.mul(-4);
        vec.add(x, y, z);

        player.refreshPositionAndAngles(vec.x, vec.y - player.getStandingEyeHeight(), vec.z,
                player.getYaw(), player.getPitch());
    }

    public CompletableFuture<?> ensureQuickModeEnabled(@Nullable FloatConsumer progressConsumer) {
        CompletableFuture<?> future = new CompletableFuture<>();
        ReplayHandler handler = getReplayHandlerOrThrow();
        QuickReplaySender quickReplaySender = ((AccessorReplayHandler) handler).getQuickReplaySender();
        ListenableFuture<Void> lFuture = quickReplaySender.getInitializationPromise();


        if (lFuture == null) {
            lFuture = quickReplaySender.initialize(progress -> {
                if (progressConsumer != null) progressConsumer.accept(progress.floatValue());
            });
        }

        Futures.addCallback(lFuture, new FutureCallback<>() {
            @Override
            public void onSuccess(@Nullable Void result) {
                future.complete(null);
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                future.completeExceptionally(t);
            }
        }, Runnable::run);

        return future;
    }

    /// ===== Operators & Undo/Redo =====

    public boolean applyOperator(ReplayOperator operator) {
        return applyOperator(operator, true);
    }

    public boolean applyOperator(ReplayOperator operator, boolean applyToGame) {
        if (scene.applyOperator(this, operator)) {
            saveSceneAsync();
            if (applyToGame) {
                applyToGame(operator.wantsSampleCurves());
            }
            if (operatorCallback != null) {
                operatorCallback.accept(operator);
            }
            return true;
        }
        return false;
    }

    public boolean undo() {
        ReplayOperator op = scene.undo(this);
        if (op != null) {
            saveSceneAsync();
            applyToGame(op.wantsSampleCurves());
            if (operatorCallback != null) {
                operatorCallback.accept(op);
            }
            return true;
        }
        return false;
    }

    public boolean redo() {
        ReplayOperator op = scene.redo(this);
        if (op != null) {
            saveSceneAsync();
            applyToGame(op.wantsSampleCurves());
            if (operatorCallback != null) {
                operatorCallback.accept(op);
            }
            return true;
        }
        return false;
    }

    public String copyKeyframes() {

        Map<KeySelectionSet.ChannelReference, JsonArray> arrays = new HashMap<>();

        for (var entry : getKeySelection().getSelectedChannels().entrySet()) {
            for (var chName : entry.getValue()) {
                var chRef = new KeySelectionSet.ChannelReference(entry.getKey(), chName);
                KeyChannel chan = chRef.get(getScene().getObjects());
                if (chan == null) continue;

                IntSet selected = keySelection.getSelectedKeyframes(chRef.objectName(), chName);
                arrays.put(chRef, chan.copyToClipboard(getPlayhead(), selected));
            }
        }
        return new GsonBuilder().enableComplexMapKeySerialization().create().toJson(arrays);
    }


    public void pasteKeyframes(String clipboard) {
        if (clipboard.isBlank()) return;

        PasteKeyframesOperator op = PasteKeyframesOperator.create(clipboard, this::onException);
        if (op != null) {
            applyOperator(op);
        }
    }

    public String copyObjects() {
        Map<String, SerializedReplayObject> objects = new HashMap<>();
        for (var objId : selectedObjects) {
            ReplayObject obj = scene.getObject(objId);
            if (obj == null) continue;

            objects.put(objId, obj.save());
        }
        return new GsonBuilder().enableComplexMapKeySerialization().create().toJson(objects);
    }

    public void pasteObjects(String clipboard) {
        if (clipboard.isBlank()) return;

        PasteObjectsOperator op = PasteObjectsOperator.create(clipboard, this::onException);
        if (op != null) {
            applyOperator(op);
        }
    }

    /// ===== Saving =====

    /**
     * Save the active scene to file.
     * @throws IllegalStateException If the current scene doesn't have a name.
     * @throws IOException If an IO exception occurs saving the scene.
     */
    public void saveScene() throws IllegalStateException, IOException  {
        String name = getSceneName();
        LOGGER.info("Saving scene '{}'", name);
        if (name == null) {
            throw new IllegalStateException("Scene does not have a name!");
        }

        ReplayScenes.saveScene(scene, name, getReplayHandlerOrThrow().getReplayFile());
        refreshSceneListSync();
    }

    public CompletableFuture<?> saveSceneAsync() {
        return CompletableFuture.runAsync(() -> {
            if (getSceneName() == null) {
                LOGGER.warn("Scene does not have a name. Skipping save...");
                return;
            }
            try {
                saveScene();
            } catch (Exception e) {
                LOGGER.debug("Error saving scene {}", sceneName, e);
                onException(e);
            }
        }, Util.getIoWorkerExecutor());
    }



    /// ===== Error Handling =====

    public void onException(Throwable e) {
        if (exceptionCallback != null) {
            exceptionCallback.accept(e);
        }
    }

    /// === Rendering ===

    public boolean isRendering() {
        return getRenderer() != null;
    }

    /**
     * Render the scene using the render settings provided by {@link ReplayScene#getRenderSettings()}
     */
    public void render() {
        if (isRendering()) {
            LOGGER.warn("Already rendering!");
            return;
        }
        renderer = VideoRenderer.create(getScene());
        try {
            renderer.render();
        } catch (Exception e) {
            LOGGER.error("Error exporting video", e);
            onException(e);
        } finally {
            renderer = null;
        }

    }

    private class ScrubbingScenePlayer extends AbstractScenePlayer {


        public ScrubbingScenePlayer() {
            super(getReplayHandlerOrThrow());
        }

        @Override
        public int getTimePassed() {
            int time = Math.max(maxScrubForwardTime, getPlayhead());
            maxScrubForwardTime = time;
            return time;
        }
    }
}