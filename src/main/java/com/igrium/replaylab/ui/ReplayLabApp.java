package com.igrium.replaylab.ui;


import com.igrium.craftui.app.DockSpaceApp;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplayModReplay;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.type.ImFloat;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The main CraftApp for the replay lab editor
 */
public class ReplayLabApp extends DockSpaceApp {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReplayLabApp.class);

    private static ReplayHandler getReplayHandler() {
        return ReplayModReplay.instance.getReplayHandler();
    }

//    private final DopeSheetOld dopeSheet = new DopeSheetOld();
    private final DopeSheet dopeSheet = new DopeSheet();

    public ReplayLabApp() {
        setViewportInputMode(ViewportInputMode.HOLD);
        setViewportInputButtons(1);
    }

    @Override
    protected void render(MinecraftClient client) {
        var replayHandler = getReplayHandler();
        if (replayHandler== null) {
            close(); // Close the app if we have no replay open.
            return;
        }

        // Don't render the default UI
        replayHandler.getOverlay().setVisible(false);

        super.render(client);

        ImGui.pushStyleColor(ImGuiCol.WindowBg, 0xFF222222);
        beginViewport("Viewport", 0);
        ImGui.end();

        drawDopeSheet();

        ImGui.popStyleColor();
    }


    private void drawDopeSheet() {
        if (ImGui.begin("Dope Sheet")) {
//            dopeSheet.drawDopeSheet(categories, testSelected, 200 * 20, playhead, 0);
        }
        ImGui.end();
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
}
