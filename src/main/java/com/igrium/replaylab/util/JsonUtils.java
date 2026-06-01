package com.igrium.replaylab.util;

import com.google.gson.JsonArray;
import lombok.experimental.UtilityClass;
import org.joml.*;

@UtilityClass
public class JsonUtils {
    public static JsonArray writeJsonVec(Vector3fc vec) {
        JsonArray arr = new JsonArray();
        arr.add(vec.x());
        arr.add(vec.y());
        arr.add(vec.z());
        return arr;
    }

    public static JsonArray writeJsonVec(Vector3dc vec) {
        JsonArray arr = new JsonArray();
        arr.add(vec.x());
        arr.add(vec.y());
        arr.add(vec.z());
        return arr;
    }

    public static Vector3f readJsonVec(JsonArray arr, Vector3f dest) {
        dest.x = arr.get(0).getAsFloat();
        dest.y = arr.get(1).getAsFloat();
        dest.z = arr.get(2).getAsFloat();
        return dest;
    }

    public static Vector3d readJsonVec(JsonArray arr, Vector3d dest) {
        dest.x = arr.get(0).getAsFloat();
        dest.y = arr.get(1).getAsFloat();
        dest.z = arr.get(2).getAsFloat();
        return dest;
    }

    public static JsonArray writeJsonQuat(Quaternionfc quat) {
        JsonArray arr = new JsonArray();
        arr.add(quat.w());
        arr.add(quat.x());
        arr.add(quat.y());
        arr.add(quat.z());
        return arr;
    }

    public static Quaternionf readJsonQuat(JsonArray arr, Quaternionf dest) {
        dest.w = arr.get(0).getAsFloat();
        dest.x = arr.get(1).getAsFloat();
        dest.y = arr.get(2).getAsFloat();
        dest.z = arr.get(3).getAsFloat();
        return dest;
    }
}
