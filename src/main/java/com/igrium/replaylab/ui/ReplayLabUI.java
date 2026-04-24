package com.igrium.replaylab.ui;


import com.igrium.craftui.app.DockSpaceApp;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.operator.InsertKeyframeOperator;
import com.igrium.replaylab.operator.RemoveObjectOperator;
import com.igrium.replaylab.render.VideoRenderSettings;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.objs.ScenePropsObject;
import com.igrium.replaylab.ui.panels.*;
import com.igrium.replaylab.ui.util.ExceptionPopup;
import com.igrium.replaylab.ui.util.ReplayLabControls;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplayModReplay;
import imgui.ImGui;
import imgui.flag.*;
import imgui.type.ImBoolean;
import lombok.Getter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The main CraftApp for the replay lab editor.
 */
public class ReplayLabUI extends DockSpaceApp {

    // =========================================================================
    // Constants
    // =========================================================================

    private static final Logger LOGGER = LoggerFactory.getLogger(ReplayLabUI.class);
    private static final Identifier LAYOUT = Identifier.of("replaylab:replaylab");

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
            new DopeSheet(Identifier.of("replaylab:dopesheet_legacy")),
            new DopeSheetNew(Identifier.of("replaylab:dopesheet")),
            new CurveEditor(Identifier.of("replaylab:curveeditor")),
            new Outliner(Identifier.of("replaylab:outliner")),
            new Inspector(Identifier.of("replaylab:inspector")),
            new ScenePropsPanel(Identifier.of("replaylab:sceneprops"))
    );


    private final SceneBrowser sceneBrowser = new SceneBrowser();


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

    private final Set<ReplayScene.KeyReference> selectedKeys = new HashSet<>();
    private final ImBoolean cameraViewInput = new ImBoolean();
    private final ImBoolean snapKeysInput = new ImBoolean();
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
            processGlobalHotkeys();
        }
        ImGui.end();

        // Panels
        for (var panel : panels) {
            panel.draw(editorState, 0, null);
        }

        ExportWindow.drawExportWindow(editorState, editorState.getScene().getSceneProps().getRenderSettings());
        ExportProgressWindow.drawExportProgress(editorState);
//        TimelineDebugScreen.drawDebugScreen();

        if (!firstFrame) {
            exceptionPopup.render();
            sceneBrowser.render(editorState);
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

    // =========================================================================
    // Menu bar & hotkeys
    // =========================================================================

    private void drawMenuBar() {
        if (!ImGui.beginMainMenuBar()) return;

        if (ImGui.beginMenu("File")) {
            if (ImGui.menuItem("Open Scene")) sceneBrowser.open();
            if (ImGui.menuItem("Export")) ExportWindow.open();
            if (ImGui.menuItem("Exit")) wantsExit = true;
            ImGui.endMenu();
        }

        if (ImGui.beginMenu("Edit")) {
            if (ImGui.menuItem("Undo", "Ctrl+Z")) editorState.undo();
            if (ImGui.menuItem("Redo", "Ctrl+Shift+Z")) editorState.redo();

            ImGui.separator();

            ImGui.beginDisabled(editorState.getSelectedObject() == null);
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

    private void processGlobalHotkeys() {
        var io = ImGui.getIO();
        if (io.getWantTextInput()) return;

        if (ImGui.shortcut(ImGuiKey.ImGuiMod_Ctrl | ImGuiKey.ImGuiMod_Shift | ImGuiKey.Z)) {
            editorState.redo();
        } else if (ImGui.shortcut(ImGuiKey.ImGuiMod_Ctrl | ImGuiKey.Z)) {
            editorState.undo();
        }

        if (ImGui.shortcut(ImGuiKey.I)) {
            insertKeyframe();
        }
    }

    // =========================================================================
    // Playback controls
    // =========================================================================

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

        playbackIcon(ReplayLabIcons.ICON_TO_START_ALT, "Scene Start", buttonSize);
        playbackIcon(ReplayLabIcons.ICON_TO_START, "Previous Keyframe", buttonSize);

        char playPauseIcon = editorState.isPlaying() ? ReplayLabIcons.ICON_PAUSE : ReplayLabIcons.ICON_PLAY;
        if (playbackIcon(playPauseIcon, "Play/Pause", buttonSize)) {
            onPlayPauseClicked();
        }

        playbackIcon(ReplayLabIcons.ICON_TO_END, "Next Keyframe", buttonSize);
        playbackIcon(ReplayLabIcons.ICON_TO_END_ALT, "Scene End", buttonSize);

        // Camera controls (right-aligned)
        ImGui.setCursorPosX(ImGui.getContentRegionMaxX() - prevCameraControlsGroupWidth);
        ImGui.beginGroup();
        cameraViewInput.set(editorState.isCameraView());
        if (ReplayLabControls.toggleButton(ReplayLabIcons.ICON_VIDEOCAM, "Toggle Camera View", cameraViewInput)) {
            editorState.setCameraView(cameraViewInput.get());
        }
        ImGui.endGroup();
        prevCameraControlsGroupWidth = ImGui.getItemRectSizeX();

        ImGui.endChild();
    }

    private void onPlayPauseClicked() {
        if (editorState.isPlaying()) {
            editorState.stopPlaying();
        } else {
            editorState.startPlaying(editorState.getPlayhead());
        }
    }

    // =========================================================================
    // Object / operator helpers
    // =========================================================================


    private void insertKeyframe() {
        String selected = editorState.getSelectedObject();
        if (selected != null) {
            editorState.applyOperator(new InsertKeyframeOperator(selected, editorState.getPlayhead()));
        }
    }

    private void deleteObject() {
        String selected = editorState.getSelectedObject();
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

    /**
     * Delete the selected object if the delete button is pressed over this window.
     */
    @Deprecated
    private void testDeleteHotkey() {
        if (ImGui.isWindowFocused(ImGuiFocusedFlags.ChildWindows) && ImGui.shortcut(ImGuiKey.Delete)) {
            deleteObject();
        }
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

    private static boolean isCtrlPressed() {
        var io = ImGui.getIO();
        return MinecraftClient.IS_SYSTEM_MAC ? io.getKeySuper() : io.getKeyCtrl();
    }
}