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
    private transient final EncoderType<?> type;

    protected EncoderConfig(EncoderType<?> type) {
        this.type = type;
    }

    public abstract void readJson(JsonObject json, JsonDeserializationContext context);

    public abstract JsonObject writeJson(JsonSerializationContext context);

    public abstract EncoderProcess spawnEncoder();

    /**
     * Get the extension(s) the file should use given the encoder's current config.
     * @return The extensions, excluding "."; An empty array if the encoder doesn't care.
     */
    public String[] getSupportedExtensions() {
        return new String[0];
    }

    /**
     * If <code>true</code>, this encoder wants to output multiple files to a folder rather than a single video file
     */
    public boolean wantsDirectory() {
        return false;
    }

    public void drawProperties(EditorState editor) {
        ImGui.text(Language.getInstance().get("gui.replaylab.encoder.noProps"));
    }
}
