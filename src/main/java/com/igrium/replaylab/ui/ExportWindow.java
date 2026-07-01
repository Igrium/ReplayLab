package com.igrium.replaylab.ui;

import com.igrium.craftui.app.AppManager;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.render.RenderSettingsObj;
import com.igrium.replaylab.scene.ReplayScene;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Language;

import java.nio.file.Files;

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

        boolean wantsClose = false;

        ImGui.setNextWindowSize(640, 0, ImGuiCond.Appearing);
        if (ImGui.beginPopupModal("Export Video", isOpen, ImGuiWindowFlags.NoSavedSettings)) {
            RenderSettingsObj renderSettings = editor.getScene().getRenderSettings();
            renderSettings.drawPropertiesPanel(editor);

            ImGui.separator();
            String confirmKey = t("gui.replaylab.fileExists.header");
            if (ImGui.button(t("gui.ok"))) {

                if (Files.isRegularFile(renderSettings.getOutPath())) {
                    ImGui.openPopup(confirmKey);
                } else {
                    wantsClose = true;
                    export(editor);
                }
            }

            if (ImGui.beginPopupModal(confirmKey, ImGuiWindowFlags.AlwaysAutoResize | ImGuiWindowFlags.NoSavedSettings)) {
                ImGui.text(tt("gui.replayLab.fileExists").formatted(renderSettings.getOutPath().getFileName().toString()));

                if (ImGui.button(t("gui.ok"))) {
                    ImGui.closeCurrentPopup();
                    wantsClose = true;
                    export(editor);
                }
                ImGui.setItemDefaultFocus();
                ImGui.sameLine();
                if (ImGui.button(t("gui.cancel"))) {
                    ImGui.closeCurrentPopup();
                }

                ImGui.endPopup();
            }

            ImGui.sameLine();
            if (ImGui.button(t("gui.cancel"))) {
                wantsClose = true;
            }

            if (wantsClose) {
                ImGui.closeCurrentPopup();
            }

            AppManager.drawGlobalPopup();
            ImGui.endPopup();
        }
    }


    private static void export(EditorState editor) {
        editor.getScene().saveObject(ReplayScene.RENDER_SETTINGS);
        editor.saveSceneAsync(); // Save our render settings
        MinecraftClient.getInstance().send(editor::render); // Render outside ImGui context
    }

    private static String t(String key) {
        return Language.getInstance().get(key) + "###" + key;
    }

    private static String tt(String key) {
        return Language.getInstance().get(key);
    }
}
