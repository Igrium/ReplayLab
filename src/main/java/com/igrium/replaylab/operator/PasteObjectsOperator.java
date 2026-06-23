package com.igrium.replaylab.operator;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.scene.obj.ReplayObjects;
import com.igrium.replaylab.scene.obj.SerializedReplayObject;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class PasteObjectsOperator implements ReplayOperator {

    private static final TypeToken<Map<String, SerializedReplayObject>> TYPE_TOKEN = new TypeToken<>() {};
    private static final Gson GSON = new Gson();

    public static @Nullable PasteObjectsOperator create(String json, Consumer<? super JsonParseException> onError) {
        try {
            var serialized = GSON.fromJson(json, TYPE_TOKEN);
            return new PasteObjectsOperator(serialized);
        } catch (JsonParseException e) {
            LoggerFactory.getLogger("ReplayLab/PasteObjectsOperator").error("Error parsing JSON object", e);
            onError.accept(e);
            return null;
        }
    }

    private final Map<String, SerializedReplayObject> serialized;
    private final Map<String, ReplayObject> objects = new HashMap<>();

    public PasteObjectsOperator(Map<String, SerializedReplayObject> serialized) {
        this.serialized = serialized;
    }


    @Override
    public boolean execute(EditorState editor) throws Exception {
        for (var entry : serialized.entrySet()) {
            ReplayScene scene = editor.getScene();

            String name = scene.makeNameUnique(entry.getKey());
            ReplayObject obj = ReplayObjects.deserialize(entry.getValue(), scene);

            scene.addObject(name, obj);
            objects.put(name, obj);
        }

        if (!objects.isEmpty()) {
            editor.getSelectedObjects().clear();
            editor.getSelectedObjects().addAll(objects.keySet());
            editor.setActiveObject(objects.keySet().stream().findFirst().orElse(null));
            return true;
        }
        return false;
    }

    @Override
    public void undo(EditorState editor) throws Exception {
        for (var name : objects.keySet()) {
            editor.getScene().removeObject(name);
        }
    }

    @Override
    public void redo(EditorState editor) throws Exception {
        for (var entry : objects.entrySet()) {
            editor.getScene().addObject(entry.getKey(), entry.getValue());
        }
    }
}
