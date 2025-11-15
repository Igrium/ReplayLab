package com.igrium.replaylab.ui;

import com.igrium.craftui.file.FileDialogs;
import com.igrium.replaylab.editor.ReplayLabEditorState;
import com.igrium.replaylab.render.VideoRenderSettings;
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

    private static final ImString filePathInput = new ImString();

    public static void open() {
        wantsOpen = true;
    }

    public static void drawExportWindow(ReplayLabEditorState editor, VideoRenderSettings settings) {
        if (wantsOpen) {
            ImGui.openPopup("Export Video");
            isOpen.set(true);
            wantsOpen = false;

            filePathInput.set(settings.getOutPath());
        }

        ImGui.setNextWindowSize(640, 0, ImGuiCond.Appearing);
        if (ImGui.beginPopupModal("Export Video", isOpen, ImGuiWindowFlags.NoSavedSettings)) {
            ImGui.text("Output File");
            if (ImGui.button("Browse")) {
                FileDialogs.showOpenFolderDialog(settings.getOutPath().toString()).thenAcceptAsync(opt -> {
                    if (opt.isPresent()) {
                        String val = opt.get();
                        filePathInput.set(val);
                        settings.setOutPath(Paths.get(val));
                    }
                }, r -> RenderSystem.recordRenderCall(r::run));
            }
            ImGui.sameLine();
            ImGui.setNextItemWidth(-1);

            if (ImGui.inputText("##filepath", filePathInput)) {
                settings.setOutPath(Paths.get(filePathInput.get()));
            }

            if (ImGui.button("Export")) {
                ImGui.closeCurrentPopup();
                export(editor, settings);
            }

            ImGui.sameLine();
            if (ImGui.button("Cancel")) {
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
        }
    }

    private static void export(ReplayLabEditorState editor, VideoRenderSettings settings) {
        // Render outside of ImGui context
        MinecraftClient.getInstance().send(() -> {
            editor.render(settings);
        });
    }
}
