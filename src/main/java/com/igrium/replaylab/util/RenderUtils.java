package com.igrium.replaylab.util;

import com.mojang.blaze3d.systems.RenderSystem;
import lombok.experimental.UtilityClass;

@UtilityClass
public class RenderUtils {
    public static void onRenderThread(Runnable runnable) {
        if (RenderSystem.isOnRenderThread()) {
            runnable.run();
        } else {
            RenderSystem.recordRenderCall(runnable::run);
        }
    }
}
