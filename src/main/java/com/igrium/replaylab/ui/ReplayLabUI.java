package com.igrium.replaylab.ui;


import com.igrium.craftui.app.DockSpaceApp;
import com.igrium.craftui.file.FileDialogs;
import com.igrium.replaylab.scene.EditorScene;
import com.igrium.replaylab.scene.KeyframeManifest;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplayModReplay;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.type.ImFloat;
import imgui.type.ImInt;
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

//    private final DopeSheetOld dopeSheet = new DopeSheetOld();
    private final DopeSheet dopeSheet = new DopeSheet();

    private final ImInt playhead = new ImInt(0);
    private boolean wantsJumpTime;


    public ReplayLabUI() {
        setViewportInputMode(ViewportInputMode.HOLD);
        setViewportInputButtons(1);
    }

    @Override
    protected void preRender(MinecraftClient client) {
        super.preRender(client);

        if (wantsJumpTime) {
            int replayTime = scene.sceneToReplayTime(playhead.get());
            replayTime = Math.min(replayTime, getReplayHandler().getReplayDuration());
            getReplayHandler().doJump(replayTime, true);
            wantsJumpTime = false;
        }
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

        ImGui.pushStyleColor(ImGuiCol.WindowBg, 0xFF222222);

        drawMenuBar();

        beginViewport("Viewport", 0);
        ImGui.end();

        drawDopeSheet();

        ImGui.popStyleColor();

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
                    scene.undo();
                }
                if (ImGui.menuItem("Redo", "Ctrl+Shift+Z")) {
                    scene.redo();
                }
                ImGui.endMenu();
            }
            ImGui.endMainMenuBar();
        }

    }

    private void processHotkeys() {
        var io = ImGui.getIO();
        if (io.getKeyCtrl() && ImGui.isKeyPressed(GLFW.GLFW_KEY_Z)) {
            scene.undo();
        }
        if (io.getKeyCtrl() && io.getKeyShift() && ImGui.isKeyPressed(GLFW.GLFW_KEY_Z)) {
            scene.redo();
        }
    }

    // Testing variables
    private final EditorScene scene = new EditorScene();
    private final Set<KeyframeManifest.KeyReference> selected = new HashSet<>();

    private void drawDopeSheet() {
        if (ImGui.begin("Dope Sheet")) {
            dopeSheet.drawDopeSheet(scene.getKeyManifest(), selected, 20 * 1000, playhead, 0);
            if (dopeSheet.isFinishedDraggingKeys()) {
                scene.commitKeyframeUpdates();
            }
        }
        ImGui.end();

        long replayTime = scene.sceneToReplayTime(playhead.get());
        // If we dropped the playhead, always jump. Otherwise, only jump of we moved forward.
        if (dopeSheet.isFinishedDraggingPlayhead() ||
                (dopeSheet.isDraggingPlayhead() && replayTime >= getReplayHandler().getReplaySender().currentTimeStamp())) {
            wantsJumpTime = true;
        }

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
