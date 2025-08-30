package com.igrium.replaylab.ui;


import com.igrium.craftui.app.DockSpaceApp;
import com.replaymod.replaystudio.replay.ReplayFile;
import imgui.ImGui;
import lombok.Getter;
import net.minecraft.client.MinecraftClient;

/**
 * The main CraftApp for the replay lab editor
 */
public class ReplayLabApp extends DockSpaceApp {
    @Getter
    private final ReplayFile replayFile;

    public ReplayLabApp(ReplayFile replayFile) {
        this.replayFile = replayFile;

        setViewportInputMode(ViewportInputMode.HOLD);
        setViewportInputButtons(1);
    }

    @Override
    protected void render(MinecraftClient client) {
        super.render(client);

        if (beginViewport("Viewport", 0)) {
            ImGui.text("This is the viewport");
        }
        ImGui.end();
    }
}
