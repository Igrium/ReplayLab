package com.igrium.replaylab.mixin;

import com.replaymod.replay.QuickReplaySender;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replaystudio.data.Marker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

@Mixin(value = ReplayHandler.class, remap = false)
public interface AccessorReplayHandler {

    @Accessor
    QuickReplaySender getQuickReplaySender();

    @Accessor
    Set<Marker> getMarkers();
}
