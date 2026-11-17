package com.igrium.replaylab.mixin;

import com.replaymod.replay.FullReplaySender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FullReplaySender.class)
public interface AccessorFullReplaySender {
    @Accessor("actualID")
    int getActualID();
}
