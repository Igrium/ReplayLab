package com.igrium.replaylab.scene;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A single "channel" of keyframes. A given channel always contain a single curve of scalar values.
 * If you need to tie multiple values together (like a vector), use a {@link KeyChannelCategory}
 */
@JsonAdapter(KeyChannelTypeAdapter.class)
public class KeyChannel {

    @Getter
    private final List<Keyframe> keys;

    protected KeyChannel(List<Keyframe> keyframes) {
        this.keys = keyframes;
    }

    public KeyChannel() {
        this.keys = new ArrayList<>();
    }

    /**
     * Sample the curve at a given timestamp.
     * @param timestamp Timestamp to sample at.
     * @return The scalar value of the curve at that time.
     */
    public double sample(int timestamp) {
        return 0; // TODO: implement
    }

    public KeyChannel copy() {
        List<Keyframe> copied = new ArrayList<>(keys.size());
        for (Keyframe key : keys) {
            copied.add(new Keyframe(key));
        }
        return new KeyChannel(copied);
    }
}

class KeyChannelTypeAdapter extends TypeAdapter<KeyChannel> {

    final KeyframeTypeAdapter keyAdapter = new KeyframeTypeAdapter();

    @Override
    public void write(JsonWriter out, KeyChannel value) throws IOException {
        out.beginArray();
        for (var key : value.getKeys()) {
            keyAdapter.write(out, key);
        }
        out.endArray();
    }

    @Override
    public KeyChannel read(JsonReader in) throws IOException {
        KeyChannel value = new KeyChannel();
        in.beginArray();
        while (in.peek() != JsonToken.END_ARRAY) {
            value.getKeys().add(keyAdapter.read(in));
        }
        in.endArray();
        return value;
    }
}
