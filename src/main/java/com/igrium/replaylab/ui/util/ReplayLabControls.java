package com.igrium.replaylab.ui.util;

import com.igrium.replaylab.config.ReplayLabConfig;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.ui.ReplayLabIcons;
import com.igrium.replaylab.util.Timestamps;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import lombok.NonNull;
import net.minecraft.util.Language;
import org.apache.commons.lang3.mutable.Mutable;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

public class ReplayLabControls {
    private static final ImBoolean isSelected = new ImBoolean();
    private static final Logger LOGGER = LoggerFactory.getLogger(ReplayLabControls.class);

    /**
     * Draw a dropdown box with a collection of strings.
     *
     * @param label    ImGui label of the dropdown.
     * @param selected The currently selected item. While the <code>Mutable</code> itself may not be null,
     *                 the value of it is nullable.
     * @param options  The options for the user to choose from.
     * @return If the selected item was changed this frame.
     */
    public static boolean stringCombo(@NonNull String label, @NonNull Mutable<String> selected, @NonNull Iterable<? extends String> options) {
        boolean updated = false;
        String preview = selected.getValue();
        if (ImGui.beginCombo(label, preview != null ? preview : "")) {
            for (String option : options) {
                isSelected.set(Objects.equals(selected.getValue(), option));

                if (ImGui.selectable(option, isSelected)) {
                    updated = true;
                    selected.setValue(option);
                }

                if (isSelected.get()) {
                    ImGui.setItemDefaultFocus(); // Default focus on selected item
                }
            }
            ImGui.endCombo();
        }
        return updated;
    }

    /**
     * Draw a dropdown box with all the objects in the scene that match a given predicate
     *
     * @param label      ImGui label of the dropdown.
     * @param selectedId The ID of the currently selected object.
     * @param predicate  A predicate for whether a given object should be allowed.
     * @param objects    The objects to choose from.
     * @return If the selection was updated this frame.
     */
    public static boolean objectSelector(@NonNull String label, @NonNull Mutable<String> selectedId,
                                         Predicate<? super ReplayObject> predicate, Map<String, ReplayObject> objects) {
        boolean updated = false;
        ReplayObject selObj = objects.get(selectedId.getValue());

        String selDisp = selObj != null ? selObj.getDisplayName() : selectedId.getValue();
        if (ImGui.beginCombo(label, selDisp != null ? selDisp : "")) {
            for (var objEntry : objects.entrySet()) {
                if (!predicate.test(objEntry.getValue())) continue;

                boolean selected = Objects.equals(selectedId.getValue(), objEntry.getKey());
                if (ImGui.selectable(objEntry.getValue().getDisplayName() + "###" + objEntry.getKey(), selected)) {
                    updated = true;
                    selectedId.setValue(objEntry.getKey());
                }
                if (selected) {
                    ImGui.setItemDefaultFocus();
                }
            }
            ImGui.endCombo();
        }
        return updated;
    }

    public static boolean toggleButton(String label, ImBoolean pressed) {
        int activeColor = ImGui.getColorU32(ImGuiCol.ButtonHovered);
        boolean wasPressed = pressed.get();

        if (wasPressed) {
            ImGui.pushStyleColor(ImGuiCol.Button, activeColor);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, activeColor);
        } else {
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ImGui.getColorU32(ImGuiCol.Button));
        }

        boolean changed = false;
        if (ImGui.button(label)) {
            changed = true;
            pressed.set(!wasPressed);
        }

        if (wasPressed) {
            ImGui.popStyleColor();
        }
        ImGui.popStyleColor();

        return changed;
    }

    @Deprecated
    public static boolean toggleButton(char icon, String id, ImBoolean pressed, @Nullable String tooltip) {
        ImGui.pushFont(ReplayLabIcons.getFont());
        boolean result = toggleButton(icon + "###" + id, pressed);
        ImGui.popFont();

        if (tooltip != null) {
            ImGui.setItemTooltip(Language.getInstance().get(tooltip));
        }
        return result;
    }

    @Deprecated
    public static boolean iconButton(char icon, String id, @Nullable String tooltip) {
        ImGui.pushFont(ReplayLabIcons.getFont());
        boolean result = ImGui.button(icon + "###" + id);
        ImGui.popFont();

        if (tooltip != null) {
            ImGui.setItemTooltip(Language.getInstance().get(tooltip));
        }
        return result;
    }

    private static final ImString timestampInBuffer = new ImString(16);

    public static boolean inputTimestamp(String label, ImInt timestamp, Timestamps.Display display, int imGuiInputTextFlags) {
        timestampInBuffer.set(Timestamps.toTimestamp(timestamp.get(), display));
        if (ImGui.inputText(label, timestampInBuffer, imGuiInputTextFlags)) {
            String out = timestampInBuffer.get();
            try {
                timestamp.set(Timestamps.fromTimestamp(out));
                return true;
            } catch (NumberFormatException ignored) {
            }
        }
        return false;
    }

    public static boolean inputTimestamp(String label, ImInt timestamp, Timestamps.Display display) {
        return inputTimestamp(label, timestamp, display, 0);
    }

    public static boolean inputTimestamp(String label, ImInt timestamp, int imGuiInputTextFlags) {
        return inputTimestamp(label, timestamp, timestampDisplay(), imGuiInputTextFlags);
    }

    public static boolean inputTimestamp(String label, ImInt timestamp) {
        return inputTimestamp(label, timestamp, timestampDisplay(), 0);
    }

    private static Timestamps.Display timestampDisplay() {
        return ReplayLabConfig.getInstance().getTimestampMode();
    }
}
