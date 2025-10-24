package com.igrium.replaylab.scene.key;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import lombok.Getter;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector2d;

import java.io.IOException;

/**
 * A single keyframe in the timeline. Contains a time, scalar value, and any curve attributes.
 *
 * @apiNote Internally, time is represented as a double for use with beziers,
 * but it's only intended to be used as an integer.
 */
public final class Keyframe implements Comparable<Keyframe> {
    /**
     * The (mutable) center point of the keyframe. The X axis is time and the Y axis is the value.
     */
    @Getter @JsonAdapter(Vector2dSerializer.class)
    private final @NonNull Vector2d center = new Vector2d();

    /**
     * The (mutable) left handle of the keyframe, relative to <code>center</code>
     */
    @Getter @JsonAdapter(Vector2dSerializer.class)
    private final @NonNull Vector2d handleA = new Vector2d(-20.0, 0.0);

    /**
     * The (mutable) right handle of the keyframe, relative to <code>center</code>
     */
    @Getter @JsonAdapter(Vector2dSerializer.class)
    private final @NonNull Vector2d handleB = new Vector2d(20.0, 0.0);

    public Keyframe(int time, double value) {
        center.set(time, value);
    }

    public Keyframe(Keyframe other) {
        copyFrom(other);
    }

    public int getTime() {
        return (int) center.x();
    }

    public void setTime(int time) {
        center.x = time;
    }

    public double getValue() {
        return center.y();
    }

    public void setValue(double value) {
        center.y = value;
    }

    public Vector2d getGlobalA(Vector2d dest) {
        return center.add(handleA, dest);
    }

    public double getGlobalAX() {
        return center.x + handleA.x;
    }

    public double getGlobalAY() {
        return center.y + handleA.y;
    }

    public Vector2d getGlobalB(Vector2d dest) {
        return center.add(handleB, dest);
    }

    public double getGlobalBX() {
        return center.x + handleB.x;
    }

    public double getGlobalBY() {
        return center.y + handleB.y;
    }


    public void copyFrom(Keyframe other) {
        this.center.set(other.center);
        this.handleA.set(other.handleA);
        this.handleB.set(other.handleB);
    }

    @Override
    public int compareTo(@NotNull Keyframe o) {
        return getTime() - o.getTime();
    }

    private static class Vector2dSerializer extends TypeAdapter<Vector2d> {

        @Override
        public void write(JsonWriter jsonWriter, Vector2d value) throws IOException {
            jsonWriter.beginArray();
            jsonWriter.value(value.x());
            jsonWriter.value(value.y());
            jsonWriter.endArray();
        }

        @Override
        public Vector2d read(JsonReader jsonReader) throws IOException {
            jsonReader.beginArray();
            double x = jsonReader.nextDouble();
            double y = jsonReader.nextDouble();
            jsonReader.endArray();
            return new Vector2d(x, y);
        }
    }
}

