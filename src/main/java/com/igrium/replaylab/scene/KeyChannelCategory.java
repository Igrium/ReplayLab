package com.igrium.replaylab.scene;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import lombok.Getter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A category of keyframe channels, each making up a single object (3D vector, etc)
 */
@JsonAdapter(KeyChannelCatTypeAdapter.class)
public class KeyChannelCategory {

    @Getter
    private final List<KeyChannel> channels;

    protected KeyChannelCategory(List<KeyChannel> channels) {
        this.channels = channels;
    }

    public KeyChannelCategory() {
        this.channels = new ArrayList<>();
    }

    public KeyChannel getChannel(int index) {
        return channels.get(index);
    }

    public KeyChannelCategory copy() {
        List<KeyChannel> copied = new ArrayList<>(channels.size());
        for (KeyChannel channel : channels) {
            copied.add(channel.copy());
        }
        return new KeyChannelCategory(copied);
    }
}

class KeyChannelCatTypeAdapter extends TypeAdapter<KeyChannelCategory> {

    final KeyChannelTypeAdapter keyChAdapter = new KeyChannelTypeAdapter();

    @Override
    public void write(JsonWriter out, KeyChannelCategory value) throws IOException {
        out.beginArray();
        for (var ch : value.getChannels()) {
            keyChAdapter.write(out, ch);
        }
        out.endArray();
    }

    @Override
    public KeyChannelCategory read(JsonReader in) throws IOException {
        KeyChannelCategory cat = new KeyChannelCategory();
        in.beginArray();
        while (in.peek() != JsonToken.END_ARRAY) {
            cat.getChannels().add(keyChAdapter.read(in));
        }
        in.endArray();
        return cat;
    }
}