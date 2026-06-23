package com.igrium.replaylab.mixin;

import com.igrium.replaylab.util.RenderUtils;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(MinecraftClient.class)
public class MixinMinecraftClient {

    // Fix https://github.com/Igrium/CraftUI/issues/5
    @WrapMethod(method = "reset")
    void wrapReset(Screen resettingScreen, Operation<Void> original) {
        RenderUtils.forceNoCraftUI = true;
        try {
            original.call(resettingScreen);
        } finally {
            RenderUtils.forceNoCraftUI = false;
        }
    }

}
