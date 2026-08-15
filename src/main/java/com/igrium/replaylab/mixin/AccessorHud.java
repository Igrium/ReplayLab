package com.igrium.replaylab.mixin;

import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Hud.class)
public interface AccessorHud {
    @Accessor("isHidden")
    void setIsHidden(boolean isHidden);
}
