package com.igrium.replaylab.ui;

import com.igrium.craftui.app.AppManager;
import com.igrium.craftui.file.FileDialogs;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.render.VideoRenderSettings;
import com.igrium.replaylab.render2.RenderSettingsObj;
import com.mojang.blaze3d.systems.RenderSystem;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import net.minecraft.client.MinecraftClient;

import java.nio.file.Paths;

public class ExportWindow {

    private static final ImBoolean isOpen = new ImBoolean();
    private static boolean wantsOpen = false;

    public static void open() {
        wantsOpen = true;
    }

    public static void drawExportWindow(EditorState editor) {
        if (wantsOpen) {
            ImGui.openPopup("Export Video");
            isOpen.set(true);
            wantsOpen = false;
        }

        ImGui.setNextWindowSize(640, 0, ImGuiCond.Appearing);
        if (ImGui.beginPopupModal("Export Video", isOpen, ImGuiWindowFlags.NoSavedSettings)) {
            editor.getScene().getRenderSettings().drawPropertiesPanel(editor);

            if (ImGui.button("Export")) {
                ImGui.closeCurrentPopup();
                export(editor);
            }

            ImGui.sameLine();
            if (ImGui.button("Cancel")) {
                ImGui.closeCurrentPopup();
            }

            AppManager.drawGlobalPopup();
            ImGui.endPopup();
        }
    }

    private static void export(EditorState editor) {
        editor.saveSceneAsync(); // Save our render settings
        MinecraftClient.getInstance().send(editor::render); // Render outside ImGui context
    }
}
