package com.igrium.replaylab.ui.util;

import com.igrium.replaylab.config.Keybinds;
import com.igrium.replaylab.config.ReplayLabConfig;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import net.minecraft.util.Language;
import net.minecraft.util.math.ColorHelper;
import org.jetbrains.annotations.Nullable;

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

    private static final int CONTEXT_ADD_KEY = 1;
    private static final int CONTEXT_ADD_KEY_S = 2;

    public enum KeyState {
        DEFAULT, NOW, ELSEWHERE, INVALID
    }

    /**
     * The state of a keyframable widget after it has been rendered.
     * @param updated The indices of the values which have been updated this frame.
     * @param newKeys The indices of the values that should have a keyframe inserted.
     */
    public record WidgetState(int @NonNull [] updated, int @NonNull[] newKeys) {

        private static final int[] EMPTY = new int[0];
        private static final int[] SINGLE = new int[] {0};

        public static final WidgetState DEFAULT = new WidgetState(EMPTY, EMPTY);
        public static final WidgetState UPDATED = new WidgetState(SINGLE, EMPTY);
        public static final WidgetState NEW_KEY = new WidgetState(EMPTY, SINGLE);
        public static final WidgetState UPDATED_NEW_KEY = new WidgetState(SINGLE, SINGLE);

        public boolean isUpdated() {
            return updated.length > 0;
        }

        public boolean isUpdated(int idx) {
            // Data set is likely very small, so complexity doesn't matter
            for (var i : updated) {
                if (i == idx) return true;
            }
            return false;
        }

        public boolean hasNewKey() {
            return newKeys.length > 0;
        }

        public static WidgetState of(boolean updated, boolean newKey) {
            if (updated) {
                return newKey ? UPDATED_NEW_KEY : UPDATED;
            } else {
                return newKey ? NEW_KEY : DEFAULT;
            }
        }

        public static WidgetState of(int @Nullable [] updated, int @Nullable [] newKeys) {
            return new WidgetState(updated != null ? updated : EMPTY, newKeys != null ? newKeys : EMPTY);
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

    public static WidgetState dragFloatN(String label, double[] v, float vSpeed, KeyState... states) {
        ImGui.beginGroup();
        ImGui.pushID(label);

        double[] active = new double[1];

        float innerSpacingX = ImGui.getStyle().getItemInnerSpacingX();

        float drawableWidth = ImGui.calcItemWidth() - innerSpacingX * (v.length - 1);
        float itemWidth = (float) Math.floor(drawableWidth / v.length);

        IntList newKeys = null;
        boolean allNewKeys = !ImGui.getIO().getKeyAlt();
        boolean anyNewKey = false;

        IntList updatedValues = null;

        for (int i = 0; i < v.length; i++) {
            ImGui.pushID(i);
            ImGui.pushItemWidth(itemWidth);

            if (i > 0) {
                ImGui.sameLine(0, innerSpacingX);
            }

            KeyState state;
            if (states.length > 0) {
                state = states[Math.min(states.length - 1, i)];
            } else {
                state = KeyState.DEFAULT;
            }

            active[0] = v[i];

            pushStyle(state);
            boolean updated = ImGui.dragScalar("", active, vSpeed);
            boolean wantNewKey = shortcut();
            boolean wantNewKeySingle = shortcutAlt();
            popStyle(state);

            if (updated) {
                // Don't allocate unless we need it.
                if (updatedValues == null)
                    updatedValues = new IntArrayList(v.length);

                updatedValues.add(i);
            }

            int ctxResult = drawContextMenu();
            if (hasFlag(ctxResult, CONTEXT_ADD_KEY)) {
                wantNewKey = true;
            }
            if (hasFlag(ctxResult, CONTEXT_ADD_KEY_S)) {
                wantNewKeySingle = true;
            }

            v[i] = active[0];
            if (wantNewKeySingle) {
                // Don't allocate list unless we need it.
                if (newKeys == null)
                    newKeys = new IntArrayList(v.length);
                newKeys.add(i);
            } else if (wantNewKey) {
                anyNewKey = true;
            }

            ImGui.popItemWidth();
            ImGui.popID();
        }

        ImGui.popID();

        int labelEnd = findRenderedTextEnd(label);
        String drawnLabel = labelEnd > 0 ? label.substring(0, labelEnd) : label;
        if (!drawnLabel.isBlank()) {
            ImGui.sameLine(0, innerSpacingX);
            ImGui.text(drawnLabel);
        }

        ImGui.endGroup();

        int[] newKeyArray = null;
        if (allNewKeys && anyNewKey) {
            newKeyArray = new int[v.length];
            for (int i = 0; i < v.length; i++) {
                newKeyArray[i] = i;
            }
        } else if (newKeys != null) {
            newKeyArray = newKeys.toIntArray();
        }


        return WidgetState.of(updatedValues != null ? updatedValues.toIntArray() : null, newKeyArray);
    }

    /// === UTILITY ===

    private static int drawContextMenu() {
        int result = 0;
        if (ImGui.beginPopupContextItem()) {
            if (ImGui.menuItem(t("key.replaylab.add_key")))
                result |= CONTEXT_ADD_KEY;

            if (ImGui.menuItem(t("key.replaylab.add_key_s")))
                result |= CONTEXT_ADD_KEY_S;

            ImGui.endPopup();
        }
        return result;
    }

    private static boolean shortcut() {
        return ImGui.isItemHovered() && ImGui.shortcut(Keybinds.addKey(), 1 << 12); // ImGuiInputFlags_RouteGlobal (why is this not in the bindings???)
    }

    private static boolean shortcutAlt() {
        return ImGui.isItemHovered() && ImGui.shortcut(Keybinds.addKeySingle(), 1 << 12);
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

    private static int findRenderedTextEnd(String text) {
        for (int i = 0; i < text.length()-1; i++) {
            if (text.charAt(i) == '#' && text.charAt(i+1) == '#')
                return i;
        }
        return -1;
    }

    // Translate
    private static String t(String key) {
        return Language.getInstance().get(key) + "###" + key;
    }

    private static boolean hasFlag(int flags, int flag) {
        return (flags & flag) == flag;
    }
}
