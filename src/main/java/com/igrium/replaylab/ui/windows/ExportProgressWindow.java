package com.igrium.replaylab.ui.windows;

import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.render.VideoRenderer;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import com.igrium.replaylab.render.SimpleTexture;
import com.mojang.blaze3d.opengl.GlTexture;
import net.minecraft.locale.Language;

public class ExportProgressWindow {
    private static final String POPUP = "Exporting Video";

    public static void drawExportProgress(EditorState editorState) {
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

        String textKey = switch (r.getRenderState()) {
            case READY -> "gui.replaylab.export.ready";
            case STARTING -> "gui.replaylab.export.starting";
            case RENDERING -> "gui.replaylab.export.rendering";
            case FINISHING -> "gui.replaylab.export.finishing";
            case DONE -> "gui.replaylab.export.done";
        };

        ImGui.text(t(textKey).formatted(currentFrame, totalFrames));

        float progress = totalFrames > 0 ? (float) currentFrame / totalFrames : 0;
        ImGui.progressBar(progress);

        ImGui.beginDisabled(r.getRenderState() != VideoRenderer.RenderState.RENDERING);
        if (ImGui.button(t("gui.cancel"), ImGui.getContentRegionAvailX(), 0)) {
            r.abort();
        }
        ImGui.endDisabled();

        float ratio = (float) r.getRenderMetadata().width() / r.getRenderMetadata().height();

        float availWidth = ImGui.getContentRegionAvailX();
        float imageWidth = availWidth;
        float imageHeight = imageWidth / ratio;


        // Default max Y if first frame so window can auto-size correctly
        float maxClampY = ImGui.isWindowAppearing()
                ? ImGui.getTextLineHeightWithSpacing() * 32
                : ImGui.getContentRegionAvailY();

        if (imageHeight > maxClampY) {
            imageHeight = maxClampY;
            imageWidth = imageHeight * ratio;
        }

        float centerOffsetX = (availWidth - imageWidth) / 2.0f;
        ImGui.setCursorPosX(ImGui.getCursorPosX() + centerOffsetX);

        // ImGui only understands raw texture handles. The OpenGL backend gives us one; the Vulkan
        // backend (ImGuiImplBlaze3D) ignores per-command texture ids entirely, so there we fall
        // back to blank space and the progress bar above carries the feedback.
        SimpleTexture tex = r.getRenderTexture();
        if (tex != null && !tex.isClosed() && tex.getTexture() instanceof GlTexture glTexture) {
            ImGui.image(glTexture.glId(), imageWidth, imageHeight, 0, 1, 1, 0);
        } else {
            ImGui.dummy(imageWidth, imageHeight);
        }

    }

    private static String t(String key) {
        return Language.getInstance().getOrDefault(key);
    }
}
