package com.igrium.replaylab.ui.panels;

import com.igrium.replaylab.ReplayLab;
import com.igrium.replaylab.config.Keybinds;
import com.igrium.replaylab.config.ReplayLabConfig;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.util.ShortcutUtils;
import imgui.ImGui;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiTableColumnFlags;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntConsumer;

public class KeybindEditor extends UIModal {
    // Tracks which binding is actively being remapped
    private String currentlyEditing = null;

    public KeybindEditor(Identifier id) {
        super(id);
    }

    @Override
    protected void drawContents(EditorState editor) {
        Keybinds current = ReplayLabConfig.getInstance().getKeybinds();
        Keybinds defBinds = new Keybinds();

        if (ImGui.beginTable("Keybinds", 2)) {
            ImGui.tableSetupColumn("Action", ImGuiTableColumnFlags.WidthFixed);
            ImGui.tableSetupColumn("Shortcut", ImGuiTableColumnFlags.WidthStretch);

            drawBinding("Undo", current.getUndo(), defBinds.getUndo(), current::setUndo);
            drawBinding("Redo", current.getRedo(), defBinds.getRedo(), current::setRedo);
            ImGui.endTable();
        }
    }

    private void drawBinding(String label, int value, int defaultValue, @Nullable IntConsumer onChanged) {
        ImGui.tableNextColumn();
        ImGui.text(Language.getInstance().get(label));

        ImGui.tableNextColumn();
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (label.equals(currentlyEditing)) {
            // If we're currently editing this binding
            ImGui.button("> Press new key <###" + label);

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
                    if (onChanged != null) onChanged.accept(newKey | mods);
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

            if (ImGui.button(btnText + "###" + label)) {
                currentlyEditing = label; // Switch into edit mode
            }
        }
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
}