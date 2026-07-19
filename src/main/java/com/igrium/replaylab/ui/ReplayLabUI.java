package com.igrium.replaylab.ui;


import com.igrium.craftui.app.DockSpaceApp;
import com.igrium.replaylab.ReplayLab;
import com.igrium.replaylab.config.Keybinds;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.operator.*;
import com.igrium.replaylab.operator.keyframe.InsertKeyframeOperator;
import com.igrium.replaylab.operator.object.RemoveObjectOperator;
import com.igrium.replaylab.operator.object.RemoveObjectsOperator;
import com.igrium.replaylab.operator.scene.SetSceneCameraOperator;
import com.igrium.replaylab.scene.obj.EntityProvider;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.scene.obj.ReplayObject3D;
import com.igrium.replaylab.scene.obj.ObjectSceneProps;
import com.igrium.replaylab.ui.gizmos.GizmoRenderer;
import com.igrium.replaylab.ui.panels.*;
import com.igrium.replaylab.ui.subpanels.ExceptionPopup;
import com.igrium.replaylab.ui.subpanels.ViewportControls;
import com.igrium.replaylab.ui.subpanels.ViewportFooter;
import com.igrium.replaylab.ui.util.ReplayLabControls;
import com.igrium.replaylab.ui.windows.*;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplayModReplay;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.type.ImBoolean;
import lombok.Getter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.List;

import static com.igrium.replaylab.config.ShortcutUtils.getChordLabel;

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

    // =========================================================================
    // UI panels
    // =========================================================================

    private static final Inspector inspector = new Inspector(Identifier.of("replaylab:inspector"));

    private final List<UIPanel> panels = List.of(
            new DopeSheet(Identifier.of("replaylab:dopesheet")),
            new CurveEditor(Identifier.of("replaylab:curveeditor")),
            new Outliner(Identifier.of("replaylab:outliner")),
            inspector,
            new ScenePropsPanel(Identifier.of("replaylab:sceneprops"))
    );

    @Getter
    private final SceneBrowser sceneBrowser = new SceneBrowser(Identifier.of("replaylab:scenebrowser"));

    @Getter
    private final SettingsWindow settingsWindow = new SettingsWindow(Identifier.of("replaylab:settings"));

    private final ViewportControls viewportControls = new ViewportControls();

    @Getter
    private final ExceptionPopup exceptionPopup = new ExceptionPopup();

    @Getter
    private final QuickModePopup quickModePopup = new QuickModePopup();

    private final ViewportFooter footer = new ViewportFooter();

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

    // =========================================================================
    // Constructor & lifecycle
    // =========================================================================

    public ReplayLabUI() {
        setViewportInputMode(ViewportInputMode.DRAG);
        setViewportInputButtons(1);
        editorState.setExceptionCallback(exceptionPopup::displayException);
        editorState.setOperatorCallback(this::onApplyOperator);
        editorState.setQuickModeInitCallback(quickModePopup);
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
            footer.drawPlaybackControls(editorState);
            ImGui.popStyleColor();

            GizmoRenderer.drawGizmos(editorState, getViewportBounds());

            viewportControls.drawViewport(editorState);

        }
        ImGui.end();

        // Panels
        if (editorState.isWantOpenInspector()) {
            inspector.setVisible(true);
            inspector.requestFocus();
            editorState.setWantOpenInspector(false);
        }

        for (var panel : panels) {
            panel.draw(editorState, 0, null);
        }

        ExportWindow.drawExportWindow(editorState);
        ExportProgressWindow.drawExportProgress(editorState);

        if (!firstFrame) {
            sceneBrowser.draw(editorState);
            settingsWindow.draw(editorState);
            exceptionPopup.render();
            quickModePopup.render();
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

    private void onApplyOperator(ReplayOperator op) {
        for (var panel : panels) {
            panel.onAppliedOperator(op, editorState);
        }
    }


    // =========================================================================
    // Menu bar & hotkeys
    // =========================================================================

    private void drawMenuBar() {
        if (!ImGui.beginMainMenuBar()) return;

        if (ImGui.beginMenu(t("gui.replaylab.file"))) {
            if (ImGui.menuItem(t("gui.replaylab.new_scene"))) {
                sceneBrowser.openPopup();
            }
            if (ImGui.menuItem(t("gui.replaylab.open_scene"))) {
                sceneBrowser.openPopup();
            }
            if (ImGui.menuItem(t("gui.replaylab.export"))) {
                ExportWindow.open();
            }

            ImGui.separator();
            if (ImGui.menuItem(t("gui.replaylab.settings"))) {
                settingsWindow.openPopup();
            }
            if (ImGui.menuItem(t("gui.replaylab.exit"))) {
                wantsExit = true;
            }
            ImGui.endMenu();
        }

        if (ImGui.beginMenu(t("gui.replaylab.edit"))) {
            ImGui.beginDisabled(editorState.getScene().getUndoStack().isEmpty());
            if (ImGui.menuItem(t("key.replaylab.undo"), getChordLabel(Keybinds.undo()))) {
                editorState.undo();
            }
            ImGui.endDisabled();
            ImGui.beginDisabled(editorState.getScene().getRedoStack().isEmpty());
            if (ImGui.menuItem(t("key.replaylab.redo"), getChordLabel(Keybinds.redo()))) {
                editorState.redo();
            }
            ImGui.endDisabled();

            ImGui.separator();
            ImGui.beginDisabled(editorState.getSelectedObjects().isEmpty());
            if (ImGui.menuItem(t("key.replaylab.copy"), getChordLabel(Keybinds.copy()))) {
                ImGui.setClipboardText(editorState.copyObjects());
            }
            ImGui.endDisabled();
            if (ImGui.menuItem(t("key.replaylab.paste"), getChordLabel(Keybinds.paste()))) {
                editorState.pasteObjects(ImGui.getClipboardText());
            }

            ImGui.beginDisabled(editorState.getSelectedObjects().isEmpty());
            if (ImGui.menuItem(t("key.replaylab.delete"), getChordLabel(Keybinds.deleteSelected()))) {
                editorState.applyOperator(new RemoveObjectsOperator(editorState.getSelectedObjects()));
            }
            ImGui.endDisabled();

            ImGui.separator();

            ReplayObject selected = editorState.getScene().getObject(editorState.getActiveObject());

            ImGui.beginDisabled(selected == null);
            boolean wantKeyPos = ImGui.shortcut(Keybinds.addKeyPos());
            boolean wantKeyRot = ImGui.shortcut(Keybinds.addKeyRot());
            boolean wantKeyScale = ImGui.shortcut(Keybinds.addKeyScale());
            if (ImGui.menuItem(t("key.replaylab.add_key"), getChordLabel(Keybinds.addKey()))) {
                wantKeyPos = true;
                wantKeyRot = true;
                wantKeyScale = true;
            }

            ImGui.beginDisabled(!(selected instanceof ReplayObject3D));
            if (ImGui.menuItem(t("key.replaylab.add_key_pos"), getChordLabel(Keybinds.addKeyPos()))) {
                wantKeyPos = true;
            }
            if (ImGui.menuItem(t("key.replaylab.add_key_rot"), getChordLabel(Keybinds.addKeyRot()))) {
                wantKeyRot = true;
            }
            if (ImGui.menuItem(t("key.replaylab.add_key_scale"), getChordLabel(Keybinds.addKeyScale()))) {
                wantKeyScale = true;
            }
            ImGui.endDisabled();

            if (selected != null && (wantKeyPos || wantKeyRot || wantKeyScale)) {
                editorState.applyOperator(new InsertKeyframeOperator(editorState.getPlayhead(),
                        wantKeyPos, wantKeyRot, wantKeyScale, selected.getId()));
            }

            ImGui.endDisabled();

            ImGui.endMenu();
        }
        if (ImGui.beginMenu(t("gui.replaylab.window"))) {
            ImBoolean visible = new ImBoolean();
            for (var panel : panels) {
                visible.set(panel.isVisible());
                if (ImGui.menuItem(panel.getTitle(), "", visible)) {
                    panel.requestFocus();
                    panel.setVisible(true);
                }
            }
            ImGui.endMenu();
        }

        if (ImGui.beginMenu(t("gui.replaylab.viewport"))) {

            ImBoolean camView = new ImBoolean(editorState.isCameraView());
            if (ImGui.menuItem(t("gui.replaylab.cameraview"), getChordLabel(Keybinds.cameraView()), camView)) {
                editorState.setCameraView(camView.get());
            }

//            if (ImGui.menuItem(t("key.replaylab.camera_to_view"), getChordLabel(Keybinds.cameraToView()))) {
//                // TODO: implement
//            }

            ReplayObject obj = editorState.getScene().getObject(editorState.getActiveObject());
            ImGui.beginDisabled(!(obj instanceof EntityProvider<?>));
            if (ImGui.menuItem(t("key.replaylab.active_to_cam"), getChordLabel(Keybinds.activeToCam()))) {
                editorState.applyOperator(new SetSceneCameraOperator(editorState.getActiveObject()));
            }
            ImGui.endDisabled();

            if (ImGui.menuItem(t("key.replaylab.frame"), getChordLabel(Keybinds.frameSelected()))) {
                editorState.snapViewportToSelected();
            }

            ImGui.endMenu();
        }

        if (ImGui.beginMenu(t("gui.replaylab.playback"))) {
            ImBoolean quickMode = new ImBoolean(editorState.isQuickMode());
            if (ImGui.menuItem(t("gui.replaylab.quick"), getChordLabel(Keybinds.quickMode()), quickMode)) {
                editorState.setQuickMode(quickMode.get());
            }
            ImGui.separator();
            if (ImGui.menuItem(t("key.replaylab.scene_start"), getChordLabel(Keybinds.sceneStart()))) {
                editorState.jumpSceneStart();
            }
            if (ImGui.menuItem(t("key.replaylab.scene_end"), getChordLabel(Keybinds.sceneEnd()))) {
                editorState.jumpSceneEnd();
            }
            if (ImGui.menuItem(t("key.replaylab.prev_key"), getChordLabel(Keybinds.prevKey()))) {
                editorState.jumpPrevKeyframe();
            }
            if (ImGui.menuItem(t("key.replaylab.next_key"), getChordLabel(Keybinds.nextKey()))) {
                editorState.jumpNextKeyframe();
            }

            ImGui.endMenu();
        }

        ImGui.endMainMenuBar();
    }

    // =========================================================================
    // Playback controls
    // =========================================================================





    // =========================================================================
    // Object / operator helpers
    // =========================================================================

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
            ObjectSceneProps props = editorState.getScene().getSceneProps();
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