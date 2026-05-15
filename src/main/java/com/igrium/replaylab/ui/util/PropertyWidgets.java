package com.igrium.replaylab.ui.util;

import com.igrium.replaylab.scene.obj.ReplayObject;
import lombok.experimental.UtilityClass;
import com.igrium.replaylab.ui.util.KeyWidgets.*;
import org.joml.Math;

/**
 * Wrappers around {@link KeyWidgets} to make them integrate more directly with properties.
 */
@UtilityClass
public final class PropertyWidgets {

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
    public static KeyWidgets.WidgetState dragFloatN(ReplayObject obj, String label, float speed, int playhead, String... properties) {
        // Yeaah, this is a lot of allocations and hash lookups, but it only gets called like 15 times per frame
        double[] values = new double[properties.length];
        KeyState[] keyStates = new KeyState[properties.length];

        for (int i = 0; i < properties.length; i++) {
            values[i] = obj.getPropertyOrThrow(properties[i]);
            keyStates[i] = getKeyState(obj, properties[i], values[i], playhead);
        }

        var state = KeyWidgets.dragFloatN(label, values, speed, keyStates);

        for (int i = 0; i < properties.length; i++) {
            obj.setProperty(properties[i], values[i]);
        }

        for (int idx : state.newKeys()) {
            obj.getOrCreateChannel(properties[idx]).addKeyframe(playhead, values[idx]);
            obj.getSampledValues().put(properties[idx], values[idx]);
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
