package com.igrium.replaylab.ui.panels;

import com.igrium.replaylab.editor.EditorState;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import org.jetbrains.annotations.Nullable;

public abstract class UIModal {

    @Getter
    private final Identifier id;

    private final ImBoolean open = new ImBoolean(false);

    @Getter @Setter
    private float defaultWidth;

    @Getter @Setter
    private float defaultHeight;

    private boolean wantsOpenPopup;

    protected UIModal(Identifier id) {
        this.id = id;
    }

    public boolean isOpen() {
        return open.get();
    }

    public String getTitle() {
        return Language.getInstance().get(id.toTranslationKey("panel"));
    }

    public String getPopupName() {
        return getTitle() + "###" + id;
    }

    public void openPopup() {
        wantsOpenPopup = true;
    }

    public final boolean draw(EditorState editorState) {
        return draw(editorState, 0, null);
    }

    public final boolean draw(EditorState editorState, int imGuiWindowFlags, @Nullable Runnable callback) {
        if (wantsOpenPopup) {
            ImGui.openPopup(getPopupName());
            open.set(true);
            wantsOpenPopup = false;
        }

        float centerX = ImGui.getMainViewport().getCenterX();
        float centerY = ImGui.getMainViewport().getCenterY();

        ImGui.setNextWindowPos(centerX, centerY, ImGuiCond.Appearing, 0.5f, 0.5f);
        if (defaultWidth > 0 && defaultHeight > 0) {
            ImGui.setNextWindowSize(defaultWidth, defaultHeight, ImGuiCond.FirstUseEver);
        }

        if (ImGui.beginPopupModal(getPopupName(), open, imGuiWindowFlags | ImGuiWindowFlags.NoSavedSettings)) {
            drawContents(editorState);
            if (callback != null) {
                callback.run();
            }
            UIPanel.processGlobalHotkeys(editorState);
            ImGui.endPopup();
            return true;
        } else {
            open.set(false);
            return false;
        }
    }

    protected abstract void drawContents(EditorState editor);
}
