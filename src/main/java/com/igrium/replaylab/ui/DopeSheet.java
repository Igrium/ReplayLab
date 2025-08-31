package com.igrium.replaylab.ui;

import imgui.ImColor;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.type.ImFloat;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class DopeSheet {

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

    @Getter @Setter
    private float offsetX = 0;

    @Getter
    private float factorX = 32;

    public void setFactorX(float factorX) {
        if (factorX <= 0)
            throw new IllegalArgumentException("zoomX must be greater than 0");
        this.factorX = factorX;
    }

    // drag state
//    private boolean dragging = false;

    /**
     * A map of keys being dragged and their start time.
     */
    private final Object2FloatMap<KeyReference> dragging = new Object2FloatOpenHashMap<>();

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

    public void drawDopeSheet(List<DopeChannel> channels, Set<KeyReference> selected, int flags) {
        ImDrawList drawList = ImGui.getWindowDrawList();

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

        int channelIndex = 0;
        boolean wantsStartDragging = false;
        for (var channel : channels) {
            if (drawDopeChannel(channel, channelIndex, selected, drawList, flags)) {
                wantsStartDragging = true;
            }
            channelIndex++;
        }

        if (wantsStartDragging && !hasFlag(READONLY, flags)) {
            startDragging(selected, channels);
        }
    }

    private interface BiFloatPredicate {
        boolean test(float a, float b);
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
        int color = channelIndex % 2 == 0 ? ImColor.rgba(1, 1, 1, .25f) : ImColor.rgba(.5f, .5f, .5f, .25f);

        drawList.addRectFilled(cursorX, cursorY, cursorX + lineWidth, cursorY + lineHeight, color);

        float keySize = ImGui.getFontSize();
        float keyRadius = keySize / 2;
        float centerY = cursorY + lineHeight / 2;

        BiFloatFunction<Integer> getHoveredKey = (posX, posY) -> {
            int i = 0;
            for (var keyTime : channel.keys) {
                float centerX = cursorX + keyTime.get() * factorX;
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
            if (ImGui.isMouseDragging(0) && hovered != null) {
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

            float centerX = cursorX + keyTime.get() * factorX;
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
