package com.igrium.replaylab.render;

import com.igrium.craftui.api.app.CraftApp.ViewportBounds;
import com.igrium.craftui.api.event.CraftUIRenderEvents;
import com.igrium.craftui.api.event.ctx.CraftUIRenderContext;
import com.igrium.replaylab.ReplayLab;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import lombok.experimental.UtilityClass;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import java.util.Optional;

/**
 * Clears the area of CraftUI's composite target outside the viewport bounds to fix hall-of-mirrors when in camera view
 */
@UtilityClass
public final class ViewportLetterbox {
    private static final Vector4fc CLEAR_COLOR = new Vector4f(0, 0, 0, 1);

    public static void clearOutside(CraftUIRenderContext ctx) {
        if (!ReplayLab.getInstance().isEditorOpen()) return;

        ViewportBounds logical = ctx.getViewportBounds();
        if (logical == null) return;

        // The composite uses physical pixels
        ViewportBounds b = logical.scaled();

        RenderTarget target = ctx.getClient().gameRenderer.mainRenderTarget();
        GpuTextureView view = target.getColorTextureView();
        if (view == null) return;

        int texW = view.getWidth(0);
        int texH = view.getHeight(0);

        int x0 = clamp(b.x(), texW);
        int y0 = clamp(b.y(), texH);
        int x1 = clamp(b.x() + b.width(), texW);
        int y1 = clamp(b.y() + b.height(), texH);

        RenderSystem.assertOnRenderThread();
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

        clearRegion(encoder, view, 0, 0, x0, texH);               // left
        clearRegion(encoder, view, x1, 0, texW - x1, texH);       // right
        clearRegion(encoder, view, x0, 0, x1 - x0, y0);           // below viewport
        clearRegion(encoder, view, x0, y1, x1 - x0, texH - y1);   // above viewport
    }

    private static void clearRegion(CommandEncoder encoder, GpuTextureView view, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        encoder.createRenderPass(() -> "ReplayLab viewport letterbox", view, Optional.of(CLEAR_COLOR), null,
                java.util.OptionalDouble.empty(), new RenderPass.RenderArea(x, y, w, h)).close();

    }

    private static int clamp(int v, int max) {
        return Math.clamp(v, 0, max);
    }
}