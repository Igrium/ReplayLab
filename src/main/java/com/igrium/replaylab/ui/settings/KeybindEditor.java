package com.igrium.replaylab.ui.settings;

import com.igrium.replaylab.config.Keybinds;
import com.igrium.replaylab.config.ReplayLabConfig;
import com.igrium.replaylab.config.ShortcutUtils;
import imgui.ImGui;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import net.minecraft.util.Language;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntConsumer;

public class KeybindEditor {
    private static final Keybinds DEFAULT = new Keybinds();

    // Tracks which binding is actively being remapped
    private String currentlyEditing = null;

    private final ReplayLabConfig mutableConfig;

    public KeybindEditor(ReplayLabConfig mutableConfig) {
        this.mutableConfig = mutableConfig;
    }


    protected boolean draw() {
        Keybinds current = mutableConfig.getKeybinds();
        boolean changed = false;

        if (ImGui.beginTable("Keybinds", 2, ImGuiTableFlags.Borders)) {
            ImGui.tableSetupColumn("Action", ImGuiTableColumnFlags.WidthFixed);
            ImGui.tableSetupColumn("Shortcut", ImGuiTableColumnFlags.WidthStretch);

            changed |= drawBinding("key.replaylab.undo", current.getUndo(), DEFAULT.getUndo(), current::setUndo);
            changed |= drawBinding("key.replaylab.redo", current.getRedo(), DEFAULT.getRedo(), current::setRedo);

            changed |= drawBinding("key.replaylab.copy", current.getCopy(), DEFAULT.getCopy(), current::setCopy);
            changed |= drawBinding("key.replaylab.paste", current.getPaste(), DEFAULT.getPaste(), current::setPaste);

            ImGui.separator();

            changed |= drawBinding("key.replaylab.cameraview", current.getCameraView(), DEFAULT.getCameraView(), current::setCameraView);
            changed |= drawBinding("key.replaylab.active_to_cam", current.getActiveToCam(), DEFAULT.getActiveToCam(), current::setActiveToCam);
            changed |= drawBinding("key.replaylab.frame", current.getFrameSelected(), DEFAULT.getFrameSelected(), current::setFrameSelected);

            ImGui.separator();

            changed |= drawBinding("key.replaylab.playpause", current.getPlayPause(), DEFAULT.getPlayPause(), current::setPlayPause);
            changed |= drawBinding("key.replaylab.scene_start", current.getSceneStart(), DEFAULT.getSceneStart(), current::setSceneStart);
            changed |= drawBinding("key.replaylab.scene_end", current.getSceneEnd(), DEFAULT.getSceneEnd(), current::setSceneEnd);
            changed |= drawBinding("key.replaylab.prev_key", current.getPrevKey(), DEFAULT.getPrevKey(), current::setPrevKey);
            changed |= drawBinding("key.replaylab.next_key", current.getNextKey(), DEFAULT.getNextKey(), current::setNextKey);
            changed |= drawBinding("key.replaylab.quickmode", current.getQuickMode(), DEFAULT.getQuickMode(), current::setQuickMode);

            ImGui.separator();

            changed |= drawBinding("key.replaylab.select_all", current.getSelectAll(), DEFAULT.getSelectAll(), current::setSelectAll);
            changed |= drawBinding("key.replaylab.select_none", current.getSelectNone(), DEFAULT.getSelectNone(), current::setSelectNone);
            changed |= drawBinding("key.replaylab.delete", current.getDeleteSelected(), DEFAULT.getDeleteSelected(), current::setDeleteSelected);

            ImGui.separator();

            changed |= drawBinding("key.replaylab.add_key", current.getAddKey(), DEFAULT.getAddKey(), current::setAddKey);
            changed |= drawBinding("key.replaylab.add_key_pos", current.getAddKeyPos(), DEFAULT.getAddKeyPos(), current::setAddKeyPos);
            changed |= drawBinding("key.replaylab.add_key_rot", current.getAddKeyRot(),  DEFAULT.getAddKeyRot(), current::setAddKeyRot);
            changed |= drawBinding("key.replaylab.add_key_scale", current.getAddKeyScale(), DEFAULT.getAddKeyScale(), current::setAddKeyScale);
            changed |= drawBinding("key.replaylab.add_key_s", current.getAddKeySingle(), DEFAULT.getAddKeySingle(), current::setAddKeySingle);

            ImGui.separator();

            changed |= drawBinding("key.replaylab.local_transforms", current.getLocalTransforms(), DEFAULT.getLocalTransforms(), current::setLocalTransforms);
            changed |= drawBinding("key.replaylab.gizmo_all", current.getGizmoAll(), DEFAULT.getGizmoAll(), current::setGizmoAll);
            changed |= drawBinding("key.replaylab.gizmo_pos", current.getGizmoPos(), DEFAULT.getGizmoPos(), current::setGizmoPos);
            changed |= drawBinding("key.replaylab.gizmo_rot", current.getGizmoRot(), DEFAULT.getGizmoRot(), current::setGizmoRot);
            changed |= drawBinding("key.replaylab.gizmo_scale", current.getGizmoScale(), DEFAULT.getGizmoScale(), current::setGizmoScale);

            ImGui.separator();

            changed |= drawBinding("key.replaylab.camera_roll", current.getCameraRoll(), DEFAULT.getCameraRoll(), current::setCameraRoll);
            ImGui.setItemTooltip(t("key.replaylab.camera_roll.tooltip"));

            ImGui.endTable();
        }

        if (ImGui.button(t("gui.replaylab.keybinds.reset"))) {
            ImGui.openPopup("resetConfirm");
        }

        if (ImGui.beginPopup("resetConfirm")) {
            ImGui.text(t("gui.replaylab.keybinds.reset_confirm"));
            if (ImGui.button(t("gui.ok"))) {
                current.copyFrom(DEFAULT);
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