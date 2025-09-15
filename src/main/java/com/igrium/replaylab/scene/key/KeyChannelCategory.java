package com.igrium.replaylab.scene.key;

import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * A category of keyframe channels, each making up a single object (3D vector, etc)
 */
@JsonAdapter(KeyCategorySerializer.class)
public class KeyChannelCategory {

    @Getter
    private final List<KeyChannel> channels;

    KeyChannelCategory(List<KeyChannel> channels) {
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

// Custom serializer serializes only the channels
class KeyCategorySerializer implements JsonSerializer<KeyChannelCategory>, JsonDeserializer<KeyChannelCategory> {

    private final TypeToken<List<KeyChannel>> channelTypeToken = new TypeToken<>() {};

    @Override
    public KeyChannelCategory deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        return new KeyChannelCategory(context.deserialize(json.getAsJsonObject(), channelTypeToken.getType()));
    }

    @Override
    public JsonElement serialize(KeyChannelCategory src, Type typeOfSrc, JsonSerializationContext context) {
        return context.serialize(src.getChannels());
    }
}