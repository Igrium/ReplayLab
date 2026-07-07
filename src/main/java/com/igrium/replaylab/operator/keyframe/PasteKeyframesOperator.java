package com.igrium.replaylab.operator.keyframe;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.editor.KeySelectionSet.ChannelReference;
import com.igrium.replaylab.operator.object.MultiObjectOperator;
import com.igrium.replaylab.anim.KeyChannel;
import com.igrium.replaylab.scene.obj.ReplayObject;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class PasteKeyframesOperator extends MultiObjectOperator {

    private static final TypeToken<Map<ChannelReference, JsonArray>> TYPE_TOKEN = new TypeToken<>() {};
    private static final Gson GSON = new Gson();

    public static @Nullable PasteKeyframesOperator create(String json, Consumer<? super JsonParseException> onError) {
        try {
            var serialized = GSON.fromJson(json, TYPE_TOKEN);
            return new PasteKeyframesOperator(serialized);
        } catch (JsonParseException e) {
            LoggerFactory.getLogger("ReplayLab/PasteKeyframesOperator").error("Error parsing JSON object", e);
            onError.accept(e);
            return null;
        }
    }

    private final Map<ChannelReference, JsonArray> serialized;
    private final List<String> targetObjects;


    public PasteKeyframesOperator(Map<ChannelReference, JsonArray> serialized) {
        this.serialized = ImmutableMap.copyOf(serialized);
        targetObjects = serialized.keySet().stream().map(ChannelReference::objectName).toList();
    }

    @Override
    protected Collection<? extends String> getTargetObjects(EditorState editor) {
        return targetObjects;
    }

    @Override
    protected boolean execute(EditorState editor, Map<String, ReplayObject> objects) throws Exception {

        boolean success = false;
        for (var entry : serialized.entrySet()) {
            KeyChannel chan = entry.getKey().get(objects);
            if (chan == null) continue;

            chan.pasteFromClipboard(editor.getPlayhead(), entry.getValue());
            success = true;
        }

        return true;
    }
}
