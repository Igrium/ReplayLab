package com.igrium.replaylab.ui.panels;

import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.operator.InsertKeyframeOperator;
import imgui.ImGui;
import imgui.flag.ImGuiKey;
import imgui.type.ImBoolean;
import lombok.Getter;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a single panel in the UI
 */
public abstract class UIPanel {
    @Getter
    private final Identifier id;
    private final ImBoolean visible = new ImBoolean(true);

    private boolean requestFocus;

    public UIPanel(Identifier id) {
        this.id = id;
    }

    public UIPanel(Identifier id, boolean visible) {
        this.id = id;
        this.visible.set(visible);
    }

    public boolean isVisible() {
        return visible.get();
    }

    public void setVisible(boolean visible) {
        this.visible.set(visible);
    }

    public String getTitle() {
        return Language.getInstance().get(id.toTranslationKey("panel"));
    }

    public final String getPanelName() {
        return getTitle() + "###" + id;
    }

    public void requestFocus() {
        requestFocus = true;
    }

    public final void draw(EditorState editorState, int imGuiWindowFlags, @Nullable Runnable callback) {
        if (visible.get()) {

            if (requestFocus) {
                ImGui.setNextWindowFocus();
                requestFocus = false;
            }

            if (ImGui.begin(getPanelName(), visible, imGuiWindowFlags)) {
                drawContents(editorState);
                if (callback != null) {
                    callback.run();
                }
                processGlobalHotkeys(editorState);
            }
            ImGui.end();
        }

    }

    protected abstract void drawContents(EditorState editorState);

    private static void processGlobalHotkeys(EditorState editorState) {
        var io = ImGui.getIO();
        if (io.getWantTextInput()) return;

        if (ImGui.shortcut(ImGuiKey.ImGuiMod_Ctrl | ImGuiKey.ImGuiMod_Shift | ImGuiKey.Z)) {
            editorState.redo();
        } else if (ImGui.shortcut(ImGuiKey.ImGuiMod_Ctrl | ImGuiKey.Z)) {
            editorState.undo();
        }

        if (ImGui.shortcut(ImGuiKey.I)) {
            String selected = editorState.getSelectedObject();
            if (selected != null) {
                editorState.applyOperator(new InsertKeyframeOperator(selected, editorState.getPlayhead()));
            }
        }
    }
}
