package com.igrium.replaylab.render;

import com.replaymod.core.events.PostRenderCallback;
import com.replaymod.core.events.PreRenderCallback;
import com.replaymod.render.mixin.GameRendererAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

import java.io.Closeable;
import java.io.IOException;

/**
 * A re-implementation of Replay Mod's world renderer with less bloat
 */
public class ReplayWorldRenderer {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    public void renderWorld() {
        PreRenderCallback.EVENT.invoker().preRender();

        if (mc.world != null && mc.player != null) {
            GameRendererAccessor gameRenderer = (GameRendererAccessor) mc.gameRenderer;

            Screen orgScreen = mc.currentScreen;
            boolean orgPauseOnLostFocus = mc.options.pauseOnLostFocus;
            boolean orgRenderHand = gameRenderer.getRenderHand();

            try {
                mc.currentScreen = null;
                mc.options.pauseOnLostFocus = false;

                mc.gameRenderer.render(mc.getRenderTickCounter(), true);
            } finally {
                mc.currentScreen = orgScreen;
                mc.options.pauseOnLostFocus = orgPauseOnLostFocus;
                gameRenderer.setRenderHand(orgRenderHand);
            }
        }

        PostRenderCallback.EVENT.invoker().postRender();
    }

}
