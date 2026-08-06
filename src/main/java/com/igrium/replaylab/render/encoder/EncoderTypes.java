package com.igrium.replaylab.render.encoder;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import com.igrium.replaylab.render.ffmpeg.FFmpegEncoder;
import net.minecraft.resources.ResourceLocation;

public class EncoderTypes {
    public static final BiMap<ResourceLocation, EncoderType<?>> REGISTRY = Maps.synchronizedBiMap(HashBiMap.create());

    public static final EncoderType<FFmpegEncoder> FFMPEG = register(new EncoderType<>(FFmpegEncoder::new),
            ResourceLocation.parse("replaylab:ffmpeg"));

    public static final EncoderType<DummyEncoder> DUMMY = register(new EncoderType<>(DummyEncoder::new),
            ResourceLocation.parse("replaylab:dummy"));

    public static final EncoderType<PNGEncoder> PNG = register(new EncoderType<>(PNGEncoder::new),
            ResourceLocation.parse("replaylab:png"));

    public static <T extends EncoderConfig> EncoderType<T> register(EncoderType<T> type, ResourceLocation id) {
        REGISTRY.put(id, type);
        return type;
    }
}
