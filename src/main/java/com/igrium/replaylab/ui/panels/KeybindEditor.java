package com.igrium.replaylab.ui.panels;

import com.igrium.replaylab.config.Keybinds;
import com.igrium.replaylab.config.ReplayLabConfig;
import com.igrium.replaylab.util.ShortcutUtils;
import imgui.ImGui;
import net.minecraft.util.Language;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

public class KeybindEditor {
    public static void drawKeybindEditor(@Nullable Consumer<? super Throwable> exceptionCallback) {
        Keybinds current = ReplayLabConfig.getInstance().getKeybinds();
        Keybinds def = new Keybinds();
        drawBinding("Undo", current.getUndo(), def.getUndo(), null);
        drawBinding("Redo", current.getRedo(), def.getRedo(), null);
    }

    private static void drawBinding(String label, int value, int defaultValue, @Nullable IntConsumer onChanged) {
        int key = ShortcutUtils.getChordKey(value);
        int[] mods = ShortcutUtils.getChordMods(value);

        ImGui.text(Language.getInstance().get(label));

        ImGui.tableNextColumn();
        for (int mod : mods) {
            ImGui.button(ShortcutUtils.getModName(mod));
            ImGui.sameLine();
            ImGui.text("+");
            ImGui.sameLine();
        }

        ImGui.button(ShortcutUtils.getKeyName(key));

        ImGui.tableNextRow();
    }
}
