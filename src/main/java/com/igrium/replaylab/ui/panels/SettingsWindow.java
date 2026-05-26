package com.igrium.replaylab.ui.panels;

import com.igrium.replaylab.config.ReplayLabConfig;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.ui.settings.SettingsEditor;
import net.minecraft.util.Identifier;

public class SettingsWindow extends UIModal {

    private final SettingsEditor settingsEditor = new SettingsEditor(ReplayLabConfig.getInstance());

    public SettingsWindow(Identifier id) {
        super(id);

        setDefaultWidth(400);
        setDefaultHeight(300);
    }

    @Override
    protected void drawContents(EditorState editor) {
        settingsEditor.draw();
    }
}
