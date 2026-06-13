package com.igrium.replaylab.ui.panels;

import com.igrium.replaylab.config.Keybinds;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.operator.RemoveKeyframesOperator;
import imgui.ImGui;
import net.minecraft.util.Identifier;

/**
 * A panel designed to render keyframes (auto-listens to hotkeys, etc)
 */
public abstract class KeyframePanel extends UIPanel {
    public KeyframePanel(Identifier id) {
        super(id);
    }

    @Override
    protected void drawContents(EditorState editorState) {
        if (ImGui.shortcut(Keybinds.deleteSelected())) {
            // we need to clear selected keyframes
            var selected = editorState.getKeySelection().getSelectedKeyframes();
            editorState.getKeySelection().deselectAll();

            editorState.applyOperator(new RemoveKeyframesOperator(selected));
        }

        if (ImGui.shortcut(Keybinds.selectAll())) {
            editorState.getKeySelection().selectAll(editorState.getScene().getObjects());
        }

        if (ImGui.shortcut(Keybinds.selectNone())) {
            editorState.getKeySelection().deselectAll();
        }

        if (ImGui.shortcut(Keybinds.copy())) {
            ImGui.setClipboardText(editorState.copyKeyframes());
        }

        if (ImGui.shortcut(Keybinds.paste())) {
            editorState.pasteKeyframes(ImGui.getClipboardText());
        }

        testAddKeyShortcut(editorState);
    }
}
