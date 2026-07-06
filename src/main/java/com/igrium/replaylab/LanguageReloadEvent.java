package com.igrium.replaylab;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.util.Language;

/**
 * Called when the language file is switched or reloaded
 */
public interface LanguageReloadEvent {
    void onReloadLanguage(Language translationStorage);

    Event<LanguageReloadEvent> EVENT = EventFactory.createArrayBacked(LanguageReloadEvent.class,
            listeners -> translationStorage -> {
        for (var listener : listeners) {
            listener.onReloadLanguage(translationStorage);
        }
    });
}
