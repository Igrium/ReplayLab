package com.igrium.replaylab.mixin;

import com.replaymod.render.RenderSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = RenderSettings.class, remap = false)
public interface AccessorRenderSettings {

    @Invoker("findFFmpeg")
    static String invokeFindFFmpeg() {
        throw new AssertionError();
    }
}
