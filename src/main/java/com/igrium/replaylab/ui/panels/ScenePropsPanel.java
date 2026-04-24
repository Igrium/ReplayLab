package com.igrium.replaylab.ui.panels;

import com.igrium.replaylab.editor.EditorState;
import net.minecraft.util.Identifier;

public class ScenePropsPanel extends Inspector {
    public ScenePropsPanel(Identifier id) {
        super(id);
    }

    @Override
    protected void drawContents(EditorState editorState) {
        drawObjectProperties(editorState.getScene().getSceneProps(), editorState);
    }
}
