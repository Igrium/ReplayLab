package com.igrium.replaylab.mixin;

import com.replaymod.replay.gui.screen.GuiReplayViewer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = GuiReplayViewer.GuiReplayEntry.class, remap = false)
public interface AccessorGuiReplayEntry {

    @Accessor("incompatible")
    boolean isIncompatible();
}
