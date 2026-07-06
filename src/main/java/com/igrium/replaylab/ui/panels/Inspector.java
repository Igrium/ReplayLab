package com.igrium.replaylab.ui.panels;

import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.operator.object.CommitObjectUpdateOperator;
import com.igrium.replaylab.scene.obj.ObjectEditState;
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
        String selId = editorState.getActiveObject();
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
        int state = object.drawPropertiesPanel(editorState);

        if (hasFlag(state, ObjectEditState.UPDATE_SCENE)) {
            editorState.applyToGame(hasFlag(state, ObjectEditState.RESAMPLE) ? o -> true : o -> o != object);
        }
        if (hasFlag(state, ObjectEditState.CREATE_UNDO_STEP)) {
            editorState.applyOperator(new CommitObjectUpdateOperator(false, object.getId()), false);
        }
    }

    private static boolean hasFlag(int flags, int flag) {
        return (flags & flag) != 0;
    }
}
