package com.igrium.replaylab.util;

import com.google.gson.*;

import java.lang.reflect.Type;

public class GsonSerializationContext implements JsonSerializationContext, JsonDeserializationContext {

    private final Gson gson;

    public GsonSerializationContext(Gson gson) {
        this.gson = gson;
    }

    @Override
    public <T> T deserialize(JsonElement json, Type typeOfT) throws JsonParseException {
        return gson.fromJson(json, typeOfT);
    }

    @Override
    public JsonElement serialize(Object src) {
        return gson.toJsonTree(src);
    }

    @Override
    public JsonElement serialize(Object src, Type typeOfSrc) {
        return gson.toJsonTree(src, typeOfSrc);
    }
}
