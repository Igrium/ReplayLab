package com.igrium.replaylab.ui.panels;

import com.igrium.replaylab.editor.EditorState;
import net.minecraft.resources.ResourceLocation;

public class ScenePropsPanel extends Inspector {
    public ScenePropsPanel(ResourceLocation id) {
        super(id);
    }

    @Override
    protected void drawContents(EditorState editorState) {
        drawObjectProperties(editorState.getScene().getSceneProps(), editorState);
    }
}
