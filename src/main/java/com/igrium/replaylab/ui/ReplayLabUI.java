package com.igrium.replaylab.ui;


import com.igrium.craftui.app.DockSpaceApp;
import com.igrium.replaylab.operator.InsertKeyframeOperator;
import com.igrium.replaylab.operator.CommitObjectUpdateOperator;
import com.igrium.replaylab.operator.RemoveKeyframesOperator;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.ui.util.ExceptionPopup;
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

    private final ReplayLabEditorState editorState = new ReplayLabEditorState();

    //    private final DopeSheetOld dopeSheet = new DopeSheetOld();
    private final DopeSheet dopeSheet = new DopeSheet();
    private final SceneBrowser sceneBrowser = new SceneBrowser();
    private boolean wantsJumpTime;
    private boolean wantsApplyToGame;

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

        if (wantsJumpTime) {
            editorState.doTimeJump();
            wantsJumpTime = false;
        } else if (wantsApplyToGame) {
            editorState.applyToGame();
            wantsApplyToGame = false;
        }

        editorState.onPreRender();
    }

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
        }

        ImGui.end();

        drawDopeSheet();
        drawOutliner();
        drawInspector();
        drawSceneProperties();

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
                if (ImGui.menuItem("Open")) {
                    sceneBrowser.open();
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

    // Testing variables
    private final Set<ReplayScene.KeyReference> selectedKeys = new HashSet<>();

    private final ImBoolean cameraViewInput = new ImBoolean();

    private float prevCameraControlsGroupWidth = 0;

    private void drawPlaybackControls() {
        ImGui.pushFont(PlaybackIcons.playbackIcons());
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

            playbackIcon(PlaybackIcons.JUMP_START, "Scene Start", buttonSize);
            playbackIcon(PlaybackIcons.PREV_KEYFRAME, "Previous Keyframe", buttonSize);
            String playPauseIcon = editorState.isPlaying() ? PlaybackIcons.PAUSE : PlaybackIcons.PLAY;
            if (playbackIcon(playPauseIcon, "Play/Pause", buttonSize)) {
                onPlayPauseClicked();
            }

            playbackIcon(PlaybackIcons.NEXT_KEYFRAME, "Next Keyframe", buttonSize);
            playbackIcon(PlaybackIcons.JUMP_END, "Scene End", buttonSize);

            ImGui.setCursorPosX(ImGui.getContentRegionMaxX() - prevCameraControlsGroupWidth);

            ImGui.beginGroup();
            cameraViewInput.set(editorState.isCameraView());
            ImGui.checkbox("Camera View", cameraViewInput);
            editorState.setCameraView(cameraViewInput.get());
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

    private boolean playbackIcon(String icon, String tooltip, float buttonSize) {
        ImGui.pushFont(PlaybackIcons.playbackIcons());
        boolean res = ImGui.button(icon, buttonSize, buttonSize);
        ImGui.popFont();

        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(tooltip);
        }
        ImGui.sameLine();
        return res;
    }


    private void drawDopeSheet() {
        if (ImGui.begin("Dope Sheet")) {
            dopeSheet.drawDopeSheet(editorState.getScene(), selectedKeys, 20 * 1000, editorState.getPlayheadRef(), DopeSheet.SNAP_KEYS);
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

    private void drawOutliner() {
        if (ImGui.begin("Outliner")) {
            Outliner.drawOutliner(editorState);
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
        String objId;
        if (state == ReplayObject.PropertiesPanelState.COMMIT && (objId = object.getId()) != null) {
            editorState.applyOperator(new CommitObjectUpdateOperator(objId));
        }
        if (state == ReplayObject.PropertiesPanelState.COMMIT || state == ReplayObject.PropertiesPanelState.DRAGGING) {
            editorState.applyToGame();
        }
    }

    @Override
    protected ViewportBounds getCustomViewportBounds() {
        var bounds = super.getCustomViewportBounds();
        if (bounds == null) return null; // Shouldn't happen

        return new ViewportBounds(bounds.x(), bounds.y() + (int) viewportFooterHeight, bounds.width(), bounds.height() - (int) viewportFooterHeight);
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
