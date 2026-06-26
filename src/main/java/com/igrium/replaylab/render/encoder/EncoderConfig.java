package com.igrium.replaylab.render.encoder;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.replaylab.editor.EditorState;
import imgui.ImGui;
import lombok.Getter;
import net.minecraft.util.Language;

/**
 * Stores and persists the configuration for an {@link EncoderConfig}.
 *
 * <p>Each {@link EncoderType} has a corresponding {@code EncoderConfig} subclass
 * that captures its specific settings. Configs are responsible for serializing
 * those settings to JSON and spawning a configured {@link EncoderProcess} instance.
 */
public abstract class EncoderConfig {

    @Getter
    private final EncoderType<?> type;

    protected EncoderConfig(EncoderType<?> type) {
        this.type = type;
    }

    public abstract void readJson(JsonObject json, JsonDeserializationContext context);

    public abstract JsonObject writeJson(JsonSerializationContext context);

    public abstract EncoderProcess spawnEncoder();

    public void drawProperties(EditorState editor) {
        ImGui.text(Language.getInstance().get("gui.replaylab.encoder.noProps"));
    }
}
