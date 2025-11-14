package com.igrium.replaylab.ui;

import com.igrium.craftui.file.FileDialogs;
import com.igrium.replaylab.ReplayLab;
import com.igrium.replaylab.render.VideoRenderSettings;
import com.igrium.replaylab.render.VideoRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.replaymod.replay.ReplayModReplay;
import imgui.ImGui;
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

    public static void drawExportWindow(ReplayLabUI ui, VideoRenderSettings settings) {
        if (wantsOpen) {
            ImGui.openPopup("Export Video");
            isOpen.set(true);
            wantsOpen = false;

            filePathInput.set(settings.getOutPath());
        }

        if (ImGui.beginPopupModal("Export Video", isOpen, ImGuiWindowFlags.AlwaysAutoResize)) {
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
            ImGui.setNextItemWidth(512);

            if (ImGui.inputText("##filepath", filePathInput)) {
                settings.setOutPath(Paths.get(filePathInput.get()));
            }

            if (ImGui.button("Export")) {
                ImGui.closeCurrentPopup();
                export(ui, settings);
            }

            ImGui.sameLine();
            if (ImGui.button("Cancel")) {
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
        }
    }

    private static void export(ReplayLabUI ui, VideoRenderSettings settings) {
        // Render outside of ImGui context
        MinecraftClient.getInstance().send(() -> {
            VideoRenderer renderer = new VideoRenderer(settings, ReplayModReplay.instance.getReplayHandler(), ui.getEditorState().getScene());
            try {
                renderer.render();
            } catch (Throwable e) {
                ReplayLab.getLogger("Exporter").error("Error exporting video", e);
                ui.getExceptionPopup().displayException(e);
            }
        });
    }
}
