package com.igrium.replaylab.ui.util;

import com.igrium.replaylab.scene.obj.ReplayObject;
import imgui.ImGui;
import imgui.type.ImBoolean;
import lombok.NonNull;
import org.apache.commons.lang3.mutable.Mutable;

import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

public class ReplayLabControls {
    private static final ImBoolean isSelected = new ImBoolean();

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
     * @param label        ImGui label of the dropdown.
     * @param selectedName The name of the currently selected object.
     * @param predicate    A predicate for whether a given object should be allowed.
     * @param objects      The objects to choose from.
     * @return If the selection was updated this frame.
     */
    public static boolean objectSelector(@NonNull String label, Mutable<String> selectedName,
                                         Predicate<? super ReplayObject> predicate, Map<String, ReplayObject> objects) {
        return stringCombo(label, selectedName, () -> objects.entrySet()
                .stream()
                .filter(e -> predicate.test(e.getValue()))
                .map(Map.Entry::getKey)
                .iterator());
    }

}
