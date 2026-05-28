package com.igrium.replaylab.ui.panels;

import com.igrium.replaylab.ReplayLab;
import com.igrium.replaylab.config.ReplayLabConfig;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.ui.settings.SettingsEditor;
import imgui.ImGui;
import lombok.Getter;
import net.minecraft.util.Identifier;

public class SettingsWindow extends UIModal {

    private final SettingsEditor settingsEditor = new SettingsEditor(ReplayLabConfig.getInstance());

    @Getter
    private boolean modified;

    public SettingsWindow(Identifier id) {
        super(id);

        setDefaultWidth(640);
        setDefaultHeight(480);
    }

    @Override
    protected void drawContents(EditorState editor) {
        modified |= settingsEditor.draw();
    }

    @Override
    protected void onClosed(EditorState editor) {
        super.onClosed(editor);
        ReplayLab.getInstance().saveConfig().exceptionally(e -> {
            editor.onException(e);
            return null;
        });
    }
}
