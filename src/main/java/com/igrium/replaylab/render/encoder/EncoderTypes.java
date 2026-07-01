package com.igrium.replaylab.render.encoder;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import com.igrium.replaylab.render.ffmpeg.FFmpegEncoder;
import net.minecraft.util.Identifier;

public class EncoderTypes {
    public static final BiMap<Identifier, EncoderType<?>> REGISTRY = Maps.synchronizedBiMap(HashBiMap.create());

    public static final EncoderType<FFmpegEncoder> FFMPEG = register(new EncoderType<>(FFmpegEncoder::new),
            Identifier.of("replaylab:ffmpeg"));

    public static final EncoderType<DummyEncoder> DUMMY = register(new EncoderType<>(DummyEncoder::new),
            Identifier.of("replaylab:dummy"));

    public static final EncoderType<PNGEncoder> PNG = register(new EncoderType<>(PNGEncoder::new),
            Identifier.of("replaylab:png"));

    public static <T extends EncoderConfig> EncoderType<T> register(EncoderType<T> type, Identifier id) {
        REGISTRY.put(id, type);
        return type;
    }
}
