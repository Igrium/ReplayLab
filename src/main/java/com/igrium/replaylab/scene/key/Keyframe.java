package com.igrium.replaylab.scene.key;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;

/**
 * A single keyframe in the timeline. Contains a time, scalar value, and any curve attributes.
 */
@Getter @Setter
@JsonAdapter(KeyframeTypeAdapter.class)
public class Keyframe {
    private int time;
    private double value;

    public Keyframe(int time, double value) {
        this.time = time;
        this.value = value;
    }

    public Keyframe(Keyframe other) {
        this(other.time, other.value);
    }

    public void copyFrom(Keyframe other) {
        this.time = other.time;
        this.value = other.value;
    }
}

class KeyframeTypeAdapter extends TypeAdapter<Keyframe> {

    @Override
    public void write(JsonWriter out, Keyframe value) throws IOException {
        out.beginArray();
        out.value(value.getTime());
        out.value(value.getValue());
        out.endArray();
    }

    @Override
    public Keyframe read(JsonReader in) throws IOException {
        in.beginArray();
        int time = in.nextInt();
        double value = in.nextDouble();
        in.endArray();
        return new Keyframe(time, value);
    }
}