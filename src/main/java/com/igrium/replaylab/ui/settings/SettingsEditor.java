package com.igrium.replaylab.ui.settings;

import com.igrium.replaylab.config.ReplayLabConfig;
import com.igrium.replaylab.math.DynamicRotation;
import com.igrium.replaylab.scene.key.Keyframe;
import com.igrium.replaylab.util.Timestamps;
import imgui.ImGui;
import imgui.type.ImBoolean;
import lombok.Getter;
import net.minecraft.util.Language;
import org.apache.commons.lang3.function.BooleanConsumer;

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

        ReplayLabConfig c = mutableConfig;

        if (ImGui.beginTabBar("settings")) {

            if (ImGui.beginTabItem(t("gui.replaylab.general"))) {

                /// Behavior
                ImGui.separatorText(tt("settings.replaylab.behavior"));

                changed |= drawCheckBox(t("settings.replaylab.auto_set_camera"),
                        c.isAutoSetCamera(), c::setAutoSetCamera);
                ImGui.setItemTooltip(tt("settings.replaylab.auto_set_camera.tooltip"));

                changed |= drawCheckBox(t("settings.replaylab.inspect_on_create"),
                        c.isInspectOnCreate(), c::setInspectOnCreate);
                ImGui.setItemTooltip(tt("settings.replaylab.inspect_on_create.tooltip"));

                if (ImGui.beginCombo(t("settings.replaylab.default_handle_type"), t(c.getDefaultHandleType().getTranslationKey()))) {
                    for (Keyframe.HandleType type : Keyframe.HandleType.values()) {
                        if (ImGui.selectable(t(type.getTranslationKey()), type == c.getDefaultHandleType())) {
                            c.setDefaultHandleType(type);
                            changed = true;
                        }
                    }
                    ImGui.endCombo();
                }

                /// Interface
                ImGui.separatorText(tt("settings.replaylab.interface"));

                changed |= drawCheckBox(t("settings.replaylab.display_degrees"), c.isDisplayDegrees(), c::setDisplayDegrees);
                ImGui.setItemTooltip(tt("settings.replaylab.display_degrees.tooltip"));

                if (ImGui.beginCombo(t("settings.replaylab.timestamp_mode"), t(c.getTimestampMode().getTranslationKey()))) {
                    for (var val : Timestamps.Display.values()) {
                        if (ImGui.selectable(t(val.getTranslationKey()), val == c.getTimestampMode())) {
                            c.setTimestampMode(val);
                            changed = true;
                        }
                    }
                    ImGui.endCombo();
                }

                /// 3D OBJECT SETTINGS
                ImGui.separatorText(tt("settings.replaylab.settings_3d"));

                if (ImGui.beginCombo(t("settings.replaylab.default_rot_mode"), t(c.getDefaultRotMode().getLabel()))) {
                    for (DynamicRotation.RotationMode mode : DynamicRotation.RotationMode.values()) {
                        if (ImGui.selectable(t(mode.getLabel()), mode == c.getDefaultRotMode())) {
                            c.setDefaultRotMode(mode);
                            changed = true;
                        }
                    }
                    ImGui.endCombo();
                }
                ImGui.setItemTooltip(tt("settings.replaylab.default_rot_mode.tooltip"));

                changed |= drawCheckBox(t("settings.replaylab.rot_mode_convert"), c.isRotModeConvert(), c::setRotModeConvert);
                ImGui.setItemTooltip(tt("settings.replaylab.rot_mode_convert.tooltip"));

                ImGui.endTabItem();
            }

            if (ImGui.beginTabItem(t("gui.replaylab.keybinds"))) {
                changed |= keybindEditor.draw();
                ImGui.endTabItem();
            }

            ImGui.endTabBar();
        }
        return changed;
    }

    private boolean drawCheckBox(String label, boolean value, BooleanConsumer setter) {
        tmpBool.set(value);
        if (ImGui.checkbox(label, tmpBool)) {
            setter.accept(tmpBool.get());
            return true;
        }
        return false;
    }

    private final ImBoolean tmpBool = new ImBoolean(false);

    private static String t(String key) {
        return Language.getInstance().get(key) + "###" + key;
    }

    private static String tt(String key) {
        return Language.getInstance().get(key);
    }
}
