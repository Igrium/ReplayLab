package com.igrium.replaylab.render2.encoder;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.render2.ManagedNativeImage;
import com.igrium.replaylab.render2.RenderMetadata;
import imgui.ImGui;
import imgui.type.ImString;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.CompletableFuture;

/**
 * An encoder that simply consumes frames
 */
public class DummyEncoder extends EncoderConfig {
    protected DummyEncoder(EncoderType<?> type) {
        super(type);
    }

    @Getter @Setter
    private String dummyValue = "";

    @Override
    public void readJson(JsonObject json, JsonDeserializationContext context) {
        json.addProperty("dummyValue", dummyValue);
    }

    @Override
    public JsonObject writeJson(JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("dummyValue", dummyValue);
        return jsonObject;
    }

    @Override
    public EncoderProcess spawnEncoder() {
        return new DummyEncoderProcess();
    }

    private final ImString strRef = new ImString(64);

    @Override
    public void drawProperties(EditorState editor) {
        strRef.set(dummyValue);
        if (ImGui.inputText("Dummy Value", strRef)) {
            setDummyValue(strRef.get());
        }
    }

    public static class DummyEncoderProcess extends EncoderProcess {

        @Override
        protected void startEncoding(RenderMetadata metadata) throws Exception {

        }

        @Override
        protected void encodeFrame(ManagedNativeImage frame, int frameIdx) throws Exception {

        }

        @Override
        protected CompletableFuture<?> finishEncoding() throws Exception {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        protected void onFailed(Throwable reason) {

        }
    }
}
