package com.igrium.replaylab.ui.widgets;

import com.igrium.replaylab.object.ReplayObject;
import imgui.ImGui;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import com.igrium.replaylab.ui.widgets.KeyWidgets.*;
import org.joml.Math;

import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * Wrappers around {@link KeyWidgets} to make them integrate more directly with properties.
 */
@UtilityClass
public final class PropertyWidgets {

    public static final class PropComboScope extends ComboScope {

        private final ReplayObject obj;

        private final String property;

        private final int playhead;

        private PropComboScope(boolean open, boolean newKey, ReplayObject obj, String property, int playhead) {
            super(open, newKey);
            this.obj = obj;
            this.property = property;
            this.playhead = playhead;
        }

        @Override
        public WidgetState end() {
            WidgetState state = super.end();
            if (state.hasNewKey()) {
                var channel = obj.getOrCreateChannel(property);
                if (!channel.isLocked()) {
                    double val = obj.getPropertyOrThrow(property);
                    channel.addKeyframe(playhead, val, obj.getDefaultInterpMode(property));
                    obj.getSampledValues().put(property, val);
                }
            }
            return state;
        }
    }

    public static PropComboScope beginCombo(ReplayObject obj, String label, String preview, int playhead, String property) {
        double val = obj.getPropertyOrThrow(property);
        KeyState keyState = getKeyState(obj, property, val, playhead);

        var intScope = KeyWidgets.beginCombo(label, preview, keyState, 0);
        return new PropComboScope(intScope.isOpen(), intScope.isNewKey(), obj, property, playhead);
    }

    public static WidgetState combo(ReplayObject obj, String label, int maxVal, int playhead, IntFunction<String> toString, String property) {
        int val = (int) Math.round(obj.getPropertyOrThrow(property));
        var scope = beginCombo(obj, label, toString.apply(val), playhead, property);
        if (scope.isOpen()) {
            for (int i = 0; i < maxVal; i++) {
                if (ImGui.selectable(toString.apply(i), i == val)) {
                    obj.setProperty(property, i);
                    scope.select();
                }
            }
        }
        return scope.end();
    }

    /**
     * Draw a slider widget for a vector-like structure containing a number of properties.
     * Automatically handles keyframe insertion and highlighting.
     *
     * @param obj        Object to reference
     * @param label      Label to give the widget
     * @param speed      Slider speed
     * @param playhead   The editor's current playhead
     * @param properties The names of the properties to edit
     * @return The widget state returned by <code>dragFloatN</code>
     */
    public static WidgetState dragFloatN(ReplayObject obj, String label, float speed, int playhead, String... properties) {
        return dragFloatN(obj, label, speed, playhead, 1, properties);
    }

    /**
     * Draw a slider widget for a vector-like structure containing a number of properties.
     * Automatically handles keyframe insertion and highlighting.
     *
     * @param obj        Object to reference
     * @param label      Label to give the widget
     * @param speed      Slider speed
     * @param playhead   The editor's current playhead
     * @param factor     Multiply the rendered amount by this factor (primarily used in degrees/radians conversion)
     * @param properties The names of the properties to edit
     * @return The widget state returned by <code>dragFloatN</code>
     */
    public static WidgetState dragFloatN(ReplayObject obj, String label, float speed, int playhead, double factor, String... properties) {
        // Yeaah, this is a lot of allocations and hash lookups, but it only gets called like 15 times per frame
        double[] values = new double[properties.length];
        KeyState[] keyStates = new KeyState[properties.length];

        for (int i = 0; i < properties.length; i++) {
            values[i] = obj.getPropertyOrThrow(properties[i]) * factor;
            keyStates[i] = getKeyState(obj, properties[i], values[i], playhead);
        }

        var state = KeyWidgets.dragFloatN(label, values, speed, keyStates);

        for (int i = 0; i < properties.length; i++) {
            values[i] /= factor;
            obj.setProperty(properties[i], values[i]);
        }

        for (int idx : state.newKeys()) {
            var chan = obj.getOrCreateChannel(properties[idx]);
            if (!chan.isLocked()) {
                chan.addKeyframe(playhead, values[idx], obj.getDefaultInterpMode(properties[idx]));
                obj.getSampledValues().put(properties[idx], values[idx]);
            }
        }
        return state;
    }

    private static KeyState getKeyState(ReplayObject obj, String chName, double current, int timestamp) {
        var ch = obj.getChannel(chName);
        if (ch == null || ch.getKeyframes().isEmpty())
            return KeyState.DEFAULT;

        // Check if there's a keyframe at the playhead
        // TODO: optimize this

        double sampled = obj.getSampledValues().getOrDefault(chName, current);
        boolean valid = Math.abs(sampled - current) < 0.001f;

        if (!valid)
            return KeyState.INVALID;

        boolean atPlayhead = false;
        for (var key : ch.getKeyframes()) {
            if (key.getTimeInt() == timestamp) {
                atPlayhead = true;
                break;
            }
        }

        return atPlayhead ? KeyState.NOW : KeyState.ELSEWHERE;
    }
}
