package com.igrium.replaylab.ui.util;

import com.google.common.primitives.ImmutableIntArray;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiKey;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import net.minecraft.util.math.ColorHelper;

/**
 * A collection of widgets which can accept the "insert keyframe" keybind
 */
@UtilityClass
public final class KeyWidgets {

    public static final int COLOR_KEYED_NOW_HOVER = 0xFF27B7D1;
    public static final int COLOR_KEYED_NOW = ColorHelper.withAlpha(128, COLOR_KEYED_NOW_HOVER);

    public static final int COLOR_KEYED_ELSEWHERE_HOVER = 0xFF29C75F;
    public static final int COLOR_KEYED_ELSEWHERE = ColorHelper.withAlpha(128, COLOR_KEYED_ELSEWHERE_HOVER);

    public static final int COLOR_KEYED_INVALID_HOVER = 0xFF3184DF;
    public static final int COLOR_KEYED_INVALID = ColorHelper.withAlpha(128, COLOR_KEYED_INVALID_HOVER);

    public enum KeyState {
        DEFAULT, NOW, ELSEWHERE, INVALID
    }

    public record WidgetState(boolean updated, @NonNull ImmutableIntArray newKeys) {

        public static final WidgetState DEFAULT = new WidgetState(false, ImmutableIntArray.of());
        public static final WidgetState UPDATED = new WidgetState(true, ImmutableIntArray.of());
        public static final WidgetState NEW_KEY = new WidgetState(false, ImmutableIntArray.of(0));
        public static final WidgetState UPDATED_NEW_KEY = new WidgetState(true, ImmutableIntArray.of(0));

        public boolean newKey() {
            return !newKeys.isEmpty();
        }

        public static WidgetState of(boolean updated, boolean newKey) {
            if (updated) {
                return newKey ? UPDATED_NEW_KEY : UPDATED;
            } else {
                return newKey ? NEW_KEY : DEFAULT;
            }
        }

        public static WidgetState of(boolean updated, int... newKeys) {
            return new WidgetState(updated, ImmutableIntArray.copyOf(newKeys));
        }
    }

    /// === WIDGETS ===

    public static WidgetState dragFloat3(String label, float[] v, float vSpeed, KeyState state) {
        pushStyle(state);
        boolean updated = ImGui.dragFloat3(label, v, vSpeed);
        boolean shortcut = shortcut();
        popStyle(state);

        return WidgetState.of(updated, shortcut);
    }

    /// === UTILITY ===

    private static boolean shortcut() {
        return ImGui.isItemHovered() && ImGui.shortcut(ImGuiKey.I);
    }

    private static void pushStyle(KeyState state) {
        switch (state) {
            case NOW -> {
                ImGui.pushStyleColor(ImGuiCol.FrameBg, COLOR_KEYED_NOW);
                ImGui.pushStyleColor(ImGuiCol.FrameBgActive, COLOR_KEYED_NOW);
                ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, COLOR_KEYED_NOW_HOVER);
            }
            case ELSEWHERE -> {
                ImGui.pushStyleColor(ImGuiCol.FrameBg, COLOR_KEYED_ELSEWHERE);
                ImGui.pushStyleColor(ImGuiCol.FrameBgActive, COLOR_KEYED_ELSEWHERE);
                ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, COLOR_KEYED_ELSEWHERE_HOVER);
            }
            case INVALID -> {
                ImGui.pushStyleColor(ImGuiCol.FrameBg, COLOR_KEYED_INVALID);
                ImGui.pushStyleColor(ImGuiCol.FrameBgActive, COLOR_KEYED_INVALID);
                ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, COLOR_KEYED_INVALID_HOVER);
            }
        }
    }

    private static void popStyle(KeyState state) {
        if (state != KeyState.DEFAULT) {
            ImGui.popStyleColor(3);
        }
    }
}
