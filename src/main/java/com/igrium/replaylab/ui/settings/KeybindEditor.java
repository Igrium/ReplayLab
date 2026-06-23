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

            changed |= drawBinding("key.replaylab.undo", current.getUndo(), defBinds.getUndo(), current::setUndo);
            changed |= drawBinding("key.replaylab.redo", current.getRedo(), defBinds.getRedo(), current::setRedo);

            changed |= drawBinding("key.replaylab.copy", current.getCopy(), defBinds.getCopy(), current::setCopy);
            changed |= drawBinding("key.replaylab.paste", current.getPaste(), defBinds.getPaste(), current::setPaste);

            ImGui.separator();

            changed |= drawBinding("key.replaylab.playpause", current.getPlayPause(), defBinds.getPlayPause(), current::setPlayPause);
            changed |= drawBinding("key.replaylab.cameraview", current.getCameraView(), defBinds.getCameraView(), current::setCameraView);
            changed |= drawBinding("key.replaylab.active_to_cam", current.getActiveToCam(), defBinds.getActiveToCam(), current::setActiveToCam);
            changed |= drawBinding("key.replaylab.frame", current.getFrameSelected(), defBinds.getFrameSelected(), current::setFrameSelected);

            ImGui.separator();

            changed |= drawBinding("key.replaylab.scene_start", current.getSceneStart(), defBinds.getSceneStart(), current::setSceneStart);
            changed |= drawBinding("key.replaylab.scene_end", current.getSceneEnd(), defBinds.getSceneEnd(), current::setSceneEnd);
            changed |= drawBinding("key.replaylab.prev_key", current.getPrevKey(), defBinds.getPrevKey(), current::setPrevKey);
            changed |= drawBinding("key.replaylab.next_key", current.getNextKey(), defBinds.getNextKey(), current::setNextKey);

            ImGui.separator();

            changed |= drawBinding("key.replaylab.select_all", current.getSelectAll(), defBinds.getSelectAll(), current::setSelectAll);
            changed |= drawBinding("key.replaylab.select_none", current.getSelectNone(), defBinds.getSelectNone(), current::setSelectNone);
            changed |= drawBinding("key.replaylab.delete", current.getDeleteSelected(), defBinds.getDeleteSelected(), current::setDeleteSelected);

            ImGui.separator();

            changed |= drawBinding("key.replaylab.add_key", current.getAddKey(), defBinds.getAddKey(), current::setAddKey);
            changed |= drawBinding("key.replaylab.add_key_pos", current.getAddKeyPos(), defBinds.getAddKeyPos(), current::setAddKeyPos);
            changed |= drawBinding("key.replaylab.add_key_rot", current.getAddKeyRot(),  defBinds.getAddKeyRot(), current::setAddKeyRot);
            changed |= drawBinding("key.replaylab.add_key_scale", current.getAddKeyScale(), defBinds.getAddKeyScale(), current::setAddKeyScale);
            changed |= drawBinding("key.replaylab.add_key_s", current.getAddKeySingle(), defBinds.getAddKeySingle(), current::setAddKeySingle);

            ImGui.separator();

            changed |= drawBinding("key.replaylab.local_transforms", current.getLocalTransforms(), defBinds.getLocalTransforms(), current::setLocalTransforms);
            changed |= drawBinding("key.replaylab.gizmo_all", current.getGizmoAll(), defBinds.getGizmoAll(), current::setGizmoAll);
            changed |= drawBinding("key.replaylab.gizmo_pos", current.getGizmoPos(), defBinds.getGizmoPos(), current::setGizmoPos);
            changed |= drawBinding("key.replaylab.gizmo_rot", current.getGizmoRot(), defBinds.getGizmoRot(), current::setGizmoRot);
            changed |= drawBinding("key.replaylab.gizmo_scale", current.getGizmoScale(), defBinds.getGizmoScale(), current::setGizmoScale);

            ImGui.separator();

            changed |= drawBinding("key.replaylab.camera_roll", current.getCameraRoll(), defBinds.getCameraRoll(), current::setCameraRoll);
            ImGui.setItemTooltip(t("key.replaylab.camera_roll.tooltip"));

            ImGui.endTable();
        }

        if (ImGui.button(t("gui.replaylab.keybinds.reset"))) {
            ImGui.openPopup("resetConfirm");
        }

        if (ImGui.beginPopup("resetConfirm")) {
            ImGui.text(t("gui.replaylab.keybinds.reset_confirm"));
            if (ImGui.button(t("gui.ok"))) {
                current.copyFrom(defBinds);
                ImGui.closeCurrentPopup();
                changed = true;
            }
            ImGui.endPopup();
        }

        return changed;
    }

    private boolean drawBinding(String labelKey, int value, int defaultValue, @Nullable IntConsumer onChanged) {
        boolean changed = false;
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text(t(labelKey));

        ImGui.tableNextColumn();
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (labelKey.equals(currentlyEditing)) {
            // If we're currently editing this binding
            ImGui.button(t("gui.replaylab.keybind.new") + "###" + labelKey, -1, 0);

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
            String btnText = ShortcutUtils.getChordLabel(value);

            if (ImGui.button(btnText + "###" + labelKey, -1, 0)) {
                currentlyEditing = labelKey; // Switch into edit mode
            }

            if (onChanged != null && ImGui.beginPopupContextItem()) {
                if (ImGui.menuItem(t("gui.replaylab.keybind.reset"))) {
                    onChanged.accept(defaultValue);
                    changed = true;
                }
                ImGui.endPopup();
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
                key == ImGuiKey.ImGuiMod_Ctrl || key == ImGuiKey.ImGuiMod_Shift ||
                key == ImGuiKey.ImGuiMod_Alt || key == ImGuiKey.ImGuiMod_Super;
    }

    private static String t(String key) {
        return Language.getInstance().get(key);
    }
}