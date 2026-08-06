package com.igrium.replaylab.mixin;

import com.igrium.replaylab.render.RenderUtils;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Minecraft.class)
public class MixinMinecraft {

    // Fix https://github.com/Igrium/CraftUI/issues/5
    @WrapMethod(method = "updateScreenAndTick")
    void wrapReset(Screen resettingScreen, Operation<Void> original) {
        RenderUtils.forceNoCraftUI = true;
        try {
            original.call(resettingScreen);
        } finally {
            RenderUtils.forceNoCraftUI = false;
        }
    }

}
