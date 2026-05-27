package com.igrium.replaylab.ui.settings;

import com.igrium.replaylab.config.ReplayLabConfig;
import imgui.ImGui;
import lombok.Getter;
import net.minecraft.util.Language;

public class SettingsEditor {

    @Getter
    private final ReplayLabConfig mutableConfig;
    
    private final KeybindEditor keybindEditor;

    public SettingsEditor(ReplayLabConfig mutableConfig) {
        this.mutableConfig = mutableConfig;
        keybindEditor = new KeybindEditor(mutableConfig);
    }

    public boolean draw() {
        boolean changed = false;

        if (ImGui.beginTabBar("settings")) {

            if (ImGui.beginTabItem(t("gui.replaylab.keybinds"))) {
                changed |= keybindEditor.draw();
                ImGui.endTabItem();
            }

            ImGui.endTabBar();
        }
        return changed;
    }

    private static String t(String key) {
        return Language.getInstance().get(key) + "###" + key;
    }
}
