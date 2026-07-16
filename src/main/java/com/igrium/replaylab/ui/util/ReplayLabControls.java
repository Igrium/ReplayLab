package com.igrium.replaylab.ui.util;

import com.igrium.replaylab.config.ReplayLabConfig;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.ui.ReplayLabIcons;
import com.igrium.replaylab.util.Timestamps;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiMouseCursor;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import lombok.NonNull;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import net.minecraft.util.Util;
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
    public static final Identifier ROBOTO_MONO = Identifier.of("replaylab:roboto-mono");

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

    private static final float TIMESTAMP_DRAG_SPEED = 4f;

    private static int timestampInputId = 0;
    private static boolean timestampInputJustStarted = false;
    private static float timestampDragDelta = 0;

    /**
     * A timestamp field that can be dragged to change its value like <code>DragScalar</code>
     */
    public static boolean inputTimestamp(String label, ImInt timestamp, Timestamps.Display display, int imGuiInputTextFlags) {
        int id = ImGui.getID(label);
        // Typing mode: an ordinary editable inputText (native label, editing, and undo behavior).
        if (timestampInputId == id) {
            if (timestampInputJustStarted) {
                ImGui.setKeyboardFocusHere();
                timestampInputJustStarted = false;
            }
            timestampInBuffer.set(Timestamps.toTimestamp(timestamp.get(), display));
            boolean changed = false;
            if (ImGui.inputText(label, timestampInBuffer, imGuiInputTextFlags)) {
                try {
                    timestamp.set(Timestamps.fromTimestamp(timestampInBuffer.get()));
                    changed = true;
                } catch (NumberFormatException ignored) {
                }
            }
            if (ImGui.isItemDeactivated()) {
                timestampInputId = 0;
            }
            return changed;
        }
        // Readonly: keep the actual inputText buffer blank so click-dragging can't select text,
        // and draw the live timestamp on top via the draw list instead.
        String displayText = Timestamps.toTimestamp(timestamp.get(), display);
        timestampInBuffer.set("");
        // Frame bounds only (excludes the label, which getItemRect* would otherwise include).
        float minX = ImGui.getCursorScreenPosX();
        float minY = ImGui.getCursorScreenPosY();
        float maxX = minX + ImGui.calcItemWidth();
        ImGui.inputText(label, timestampInBuffer, imGuiInputTextFlags | ImGuiInputTextFlags.ReadOnly);
        float maxY = ImGui.getItemRectMaxY();
        float textWidth = ImGui.calcTextSizeX(displayText);
        float textX = minX + ((maxX - minX) - textWidth) * 0.5f;
        float textY = minY + ((maxY - minY) - ImGui.getTextLineHeight()) * 0.5f;
        ImGui.getWindowDrawList().addText(textX, textY,
                ImGui.getColorU32(ImGuiCol.Text), displayText);
        if (ImGui.isItemHovered()) {
            ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
        }
        // Switch to typing mode on double-click
        if (ImGui.isItemHovered() && (ImGui.isMouseDoubleClicked(0)
                || (ImGui.isMouseClicked(0) && ImGui.getIO().getKeyCtrl()))) {
            // Force the widget to fully deactivate so the editable inputText below reloads
            // its text from timestampInBuffer instead of continuing the blank readonly state.
            imgui.internal.ImGui.clearActiveID();
            timestampInputId = id;
            timestampInputJustStarted = true;
            return false;
        }
        if (ImGui.isItemActivated()) {
            timestampDragDelta = 0;
        }
        boolean changed = false;
        if (ImGui.isItemActive()) {
            ImGuiIO io = ImGui.getIO();
            if (ImGui.isMouseDragging(0)) {
                float speed = TIMESTAMP_DRAG_SPEED;
                if (io.getKeyAlt()) speed *= 0.01f;
                if (io.getKeyShift()) speed *= 10f;
                timestampDragDelta += io.getMouseDeltaX() * speed;
                int intDelta = (int) timestampDragDelta;
                timestampDragDelta -= intDelta; // keep the sub-integer remainder
                if (intDelta != 0) {
                    timestamp.set(timestamp.get() + intDelta);
                    changed = true;
                }
            }
            if (!io.getMouseDown(0)) {
                imgui.internal.ImGui.clearActiveID();
            }
        }
        if (changed) {
            imgui.internal.ImGui.markItemEdited(id);
        }
        return changed;
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

    public static void hyperlink(String text, String link) {
        ImGui.pushStyleColor(ImGuiCol.Text, ImGui.getStyle().getColor(ImGuiCol.ButtonHovered));
        ImGui.text(text);
        ImGui.popStyleColor();

        if (ImGui.isItemHovered()) {
            ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
        }

        if (ImGui.isItemClicked(0)) {
            Util.getOperatingSystem().open(link);
        }
    }

    public static int findRenderedTextEnd(String text) {
        for (int i = 0; i < text.length()-1; i++) {
            if (text.charAt(i) == '#' && text.charAt(i+1) == '#')
                return i;
        }
        return -1;
    }

    public static String getRenderedText(String text) {
        int end = findRenderedTextEnd(text);
        return end >= 0 ? text.substring(0, end) : text;
    }
}
