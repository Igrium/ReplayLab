package com.igrium.replaylab.ui;

import imgui.ImColor;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiStyleVar;
import imgui.type.ImFloat;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public class DopeSheetOld {

    /**
     * Don't allow editing
     */
    public static final int READONLY = 1;

    /**
     * Fill the available height, even if there aren't enough channels to do it natively.
     * (width is always filled)
     */
    public static final int FILL_HEIGHT = 2;

    /**
     * Reset the view bounds this frame.
     */
    public static final int RESET_VIEW = 4;

    /**
     * If set, snap keyframes to increments of 1 while editing.
     */
    public static final int SNAP_KEYS = 8;

    /**
     * Don't draw the header
     */
    public static final int NO_HEADER = 16;

    /**
     * Don't draw the playhead
     */
    public static final int NO_PLAYHEAD = 32;

    /**
     * Do not allow the playhead to be moved manually by the user
     */
    public static final int READONLY_PLAYHEAD = 64;

    /**
     * Snap the playhead to increments of 1 while scrubbing
     */
    public static final int SNAP_PLAYHEAD = 128;

    /**
     * Don't draw ticks.
     */
    public static final int NO_TICKS = 64;

    public record DopeChannel(String name, List<ImFloat> keys) {};
    public record KeyReference(int channel, int key) {
        public @Nullable ImFloat get(List<DopeChannel> channels) {
            if (key < 0 || channel < 0 || channel >= channels.size()) {
                return null;
            }
            var c = channels.get(channel);
            if (key >= c.keys.size()) {
                return null;
            }
            return c.keys.get(key);
        }
    };

    /**
     * The offset in pixels to render the start of the timeline.
     */
    @Getter @Setter
    private float offsetX = 0;

    @Getter
    private float factorX = 32;

    public void setFactorX(float factorX) {
        if (factorX <= 0)
            throw new IllegalArgumentException("zoomX must be greater than 0");
        this.factorX = factorX;
    }

    /**
     * The interval between major ticks. Set to <= 0 to disable.
     */
    @Getter @Setter
    private int majorTickInterval = 10;

    // drag state
//    private boolean dragging = false;

    /**
     * A map of keys being dragged and their start time.
     */
    private final Object2FloatMap<KeyReference> dragging = new Object2FloatOpenHashMap<>();

    private boolean wasMouseDragging;
    private boolean mouseStartedDragging;

    private boolean isDragging() {
        return !dragging.isEmpty();
    }

    private void startDragging(Set<KeyReference> selected, List<DopeChannel> channels) {
        for (var ref : selected) {
            ImFloat val = ref.get(channels);
            if (val != null) {
                dragging.put(ref, val.floatValue());
            }
        }
    }

    private float tickToPixel(float tick) {
        return tick * factorX - offsetX;
    }

    private float pixelToTick(float pixel) {
        return (pixel + offsetX) / factorX;
    }

    public void drawDopeSheet(List<DopeChannel> channels, Set<KeyReference> selected, @Nullable ImFloat playhead, int flags) {
        ImDrawList drawList = ImGui.getWindowDrawList();

        if (ImGui.isMouseDragging(0, 1)) {
            mouseStartedDragging = !wasMouseDragging;
            wasMouseDragging = true;
        } else {
            wasMouseDragging = false;
            mouseStartedDragging = false;
        }

        boolean header = !hasFlag(NO_HEADER, flags);

        float width = ImGui.calcItemWidth();
        float height = ImGui.getTextLineHeightWithSpacing() * channels.size();

        // HEADER
        if (header) {
            height += 1;


        }

        if (isDragging()) {
            if (ImGui.isMouseDragging(0, 0)) {
                float dx = ImGui.getMouseDragDeltaX() / factorX;
                for (var entry : dragging.object2FloatEntrySet()) {
                    var time = entry.getKey().get(channels);
                    if (time != null) {
                        float tPrime = entry.getFloatValue() + dx;
                        if (hasFlag(SNAP_KEYS, flags)) {
                            tPrime = Math.round(tPrime);
                        }
                        time.set(tPrime);
                    }
                }
            } else {
                dragging.clear();
            }
        }

        float maxNameLength = 0;
        for (var c : channels) {
            float len = ImGui.calcTextSize(c.name).x;
            if (len > maxNameLength)
                maxNameLength = len;
        }

        int channelIndex = 0;
        boolean wantsStartDragging = false;

        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, ImGui.getStyle().getItemSpacingX(), 0);

        float localCursorX = ImGui.getCursorPosX();
        ImGui.dummy(maxNameLength, 0);
        ImGui.sameLine();
        drawHeader(playhead, drawList, flags);
        for (var channel : channels) {
            ImGui.alignTextToFramePadding();
            ImGui.text(channel.name);
            ImGui.sameLine(localCursorX + maxNameLength + ImGui.getStyle().getItemSpacingX());
            if (drawDopeChannel(channel, channelIndex, selected, drawList, flags)) {
                wantsStartDragging = true;
            }
            channelIndex++;
        }

        ImGui.popStyleVar();

        if (wantsStartDragging && !hasFlag(READONLY, flags)) {
            startDragging(selected, channels);
        }
    }

    private void drawHeader(@Nullable ImFloat playhead, ImDrawList drawList, int flags) {
        ImGui.pushID("Dope Header");
//        float currentPixel = 0;

        float cursorX = ImGui.getCursorScreenPosX();
        float cursorY = ImGui.getCursorScreenPosY();

        float lineHeight = ImGui.getFrameHeight();
        float lineWidth = ImGui.calcItemWidth();

        ImGui.invisibleButton("##canvas", lineWidth, lineHeight);

        int drawNumber = majorTickInterval * Math.round(pixelToTick(0) / majorTickInterval);
        while (tickToPixel(drawNumber) < lineWidth) {
            float globalX = cursorX + tickToPixel(drawNumber);
            String str = Integer.toString(drawNumber);
            float radius = ImGui.calcTextSize(str).x;
            drawList.addText(globalX - radius, cursorY, ImColor.rgb(255, 255, 255), str);
            drawNumber += majorTickInterval;
        }

        ImGui.popID();
    }

    private interface BiFloatFunction<T> {
        T apply(float a, float b);
    }

    private boolean drawDopeChannel(DopeChannel channel, int channelIndex, Set<KeyReference> selected, ImDrawList drawList, int flags) {
        ImGui.pushID("Dope Channel " + channel.name());

        float lineHeight = ImGui.getFrameHeight();
        float lineWidth = ImGui.calcItemWidth();

        float cursorX = ImGui.getCursorScreenPosX();
        float cursorY = ImGui.getCursorScreenPosY();


        // Background
        int color = channelIndex % 2 == 0 ? ImColor.rgba(1, 1, 1, .05f) : ImColor.rgba(.5f, .5f, .5f, .05f);

        drawList.addRectFilled(cursorX, cursorY, cursorX + lineWidth, cursorY + lineHeight, color);

        float keySize = ImGui.getFontSize();
        float keyRadius = keySize / 2;
        float centerY = cursorY + lineHeight / 2;

        BiFloatFunction<Integer> getHoveredKey = (posX, posY) -> {
            int i = 0;
            for (var keyTime : channel.keys) {
                float centerX = cursorX + tickToPixel(keyTime.get());
                if (centerX - keyRadius - 2 < posX && posX < centerX + keyRadius + 2
                        && centerY - keyRadius - 2 <= posY && posY <= centerY + keyRadius + 2) {
                    return i;
                }
                i++;
            }
            return null;
        };

        ImGui.invisibleButton("##canvas", lineWidth, lineHeight);

        boolean wantsStartDragging = false;

        float mx = ImGui.getMousePosX();
        float my = ImGui.getMousePosY();
        Integer hovered = getHoveredKey.apply(mx, my);

        if (!isDragging() && ImGui.isItemHovered()) {
            if (mouseStartedDragging && hovered != null) {
                wantsStartDragging = true;
            } else if (ImGui.isMouseClicked(0)) {
                if (ImGui.getIO().getKeyCtrl()) {
                    if (hovered != null) {
                        KeyReference keyRef = new KeyReference(channelIndex, hovered);
                        if (!selected.remove(keyRef)) {
                            selected.add(keyRef);
                        }
                    }
                } else if (hovered != null) {
                    KeyReference keyRef = new KeyReference(channelIndex, hovered);
                    selected.clear();
                    selected.add(keyRef);
                }
            }
        }

        int i = 0;
        for (ImFloat keyTime : channel.keys()) {
            boolean isSelected = selected.contains(new KeyReference(channelIndex, i));
            int keyColor = isSelected ? ImColor.rgb(1f, 1f, 1f) : ImColor.rgb(.5f, .5f, .5f);

            float centerX = cursorX + tickToPixel(keyTime.get());
            drawList.addNgonFilled(centerX, centerY, keyRadius, keyColor, 4);
            i++;
        }

        ImGui.popID();
        return wantsStartDragging;
    }

    private static boolean hasFlag(int flag, int flags) {
        return (flags & flag) == flag;
    }
}
