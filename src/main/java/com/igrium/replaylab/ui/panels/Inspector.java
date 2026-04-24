package com.igrium.replaylab.ui.panels;

import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.operator.CommitObjectUpdateOperator;
import com.igrium.replaylab.scene.obj.ReplayObject;
import imgui.ImGui;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;

public class Inspector extends UIPanel {
    public Inspector(Identifier id) {
        super(id);
    }

    @Override
    protected void drawContents(EditorState editorState) {
        String selId = editorState.getSelectedObject();
        ReplayObject selected = selId != null ? editorState.getScene().getObject(selId) : null;

        if (selected == null) {
            ImGui.text("No selected object.");
        } else {
            String typeName = Language.getInstance().get(selected.getType().getTranslationKey());
            ImGui.text(selId + " (" + typeName + ")");
            ImGui.separator();
            drawObjectProperties(selected, editorState);
        }
    }

    protected void drawObjectProperties(ReplayObject object, EditorState editorState) {
        ReplayObject.PropertiesPanelState state = object.drawPropertiesPanel();
        if (state.wantsInsertKeyframe()) {
            object.insertKey(editorState.getPlayhead());
        }
        if (state.wantsUndoStep()) {
            editorState.applyOperator(new CommitObjectUpdateOperator(object.getId()), false);
        }
        if (state.wantsUpdateScene()) {
            editorState.applyToGame(o -> o != object);
        }
    }
}
