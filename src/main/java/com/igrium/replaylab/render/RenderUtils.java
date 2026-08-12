package com.igrium.replaylab.render;

import com.mojang.blaze3d.systems.RenderSystem;
import lombok.experimental.UtilityClass;
import net.minecraft.client.Minecraft;

@UtilityClass
public class RenderUtils {

    public static void onRenderThread(Runnable runnable) {
        if (RenderSystem.isOnRenderThread()) {
            runnable.run();
        } else {
            // 26.2 removed RenderSystem.recordRenderCall; the client's task queue is drained on the render thread.
            Minecraft.getInstance().execute(runnable);
        }
    }
}
