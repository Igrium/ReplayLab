package com.igrium.replaylab.ui;


import com.igrium.craftui.app.DockSpaceApp;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.operator.InsertKeyframeOperator;
import com.igrium.replaylab.operator.CommitObjectUpdateOperator;
import com.igrium.replaylab.operator.RemoveKeyframesOperator;
import com.igrium.replaylab.operator.RemoveObjectOperator;
import com.igrium.replaylab.render.VideoRenderSettings;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.scene.objs.ScenePropsObject;
import com.igrium.replaylab.ui.util.ExceptionPopup;
import com.igrium.replaylab.ui.util.ReplayLabControls;
import com.igrium.replaylab.ui.util.TimelineFlags;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplayModReplay;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiFocusedFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import lombok.Getter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * The main CraftApp for the replay lab editor
 */
public class ReplayLabUI extends DockSpaceApp {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReplayLabUI.class);

    private static final Identifier LAYOUT = Identifier.of("replaylab:replaylab");

    private static ReplayHandler getReplayHandler() {
        return ReplayModReplay.instance.getReplayHandler();
    }

    @Getter
    private final EditorState editorState = new EditorState();

    //    private final DopeSheetOld dopeSheet = new DopeSheetOld();
    private final DopeSheet dopeSheet = new DopeSheet();
    private final DopeSheetNew dopeSheetNew = new DopeSheetNew();
    private final CurveEditor curveEditor = new CurveEditor();
    private final SceneBrowser sceneBrowser = new SceneBrowser();
    private final Outliner outliner = new Outliner();
    private boolean wantsJumpTime;
    private boolean wantsApplyToGame;
    private boolean wantsExit;

    @Getter
    private final ExceptionPopup exceptionPopup = new ExceptionPopup();

    /**
     * Hacky fix for the fact that exceptionPopup seems to crash the game if shown on the first frame.
     */
    private boolean firstFrame;

    /**
     * The height of the viewport footer on the previous frame. Used when adjusting the viewport bounds.
     */
    private float viewportFooterHeight;


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
            close(); // Close the app if we have no replay open.
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

        } if (wantsJumpTime) {
            editorState.doTimeJump();
            wantsJumpTime = false;
        } else if (wantsApplyToGame) {
            editorState.applyToGame();
            wantsApplyToGame = false;
        }

        editorState.onPreRender();
    }

    private final VideoRenderSettings tmpExportSettings = new VideoRenderSettings();

    @Override
    protected void render(MinecraftClient client) {
        var replayHandler = getReplayHandler();
        if (replayHandler == null) {
            close(); // Close the app if we have no replay open.
            return;
        }

        // Don't render the default UI
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
            testDeleteHotkey();
        }

        ImGui.end();

        drawDopeSheet();
        drawDopeSheetNew();
        drawCurveEditor();
        drawOutliner();
        drawInspector();
        drawSceneProperties();
        ExportWindow.drawExportWindow(editorState, editorState.getScene().getSceneProps().getRenderSettings());
        ExportProgressWindow.drawExportProgress(editorState);

        if (!firstFrame) {
            exceptionPopup.render();
            sceneBrowser.render(editorState);
        }

        var io = ImGui.getIO();
        if (io.getWantCaptureKeyboard() && !io.getWantTextInput()) {
            processGlobalHotkeys();
        }
        firstFrame = false;
    }

    private void drawMenuBar() {
        if (ImGui.beginMainMenuBar()) {
            if (ImGui.beginMenu("File")) {
                if (ImGui.menuItem("Open Scene")) {
                    sceneBrowser.open();
                }

                if (ImGui.menuItem("Export")) {
                    ExportWindow.open();
                }

                if (ImGui.menuItem("Exit")) {
                    wantsExit = true;
                }
                ImGui.endMenu();
            }
            if (ImGui.beginMenu("Edit")) {
                if (ImGui.menuItem("Undo", "Ctrl+Z")) {
                    editorState.undo();
                }
                if (ImGui.menuItem("Redo", "Ctrl+Shift+Z")) {
                    editorState.redo();
                }

                ImGui.separator();

                ImGui.beginDisabled(editorState.getSelectedObject() == null);
                if (ImGui.menuItem("Delete Selected", "Del")) {
                    deleteObject();
                }
                ImGui.endDisabled();

                if (ImGui.menuItem("Insert Keyframe", "I")) {
                    insertKeyframe();
                }

                ImGui.endMenu();
            }

            ImGui.endMainMenuBar();
        }

    }

    private void processGlobalHotkeys() {
        var io = ImGui.getIO();
        if (io.getWantTextInput()) {
            return;
        }

        if (isCtrlPressed() && ImGui.isKeyPressed(GLFW.GLFW_KEY_Z)) {
            editorState.undo();
        }

        if (isCtrlPressed() && io.getKeyShift() && ImGui.isKeyPressed(GLFW.GLFW_KEY_Z)) {
            editorState.redo();
        }

        if (ImGui.isKeyPressed(GLFW.GLFW_KEY_I)) {
            insertKeyframe();
        }
    }

    /**
     * Delete the selected object if the delete button is pressed over this window
     */
    private void testDeleteHotkey() {
        var io = ImGui.getIO();
        if (ImGui.isWindowFocused(ImGuiFocusedFlags.ChildWindows) && !io.getWantTextInput() && ImGui.isKeyPressed(GLFW.GLFW_KEY_DELETE)) {
            deleteObject();
        }
    }

    private void saveScene() {
        if (editorState.getSceneName() == null) {
            editorState.setSceneName("scene");
        }
        editorState.saveSceneAsync();
    }

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

    private final Set<ReplayScene.KeyReference> selectedKeys = new HashSet<>();

    private final ImBoolean cameraViewInput = new ImBoolean();
    private final ImBoolean snapKeysInput = new ImBoolean();

    private float prevCameraControlsGroupWidth = 0;

    private void drawPlaybackControls() {
        ImGui.pushFont(ReplayLabIcons.getFont());
        float buttonSize = ImGui.getTextLineHeightWithSpacing() * 1.25f;
        viewportFooterHeight = buttonSize + ImGui.getStyle().getWindowPaddingY() * 2;
        ImGui.popFont();

        ImGui.setCursorPosY(ImGui.getContentRegionMaxY() - viewportFooterHeight);

        ImGui.setNextWindowBgAlpha(1);
        if (ImGui.beginChild("Playback", ImGui.getContentRegionAvailX(), viewportFooterHeight, true, ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.alignTextToFramePadding();
            ImGui.text("Scene: " + editorState.getSceneName());
            ImGui.sameLine();

            float groupWidth = (buttonSize + ImGui.getStyle().getItemSpacingX()) * 5 - ImGui.getStyle().getItemSpacingX();

            ImGui.setCursorPosX(ImGui.getContentRegionMaxX() / 2 - groupWidth / 2);
            ImGui.alignTextToFramePadding();

            playbackIcon(ReplayLabIcons.ICON_TO_START_ALT, "Scene Start", buttonSize);
            playbackIcon(ReplayLabIcons.ICON_TO_START, "Previous Keyframe", buttonSize);
            char playPauseIcon = editorState.isPlaying() ? ReplayLabIcons.ICON_PAUSE: ReplayLabIcons.ICON_PLAY;
            if (playbackIcon(playPauseIcon, "Play/Pause", buttonSize)) {
                onPlayPauseClicked();
            }

            playbackIcon(ReplayLabIcons.ICON_TO_END, "Next Keyframe", buttonSize);
            playbackIcon(ReplayLabIcons.ICON_TO_END_ALT, "Scene End", buttonSize);

            ImGui.setCursorPosX(ImGui.getContentRegionMaxX() - prevCameraControlsGroupWidth);

            /// Editor buttons
            ImGui.beginGroup();
            cameraViewInput.set(editorState.isCameraView());
            if (ReplayLabControls.toggleButton(ReplayLabIcons.ICON_VIDEOCAM, "Toggle Camera View", cameraViewInput)) {
                editorState.setCameraView(cameraViewInput.get());
            }
            ImGui.endGroup();
            prevCameraControlsGroupWidth = ImGui.getItemRectSizeX();
        }
        ImGui.endChild();
    }

    private void onPlayPauseClicked() {
        if (editorState.isPlaying()) {
            editorState.stopPlaying();
        } else {
            editorState.startPlaying(editorState.getPlayhead());
        }
    }

    private boolean playbackIcon(char icon, String tooltip, float buttonSize) {
        ImGui.pushFont(ReplayLabIcons.getFont());
        boolean res = ImGui.button(String.valueOf(icon), buttonSize, buttonSize);
        ImGui.popFont();

        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(tooltip);
        }
        ImGui.sameLine();
        return res;
    }

    private boolean toggleButton(char icon, @Nullable String tooltip, ImBoolean value) {
        ImGui.pushFont(ReplayLabIcons.getFont());
        boolean result = ReplayLabControls.toggleButton(String.valueOf(icon), value);
        ImGui.popFont();

        if (ImGui.isItemHovered() && tooltip != null) {
            ImGui.setTooltip(tooltip);
        }
        return result;
    }

    private void drawDopeSheet() {
        if (ImGui.begin("Dope Sheet")) {
            dopeSheet.drawDopeSheet(editorState.getScene(), editorState.getKeySelection(), 20 * 1000, editorState.getPlayheadRef(), TimelineFlags.SNAP_KEYS);
            if (!dopeSheet.getUpdatedObjects().isEmpty()) {
                editorState.applyOperator(new CommitObjectUpdateOperator(dopeSheet.getUpdatedObjects()));
                editorState.saveSceneAsync();
            }

            // Always apply if we're dragging
            if (!dopeSheet.getKeyDragOffsets().isEmpty()) {
                wantsApplyToGame = true;
            }

            if (ImGui.isWindowFocused(ImGuiFocusedFlags.ChildWindows)
                    && !ImGui.getIO().getWantTextInput()
                    && ImGui.isKeyPressed(GLFW.GLFW_KEY_DELETE)) {
                editorState.applyOperator(new RemoveKeyframesOperator(selectedKeys));
            }
        }
        ImGui.end();

        long replayTime = editorState.getScene().sceneToReplayTime(editorState.getPlayhead());
        // If we dropped the playhead, always jump. Otherwise, only jump of we moved forward.
        if (dopeSheet.isFinishedDraggingPlayhead() ||
                (dopeSheet.isDraggingPlayhead() && replayTime >= getReplayHandler().getReplaySender().currentTimeStamp())) {
            wantsJumpTime = true;
        } else if (dopeSheet.isDraggingPlayhead()) {
            wantsApplyToGame = true;
        }

    }

    private void drawDopeSheetNew() {
        if (ImGui.begin("Dope Sheet (new)")) {
            dopeSheetNew.drawDopeSheet(editorState.getScene(), null, editorState.getKeySelection(),
                    editorState.getPlayheadRef(), 0);
        }
        ImGui.end();
    }

    private void drawCurveEditor() {
        if (ImGui.begin("Curve Editor")) {

            curveEditor.drawCurveEditor(editorState.getScene(), null, editorState.getKeySelection(),
                    editorState.getPlayheadRef(), 0);

            long replayTime = editorState.getScene().sceneToReplayTime(editorState.getPlayhead());

            // If we dropped the playhead, always jump. Otherwise, only jump of we moved forward.
            if (curveEditor.stoppedScrubbing() ||
                    (curveEditor.isScrubbing() && replayTime > getReplayHandler().getReplaySender().currentTimeStamp())) {
                wantsJumpTime = true;
            } else if (curveEditor.isScrubbing()) {
                wantsApplyToGame = true;
            }

            if (!curveEditor.getUpdatedObjects().isEmpty()) {
                editorState.applyOperator(new CommitObjectUpdateOperator(curveEditor.getUpdatedObjects()));
            } else if (curveEditor.isDragging()) {
                // Always apply to game if we're dragging
                editorState.applyToGame();
            }
        }
        ImGui.end();
    }

    private void drawOutliner() {
        if (ImGui.begin("Outliner")) {
            outliner.drawOutliner(editorState);
            testDeleteHotkey();
        }
        ImGui.end();
    }

    private void drawInspector() {
        if (ImGui.begin("Inspector")) {
            String selId = editorState.getSelectedObject();
            ReplayObject selected = selId != null ? editorState.getScene().getObject(selId) : null;

            if (selected == null) {
                ImGui.text("No selected object.");
            } else {
                String typeName = Language.getInstance().get(selected.getType().getTranslationKey());
                ImGui.text(selId + " (" + typeName + ")");
                ImGui.separator();
                drawObjectProperties(selected);
            }
        }
        ImGui.end();
    }

    private void drawSceneProperties() {
        if (ImGui.begin("Scene Properties")) {
            drawObjectProperties(editorState.getScene().getSceneProps());

        }
        ImGui.end();
    }

    private void drawObjectProperties(ReplayObject object) {
        ReplayObject.PropertiesPanelState state = object.drawPropertiesPanel();
        if (state.wantsInsertKeyframe()) {
            object.insertKey(editorState.getPlayhead());
        }
        if (state.wantsUndoStep()) {
            editorState.applyOperator(new CommitObjectUpdateOperator(object.getId()), false);
        }
        if (state.wantsUpdateScene()) {
            editorState.applyToGame(o -> o != object);
        }
    }

    @Override
    protected ViewportBounds getCustomViewportBounds() {
        var bounds = super.getCustomViewportBounds();
        if (bounds == null) return null; // Shouldn't happen

        int width = bounds.width();
        int height = bounds.height() - (int) viewportFooterHeight;

        int offsetX = bounds.x();
        int offsetY = bounds.y() + (int) viewportFooterHeight;

        // Cast to aspect ratio if we're in camera view
        if (editorState.isCameraView()) {
            ScenePropsObject props = editorState.getScene().getSceneProps();

            int bx;
            int by;
            float targetRatio = (float) props.getResolutionX() / props.getResolutionY();
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

    private static boolean isCtrlPressed() {
        var io = ImGui.getIO();
        return MinecraftClient.IS_SYSTEM_MAC ? io.getKeySuper() : io.getKeyCtrl();
    }
}
