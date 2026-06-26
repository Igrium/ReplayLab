package com.igrium.replaylab.ui;

import com.igrium.craftui.app.AppManager;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.scene.ReplayScene;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Language;

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
            ImGui.separator();
            if (ImGui.button(t("gui.ok"))) {
                ImGui.closeCurrentPopup();
                export(editor);
            }

            ImGui.sameLine();
            if (ImGui.button(t("gui.cancel"))) {
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
        return Language.getInstance().get(key);
    }
}
