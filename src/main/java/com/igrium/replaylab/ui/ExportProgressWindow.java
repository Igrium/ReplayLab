package com.igrium.replaylab.ui;

import com.igrium.replaylab.editor.ReplayLabEditorState;
import com.igrium.replaylab.render.VideoRenderer;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;

public class ExportProgressWindow {
    private static final String POPUP = "Exporting Video";

    public static void drawExportProgress(ReplayLabEditorState editorState) {
        VideoRenderer r = editorState.getRenderer();
        if (r != null && !ImGui.isPopupOpen(POPUP)) {
            ImGui.openPopup(POPUP);
        }

        ImGui.setNextWindowSize(1024, 0, ImGuiCond.Appearing);
        if (ImGui.beginPopupModal(POPUP, ImGuiWindowFlags.NoSavedSettings)) {
            if (r != null) {
                drawPageContents(r);
            } else {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    private static void drawPageContents(VideoRenderer r) {
        int currentFrame = r.getFrameIdx();
        int totalFrames = r.getTotalFrames();

        String text = switch (r.getRenderState()) {
            case READY -> "Ready";
            case STARTING -> "Starting";
            case RENDERING -> "Rendering frame %d / %d".formatted(currentFrame, totalFrames);
            case FINISHING -> "Finalizing Export";
            case DONE -> "Finished";
        };

        ImGui.text(text);

        float progress = totalFrames > 0 ? (float) currentFrame / totalFrames : 0;
        ImGui.progressBar(progress);

        ImGui.beginDisabled(r.getRenderState() != VideoRenderer.RenderState.RENDERING);
        if (ImGui.button("Cancel", -1, 0)) {
            r.abort();
        }
        ImGui.endDisabled();
    }
}
