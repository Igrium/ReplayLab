package com.igrium.replaylab.mixin;

import com.igrium.replaylab.game.LanguageReloadEvent;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.resource.language.LanguageManager;
import net.minecraft.client.resource.language.TranslationStorage;
import net.minecraft.resource.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LanguageManager.class)
public class MixinLanguageManager {

    @Inject(method = "reload", at = @At("RETURN"))
    void onReload(ResourceManager resourceManager, CallbackInfo ci, @Local TranslationStorage translationStorage) {
        LanguageReloadEvent.EVENT.invoker().onReloadLanguage(translationStorage);
    }
}
