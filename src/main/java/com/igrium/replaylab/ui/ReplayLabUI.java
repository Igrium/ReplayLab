package com.igrium.replaylab.ui;


import com.igrium.craftui.app.DockSpaceApp;
import com.igrium.craftui.file.FileDialogs;
import com.igrium.replaylab.scene.key.KeyframeManifest;
import com.igrium.replaylab.ui.util.ExceptionPopup;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplayModReplay;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import lombok.Getter;
import net.minecraft.client.MinecraftClient;
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

    private static ReplayHandler getReplayHandler() {
        return ReplayModReplay.instance.getReplayHandler();
    }

    private final ReplayLabEditorState state = new ReplayLabEditorState();

    //    private final DopeSheetOld dopeSheet = new DopeSheetOld();
    private final DopeSheet dopeSheet = new DopeSheet();
    private boolean wantsJumpTime;

    @Getter
    private final ExceptionPopup exceptionPopup = new ExceptionPopup();

    /**
     * The height of the viewport footer on the previous frame. Used when adjusting the viewport bounds.
     */
    private float viewportFooterHeight;


    public ReplayLabUI() {
        setViewportInputMode(ViewportInputMode.HOLD);
        setViewportInputButtons(1);
    }

    @Override
    protected void preRender(MinecraftClient client) {
        super.preRender(client);

        if (wantsJumpTime) {
            state.doTimeJump();
            wantsJumpTime = false;
        }

        state.onPreRender();
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
        exceptionPopup.render();

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

        var io = ImGui.getIO();
        if (io.getWantCaptureKeyboard() && !io.getWantTextInput()) {
            processHotkeys();
        }

    }

    private void drawMenuBar() {
        if (ImGui.beginMainMenuBar()) {
            if (ImGui.beginMenu("File")) {
                if (ImGui.menuItem("Open")) {
                    FileDialogs.showOpenDialog(null);
                }
                ImGui.endMenu();
            }
            if (ImGui.beginMenu("Edit")) {
                if (ImGui.menuItem("Undo", "Ctrl+Z")) {
                    state.getScene().undo();
                }
                if (ImGui.menuItem("Redo", "Ctrl+Shift+Z")) {
                    state.getScene().redo();
                }

                if (ImGui.menuItem("Throw test exceptions")) {
                    exceptionPopup.displayException(new IOException("This is an IO exception"));
                    try {
                        throw new RuntimeException("This is a RuntimeException");
                    } catch (Exception e) {
                        exceptionPopup.displayException(e);
                    }
                }
                ImGui.endMenu();
            }

            ImGui.endMainMenuBar();
        }

    }

    private void processHotkeys() {
        var io = ImGui.getIO();
        if (isCtrlPressed() && ImGui.isKeyPressed(GLFW.GLFW_KEY_Z)) {
            state.getScene().undo();
        }
        if (isCtrlPressed() && io.getKeyShift() && ImGui.isKeyPressed(GLFW.GLFW_KEY_Z)) {
            state.getScene().redo();
        }
    }

    // Testing variables
    private final Set<KeyframeManifest.KeyReference> selected = new HashSet<>();

    private void drawPlaybackControls() {
        ImGui.pushFont(PlaybackIcons.playbackIcons());
        float buttonSize = ImGui.getTextLineHeightWithSpacing() * 1.25f;
        viewportFooterHeight = buttonSize + ImGui.getStyle().getWindowPaddingY() * 2;
        ImGui.popFont();

        ImGui.setCursorPosY(ImGui.getContentRegionMaxY() - viewportFooterHeight);

        ImGui.setNextWindowBgAlpha(1);
        if (ImGui.beginChild("Playback", ImGui.getContentRegionAvailX(), viewportFooterHeight, true, ImGuiWindowFlags.AlwaysAutoResize)) {
            float groupWidth = (buttonSize + ImGui.getStyle().getItemSpacingX()) * 5 - ImGui.getStyle().getItemSpacingX();

            ImGui.setCursorPosX(ImGui.getContentRegionAvailX() / 2 - groupWidth / 2);
            ImGui.alignTextToFramePadding();

            playbackIcon(PlaybackIcons.JUMP_START, "Scene Start", buttonSize);
            playbackIcon(PlaybackIcons.PREV_KEYFRAME, "Previous Keyframe", buttonSize);
            if (playbackIcon(PlaybackIcons.PLAY, "Play/Pause", buttonSize)) {
                onPlayPauseClicked();
            }
            ;
            playbackIcon(PlaybackIcons.NEXT_KEYFRAME, "Next Keyframe", buttonSize);
            playbackIcon(PlaybackIcons.JUMP_END, "Scene End", buttonSize);
        }
        ImGui.endChild();
    }

    private void onPlayPauseClicked() {
        if (state.isPlaying()) {
            state.stopPlaying();
        } else {
            state.startPlaying(state.getPlayhead());
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
            dopeSheet.drawDopeSheet(state.getScene().getKeyManifest(), selected, 20 * 1000, state.getPlayheadRef(), 0);
            if (dopeSheet.isFinishedDraggingKeys()) {
                state.getScene().commitKeyframeUpdates();
            }
        }
        ImGui.end();

        long replayTime = state.getScene().sceneToReplayTime(state.getPlayhead());
        // If we dropped the playhead, always jump. Otherwise, only jump of we moved forward.
        if (dopeSheet.isFinishedDraggingPlayhead() ||
                (dopeSheet.isDraggingPlayhead() && replayTime >= getReplayHandler().getReplaySender().currentTimeStamp())) {
            wantsJumpTime = true;
        }

    }

    @Override
    protected ViewportBounds getCustomViewportBounds() {
        var bounds = super.getCustomViewportBounds();
        if (bounds == null) return null; // Shouldn't happen

        return new ViewportBounds(bounds.x(), bounds.y() + (int) viewportFooterHeight, bounds.width(), bounds.height() - (int) viewportFooterHeight);
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
