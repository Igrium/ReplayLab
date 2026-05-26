package com.igrium.replaylab.ui.settings;

import com.igrium.craftui.app.CraftApp;
import com.igrium.craftui.screen.CraftAppScreen;
import com.igrium.replaylab.config.ReplayLabConfig;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

public class ReplayLabSettingsApp extends CraftApp {

    private final SettingsEditor settingsEditor = new SettingsEditor(ReplayLabConfig.getInstance());

    @Override
    protected void render(MinecraftClient client) {
        var viewport = ImGui.getMainViewport();
        ImGui.setNextWindowPos(viewport.getCenterX(), viewport.getCenterY(), ImGuiCond.Always, .5f, .5f);

        if (ImGui.begin("ReplayLab Settings", ImGuiWindowFlags.NoCollapse
                | ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoSavedSettings)) {
            if (ImGui.isKeyPressed(GLFW.GLFW_KEY_ESCAPE) && ImGui.isWindowFocused()) {
                close();
            }

            settingsEditor.draw();
        }
        ImGui.end();
    }

    public static CraftAppScreen<ReplayLabSettingsApp> createScreen() {
        return new CraftAppScreen<>(new ReplayLabSettingsApp());
    }
}
