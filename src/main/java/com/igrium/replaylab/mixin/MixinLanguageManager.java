package com.igrium.replaylab.mixin;

import com.igrium.replaylab.LanguageReloadEvent;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LanguageManager.class)
public class MixinLanguageManager {

    @Inject(method = "onResourceManagerReload", at = @At("RETURN"))
    void onReload(ResourceManager resourceManager, CallbackInfo ci, @Local ClientLanguage translationStorage) {
        LanguageReloadEvent.EVENT.invoker().onReloadLanguage(translationStorage);
    }
}
