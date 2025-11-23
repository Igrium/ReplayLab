package com.igrium.replaylab.scene.key;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
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

    public enum HandleType {
        FREE, ALIGNED, VECTOR, AUTO, AUTO_CLAMPED
    }

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

    @Getter @Setter
    private HandleType handleAType = HandleType.AUTO_CLAMPED;

    @Getter @Setter
    private HandleType handleBType = HandleType.AUTO_CLAMPED;

    public Keyframe(int time, double value) {
        center.set(time, value);
    }

    public Keyframe(Keyframe other) {
        copyFrom(other);
    }

    public int getTimeInt() {
        return (int) center.x();
    }

    public double getTime() {
        return center.x();
    }

    public void setTime(double time) {
        center.x = time;
    }

    public double getValue() {
        return center.y();
    }

    public void setValue(double value) {
        center.y = value;
    }

    public void setHandleType(HandleType handleType) {
        setHandleAType(handleType);
        setHandleBType(handleType);
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

    public void setGlobalAX(double x) {
        handleA.x = x - center.x;
    }

    public void setGlobalAY(double y) {
        handleA.y = y - center.y;
    }

    public void setGlobalA(double x, double y) {
        setGlobalAX(x);
        setGlobalAY(y);
    }

    public void setGlobalBX(double x) {
        handleB.x = x - center.x;
    }

    public void setGlobalBY(double y) {
        handleB.y = y - center.y;
    }

    public void setGlobalB(double x, double y) {
        setGlobalBX(x);
        setGlobalBY(y);
    }

    public double getHandleX(int handle) {
        return switch(handle) {
            case 0 -> center.x;
            case 1 -> getGlobalAX();
            case 2 -> getGlobalBX();
            default -> throw new IndexOutOfBoundsException(handle);
        };
    }

    public double getHandleY(int handle) {
        return switch(handle) {
            case 0 -> center.y;
            case 1 -> getGlobalAY();
            case 2 -> getGlobalBY();
            default -> throw new IndexOutOfBoundsException(handle);
        };
    }

    private double estimateAutoTangent(Keyframe[] channel, int index) {
        if (index <= 0) {
            Keyframe next = channel[1];
            return (next.center.y - center.y) / (next.center.x - center.x);
        } else if (index >= channel.length - 1) {
            Keyframe prev = channel[index - 1];
            return (center.y - prev.center.y) / (center.x - prev.center.x);
        } else {
            Keyframe next = channel[index + 1];
            Keyframe prev = channel[index - 1];

            return (next.center.y - prev.center.y) / (next.center.x - prev.center.x);
        }
    }

//    /**
//     * Called when the keyframe connected to Handle A has been moved.
//     * @param prevConnection The previous version of the keyframe (a copy)
//     * @param nextConnection The new version of the keyframe
//     */
//    public void updateHandleA(Keyframe prevConnection, Keyframe nextConnection) {
//
//    }
//
//    /**
//     * Called when the keyframe connected to Handle B has been moved.
//     * @param prevConnection The previous version of the keyframe (a copy)
//     * @param nextConnection The new version of the keyframe
//     */
//    public void updateHandleB(Keyframe prevConnection, Keyframe nextConnection) {
//
//    }

    public void copyFrom(Keyframe other) {
        this.center.set(other.center);
        this.handleA.set(other.handleA);
        this.handleB.set(other.handleB);
    }

    @Override
    public int compareTo(@NotNull Keyframe o) {
        return getTimeInt() - o.getTimeInt();
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

