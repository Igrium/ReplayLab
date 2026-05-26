package com.igrium.replaylab.ui.settings;

import com.igrium.replaylab.config.Keybinds;
import com.igrium.replaylab.config.ReplayLabConfig;
import com.igrium.replaylab.util.ShortcutUtils;
import imgui.ImGui;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import lombok.Getter;
import net.minecraft.util.Language;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntConsumer;

public class KeybindEditor {
    // Tracks which binding is actively being remapped
    private String currentlyEditing = null;

    private final ReplayLabConfig mutableConfig;

    public KeybindEditor(ReplayLabConfig mutableConfig) {
        this.mutableConfig = mutableConfig;
    }


    protected boolean draw() {
        Keybinds current = mutableConfig.getKeybinds();
        Keybinds defBinds = new Keybinds();
        boolean changed = false;

        if (ImGui.beginTable("Keybinds", 2, ImGuiTableFlags.Borders)) {
            ImGui.tableSetupColumn("Action", ImGuiTableColumnFlags.WidthFixed);
            ImGui.tableSetupColumn("Shortcut", ImGuiTableColumnFlags.WidthStretch);
            changed |= drawBinding(t("key.replaylab.undo"), current.getUndo(), defBinds.getUndo(), current::setUndo);
            changed |= drawBinding(t("key.replaylab.redo"), current.getRedo(), defBinds.getRedo(), current::setRedo);
            ImGui.endTable();
        }
        return changed;
    }

    private boolean drawBinding(String label, int value, int defaultValue, @Nullable IntConsumer onChanged) {
        boolean changed = false;
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text(Language.getInstance().get(label));

        ImGui.tableNextColumn();
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (label.equals(currentlyEditing)) {
            // If we're currently editing this binding
            ImGui.button(Language.getInstance().get("gui.replaylab.new_keybind") + "###" + label, -1, 0);

            int newKey = -1;
            // Iterate through valid ImGui keys to see what was pressed
            for (int key : ShortcutUtils.KEY_NAMES.keySet()) {
                if (key != ImGuiKey.None && !isModifierKey(key) && ImGui.isKeyPressed(key, false)) {
                    newKey = key;
                    break;
                }
            }

            if (newKey != -1) {
                if (newKey == ImGuiKey.Escape) {
                    currentlyEditing = null; // Cancel editing
                } else {
                    // Save edit
                    int mods = ImGui.getIO().getKeyMods();
                    if (onChanged != null) {
                        onChanged.accept(newKey | mods);
                        changed = true;
                    }
                    currentlyEditing = null;
                }
            }
        } else {
            // Render the current binding as a single interactive button
            StringBuilder btnText = new StringBuilder();
            int key = ShortcutUtils.getChordKey(value);
            int[] mods = ShortcutUtils.getChordMods(value);

            for (int mod : mods) {
                btnText.append(ShortcutUtils.getModName(mod)).append(" + ");
            }
            btnText.append(ShortcutUtils.getKeyName(key));

            if (ImGui.button(btnText + "###" + label, -1, 0)) {
                currentlyEditing = label; // Switch into edit mode
            }


        }
        return changed;
    }

    /**
     * Prevents the editor from saving a lone modifier (like Shift) as the primary key.
     */
    private static boolean isModifierKey(int key) {
        return key == ImGuiKey.LeftCtrl || key == ImGuiKey.RightCtrl ||
                key == ImGuiKey.LeftShift || key == ImGuiKey.RightShift ||
                key == ImGuiKey.LeftAlt || key == ImGuiKey.RightAlt ||
                key == ImGuiKey.LeftSuper || key == ImGuiKey.RightSuper ||
                key == ImGuiKey.ModCtrl || key == ImGuiKey.ModShift ||
                key == ImGuiKey.ModAlt || key == ImGuiKey.ModSuper;
    }

    private static String t(String key) {
        return Language.getInstance().get(key);
    }
}