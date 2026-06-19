package com.igrium.replaylab.mixin;

import com.replaymod.replay.QuickReplaySender;
import com.replaymod.replay.ReplayHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ReplayHandler.class, remap = false)
public interface AccessorReplayHandler {

    @Accessor
    QuickReplaySender getQuickReplaySender();
}
