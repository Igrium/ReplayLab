package com.igrium.replaylab.ui;


import com.igrium.craftui.app.DockSpaceApp;
import com.igrium.craftui.util.RaycastUtils;
import com.igrium.replaylab.ReplayLab;
import com.igrium.replaylab.config.Keybinds;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.operator.InsertKeyframeOperator;
import com.igrium.replaylab.operator.RemoveObjectOperator;
import com.igrium.replaylab.operator.RemoveObjectsOperator;
import com.igrium.replaylab.render.VideoRenderSettings;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.scene.objs.CameraObject;
import com.igrium.replaylab.scene.objs.ScenePropsObject;
import com.igrium.replaylab.ui.panels.*;
import com.igrium.replaylab.ui.util.ExceptionPopup;
import com.igrium.replaylab.ui.util.ReplayLabControls;
import com.igrium.replaylab.ui.gizmos.GizmoRenderer;
import com.igrium.replaylab.util.ShortcutUtils;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplayModReplay;
import imgui.ImGui;
import imgui.flag.*;
import imgui.type.ImBoolean;
import lombok.Getter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.util.Window;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.List;

/**
 * The main CraftApp for the replay lab editor.
 */
public class ReplayLabUI extends DockSpaceApp {

    // =========================================================================
    // Constants
    // =========================================================================

    private static final Logger LOGGER = ReplayLab.getLogger();
    private static final Identifier LAYOUT = Identifier.of("replaylab:replaylab");

    @Getter
    private static final Matrix4f viewMatrix = new Matrix4f();
    @Getter
    private static final Matrix4f projectionMatrix = new Matrix4f();

    // =========================================================================
    // Core state
    // =========================================================================

    @Getter
    private final EditorState editorState = new EditorState();

    @Getter
    private final ExceptionPopup exceptionPopup = new ExceptionPopup();

    // =========================================================================
    // UI panels
    // =========================================================================

    private final List<UIPanel> panels = List.of(
            new DopeSheetNew(Identifier.of("replaylab:dopesheet")),
            new CurveEditor(Identifier.of("replaylab:curveeditor")),
            new Outliner(Identifier.of("replaylab:outliner")),
            new Inspector(Identifier.of("replaylab:inspector")),
            new ScenePropsPanel(Identifier.of("replaylab:sceneprops"))
    );

    @Getter
    private final SceneBrowser sceneBrowser = new SceneBrowser(Identifier.of("replaylab:scenebrowser"));

    @Getter
    private final SettingsWindow settingsWindow = new SettingsWindow(Identifier.of("replaylab:settings"));


    // =========================================================================
    // Deferred action flags (set during render, executed in preRender)
    // =========================================================================

    private boolean wantsExit;

    // =========================================================================
    // UI state
    // =========================================================================

    /**
     * Hacky fix for the fact that exceptionPopup seems to crash the game if shown on the first frame.
     */
    private boolean firstFrame;

    /**
     * The height of the viewport footer on the previous frame. Used when adjusting the viewport bounds.
     */
    private float viewportFooterHeight;

    private float prevCameraControlsGroupWidth = 0;

    private final VideoRenderSettings tmpExportSettings = new VideoRenderSettings();

    // =========================================================================
    // Constructor & lifecycle
    // =========================================================================

    public ReplayLabUI() {
        setViewportInputMode(ViewportInputMode.HOLD);
        setViewportInputButtons(1);
        editorState.setExceptionCallback(exceptionPopup::displayException);
    }

    /**
     * Called directly after ReplayLab has been opened.
     */
    public void afterOpen() {
        firstFrame = true;
        editorState.afterOpen();
    }

    @Override
    protected void preRender(MinecraftClient client) {
        super.preRender(client);

        if (getReplayHandler() == null) {
            close();
            return;
        }

        if (wantsExit) {
            wantsExit = false;
            try {
                getReplayHandler().endReplay();
            } catch (Exception e) {
                LOGGER.error("Error saving replay: ", e);
                ReplayModReplay.instance.forcefullyStopReplay();
                MinecraftClient.getInstance().disconnect();
                MinecraftClient.getInstance().setScreen(null);
            }
        }
        if (editorState.wantsTimeJump()) {
            editorState.doTimeJump();
        } else if (editorState.wantsApplyToGame()) {
            editorState.applyToGame();
        }

        editorState.onPreRender();
    }

    @Override
    protected void render(MinecraftClient client) {
        var replayHandler = getReplayHandler();
        if (replayHandler == null) {
            close();
            return;
        }

        // Don't render the default ReplayMod UI
        replayHandler.getOverlay().setVisible(false);
        super.render(client);

        drawMenuBar();

        int bgColor = ImGui.getColorU32(ImGuiCol.WindowBg);

        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0, 0);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 0);
        var beginViewport = beginViewport("Viewport", 0);
        ImGui.popStyleVar(2);

        if (beginViewport) {
            ImGui.pushStyleColor(ImGuiCol.ChildBg, bgColor);
            drawPlaybackControls();
            ImGui.popStyleColor();
            ScenePropsPanel.processGlobalHotkeys(editorState);

            if (ImGui.isWindowHovered() && ImGui.isMouseClicked(0)) {
                raycastSelect();
            }

            // Gizmo hotkeys (industry standard)
            if (ImGui.shortcut(Keybinds.gizmoAll())) {
                editorState.toggleGizmos(true, true, true);
            }

            if (ImGui.shortcut(Keybinds.gizmoPos())) {
                editorState.toggleGizmos(true, false, false);
            }

            if (ImGui.shortcut(Keybinds.gizmoRot())) {
                editorState.toggleGizmos(false, true, false);
            }

            if (ImGui.shortcut(Keybinds.gizmoScale())) {
                editorState.toggleGizmos(false, false, true);
            }

            if (ImGui.shortcut(Keybinds.localTransforms())) {
                editorState.setLocalGizmos(!editorState.isLocalGizmos());
            }

            GizmoRenderer.drawGizmos(editorState, getViewportBounds());

            if (ImGui.shortcut(Keybinds.deleteSelected())) {
                editorState.applyOperator(new RemoveObjectsOperator(editorState.getSelectedObjects()));
            }

            // Zoom scrolling
            if (editorState.isPilotingCamera() && editorState.getScene().getSceneCameraObject() instanceof CameraObject cam) {
                cam.setFov(cam.getFov() + ImGui.getIO().getMouseWheel() * -2);
            }

            editorState.setRollingCamera(editorState.isPilotingCamera() && ShortcutUtils.isKeyChordDown(Keybinds.cameraRoll()));

        }
        ImGui.end();

        // Panels
        for (var panel : panels) {
            panel.draw(editorState, 0, null);
        }

        ExportWindow.drawExportWindow(editorState, editorState.getScene().getSceneProps().getRenderSettings());
        ExportProgressWindow.drawExportProgress(editorState);

        if (!firstFrame) {
            sceneBrowser.draw(editorState);
            settingsWindow.draw(editorState);
            exceptionPopup.render();
        }

        firstFrame = false;
    }

    @Override
    protected void onClose() {
        var rh = getReplayHandler();
        if (rh != null) {
            try {
                rh.endReplay();
            } catch (IOException e) {
                LOGGER.error("Error closing replay: ", e);
            }
        }
        super.onClose();
    }

    private void raycastSelect() {
        Mouse mouse = MinecraftClient.getInstance().mouse;

        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return;

        HitResult raycast = RaycastUtils.raycastViewport((float) mouse.getX(), (float) mouse.getY(), 1000, e -> true, false);
        if (raycast instanceof EntityHitResult hit) {
            Entity ent = hit.getEntity();
            if (ImGui.getIO().getKeyCtrl()) {
                /*
                    When the user ctrl-clicks an entity in the viewport:When the user ctrl-clicks an entity in the viewport:
                    If any scene object referencing that entity is currently the active object, the entire group is deselected and the active object is cleared.
                    Otherwise, all scene objects referencing that entity are selected, and the first one in the group becomes the active object.
                 */
                Iterable<ReplayObject> iter = () -> editorState.getScene().referencingObjects(ent).iterator();
                boolean anyIsActive = false;
                for (ReplayObject replayObject : iter) {
                    String id = replayObject.getId();
                    if (editorState.isObjectActive(id)) {
                        anyIsActive = true;
                        break;
                    }
                }
                String firstId = null;
                for (ReplayObject replayObject : iter) {
                    String id = replayObject.getId();
                    editorState.setObjectSelected(id, !anyIsActive);
                    if (firstId == null)
                        firstId = id;
                }
                editorState.setActiveObject(anyIsActive ? null : firstId);
            } else {
                ReplayObject replayObject = editorState.getScene().firstReferencingObject(ent);
                if (replayObject != null) {
                    String id = replayObject.getId();
                    editorState.getSelectedObjects().clear();
                    editorState.getSelectedObjects().add(id);
                    editorState.setActiveObject(id);
                }
            }
        }
    }

    // =========================================================================
    // Menu bar & hotkeys
    // =========================================================================

    private void drawMenuBar() {
        if (!ImGui.beginMainMenuBar()) return;

        if (ImGui.beginMenu("File")) {
            if (ImGui.menuItem("Open Scene")) sceneBrowser.openPopup();
            if (ImGui.menuItem("Export")) ExportWindow.open();
            ImGui.separator();
            if (ImGui.menuItem("Settings")) settingsWindow.openPopup();
            if (ImGui.menuItem("Exit")) wantsExit = true;
            ImGui.endMenu();
        }

        if (ImGui.beginMenu("Edit")) {
            if (ImGui.menuItem("Undo", "Ctrl+Z")) editorState.undo();
            if (ImGui.menuItem("Redo", "Ctrl+Shift+Z")) editorState.redo();

            ImGui.separator();

            ImGui.beginDisabled(editorState.getActiveObject() == null);
            if (ImGui.menuItem("Delete Selected", "Del")) deleteObject();
            ImGui.endDisabled();

            if (ImGui.menuItem("Insert Keyframe", "I")) insertKeyframe();

            ImGui.endMenu();
        }

        if (ImGui.beginMenu("Window")) {
            for (var panel : panels) {
                String label = (panel.isVisible() ? "* " : "  ") + panel.getTitle();
                if (ImGui.menuItem(label)) {
                    panel.requestFocus();
                    panel.setVisible(true);
                }
            }
            ImGui.endMenu();
        }

        ImGui.endMainMenuBar();
    }

    // =========================================================================
    // Playback controls
    // =========================================================================

    private final ImBoolean cameraViewInput = new ImBoolean();
    private final ImBoolean tmpBoolean = new ImBoolean();

    private void drawPlaybackControls() {
        ImGui.pushFont(ReplayLabIcons.getFont());
        float buttonSize = ImGui.getTextLineHeightWithSpacing() * 1.25f;
        viewportFooterHeight = buttonSize + ImGui.getStyle().getWindowPaddingY() * 2;
        ImGui.popFont();

        ImGui.setCursorPosY(ImGui.getContentRegionMaxY() - viewportFooterHeight);
        ImGui.setNextWindowBgAlpha(1);

        if (!ImGui.beginChild("Playback", ImGui.getContentRegionAvailX(), viewportFooterHeight, true)) {
            ImGui.endChild();
            return;
        }

        // Scene name (left-aligned)
        ImGui.alignTextToFramePadding();
        ImGui.text("Scene: " + editorState.getSceneName());
        ImGui.sameLine();

        // Transport buttons (center-aligned)
        float groupWidth = (buttonSize + ImGui.getStyle().getItemSpacingX()) * 5 - ImGui.getStyle().getItemSpacingX();
        ImGui.setCursorPosX(ImGui.getContentRegionMaxX() / 2 - groupWidth / 2);
        ImGui.alignTextToFramePadding();

        if (playbackIcon(ReplayLabIcons.ICON_TO_START_ALT, t("key.replaylab.scene_start"), buttonSize)) {
            editorState.jumpSceneStart();
        }
        playbackIcon(ReplayLabIcons.ICON_TO_START, t("key.replaylab.prev_key"), buttonSize);

        char playPauseIcon = editorState.isPlaying() ? ReplayLabIcons.ICON_PAUSE : ReplayLabIcons.ICON_PLAY;
        if (playbackIcon(playPauseIcon, t("key.replaylab.playpause"), buttonSize)) {
            editorState.togglePlayback();
        }

        playbackIcon(ReplayLabIcons.ICON_TO_END, t("key.replaylab.next_key"), buttonSize);
        if (playbackIcon(ReplayLabIcons.ICON_TO_END_ALT, t("key.replaylab.scene_end"), buttonSize)) {
            editorState.jumpSceneEnd();
        }

        // Camera controls & gizmos (right-aligned)
        ImGui.setCursorPosX(ImGui.getContentRegionMaxX() - prevCameraControlsGroupWidth);
        ImGui.beginGroup();

        tmpBoolean.set(editorState.showGizmoPos() && editorState.showGizmoRot() && editorState.showGizmoScale());
        if (ReplayLabControls.toggleButton(ReplayLabIcons.ICON_FREE_TRANSFORM, "gizmoAll", tmpBoolean, "key.replaylab.gizmo_all")) {
            editorState.showGizmoPos(tmpBoolean.get());
            editorState.showGizmoRot(tmpBoolean.get());
            editorState.showGizmoScale(tmpBoolean.get());
        }
        ImGui.sameLine();
        tmpBoolean.set(editorState.showGizmoPos());
        if (ReplayLabControls.toggleButton(ReplayLabIcons.ICON_MOVE, "gizmoPos", tmpBoolean, "key.replaylab.gizmo_pos")) {
            editorState.showGizmoPos(tmpBoolean.get());
            if (!ImGui.getIO().getKeyCtrl()) {
                editorState.showGizmoRot(false);
                editorState.showGizmoScale(false);
            }
        }
        ImGui.sameLine();
        tmpBoolean.set(editorState.showGizmoRot());
        if (ReplayLabControls.toggleButton(ReplayLabIcons.ICON_ROTATE, "gizmoRot", tmpBoolean, "key.replaylab.gizmo_rot")) {
            editorState.showGizmoRot(tmpBoolean.get());
            if (!ImGui.getIO().getKeyCtrl()) {
                editorState.showGizmoPos(false);
                editorState.showGizmoScale(false);
            }
        }
        ImGui.sameLine();
        tmpBoolean.set(editorState.showGizmoScale());
        if (ReplayLabControls.toggleButton(ReplayLabIcons.ICON_SCALE, "gizmoScale", tmpBoolean, "key.replaylab.gizmo_scale")) {
            editorState.showGizmoScale(tmpBoolean.get());
            if (!ImGui.getIO().getKeyCtrl()) {
                editorState.showGizmoPos(false);
                editorState.showGizmoRot(false);
            }
        }
        ImGui.sameLine();
        tmpBoolean.set(editorState.isLocalGizmos());
        char localIcon = tmpBoolean.get() ? ReplayLabIcons.ICON_CUBE : ReplayLabIcons.ICON_GLOBE;
        if (ReplayLabControls.toggleButton(localIcon, "freeTransform", tmpBoolean, "key.replaylab.local_transforms")) {
            editorState.setLocalGizmos(tmpBoolean.get());
        }
        ImGui.sameLine();
        cameraViewInput.set(editorState.isCameraView());
        if (ReplayLabControls.toggleButton(ReplayLabIcons.ICON_VIDEOCAM, "freeTransform", tmpBoolean, "key.replaylab.cameraview")) {
            editorState.setCameraView(cameraViewInput.get());
        }


        ImGui.endGroup();
        prevCameraControlsGroupWidth = ImGui.getItemRectSizeX();

        ImGui.endChild();
    }

    // =========================================================================
    // Object / operator helpers
    // =========================================================================


    private void insertKeyframe() {
        String selected = editorState.getActiveObject();
        if (selected != null) {
            editorState.applyOperator(new InsertKeyframeOperator(selected, editorState.getPlayhead()));
        }
    }

    private void deleteObject() {
        String selected = editorState.getActiveObject();
        if (selected != null) {
            editorState.applyOperator(new RemoveObjectOperator(selected));
        }
    }

    private void saveScene() {
        if (editorState.getSceneName() == null) {
            editorState.setSceneName("scene");
        }
        editorState.saveSceneAsync();
    }

    // =========================================================================
    // Viewport & layout overrides
    // =========================================================================

    @Override
    protected ViewportBounds getCustomViewportBounds() {
        var bounds = super.getCustomViewportBounds();
        if (bounds == null) return null;

        int width = bounds.width();
        int height = bounds.height() - (int) viewportFooterHeight;
        int offsetX = bounds.x();
        int offsetY = bounds.y() + (int) viewportFooterHeight;

        // Letterbox to the target aspect ratio when in camera view
        if (editorState.isCameraView()) {
            ScenePropsObject props = editorState.getScene().getSceneProps();
            float targetRatio = (float) props.getResolutionX() / props.getResolutionY();

            int bx, by;
            if (((float) width / height) > targetRatio) {
                bx = (int) (height * targetRatio);
                by = height;
            } else {
                bx = width;
                by = (int) (width / targetRatio);
            }

            offsetX += (width - bx) / 2;
            offsetY += (height - by) / 2;
            width = bx;
            height = by;
        }

        if (width < 2) width = 2;
        if (height < 2) height = 2;

        return new ViewportBounds(offsetX, offsetY, width, height);
    }

    /**
     * Not-null version of getCustomViewportBounds with fallback
     */
    private @NotNull ViewportBounds getViewportBounds() {
        var bounds = getCustomViewportBounds();
        Window window = MinecraftClient.getInstance().getWindow();
        return bounds != null ? bounds : new ViewportBounds(0, 0, window.getWidth(), window.getHeight());
    }

    @Override
    protected @Nullable Identifier getLayoutPreset() {
        return LAYOUT;
    }

    // =========================================================================
    // Widget helpers
    // =========================================================================

    private boolean playbackIcon(char icon, String tooltip, float buttonSize) {
        ImGui.pushFont(ReplayLabIcons.getFont());
        boolean res = ImGui.button(String.valueOf(icon), buttonSize, buttonSize);
        ImGui.popFont();

        if (ImGui.isItemHovered()) ImGui.setTooltip(tooltip);
        ImGui.sameLine();
        return res;
    }

    private boolean toggleButton(char icon, @Nullable String tooltip, ImBoolean value) {
        ImGui.pushFont(ReplayLabIcons.getFont());
        boolean result = ReplayLabControls.toggleButton(String.valueOf(icon), value);
        ImGui.popFont();

        if (ImGui.isItemHovered() && tooltip != null) ImGui.setTooltip(tooltip);
        return result;
    }

    // =========================================================================
    // Static helpers
    // =========================================================================

    private static ReplayHandler getReplayHandler() {
        return ReplayModReplay.instance.getReplayHandler();
    }

    private static String t(String key) {
        return Language.getInstance().get(key);
    }
}