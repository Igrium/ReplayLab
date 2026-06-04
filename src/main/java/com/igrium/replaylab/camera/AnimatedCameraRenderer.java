package com.igrium.replaylab.camera;

import com.igrium.replaylab.ui.gizmos.GizmoColors;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.ColorHelper;
import org.joml.*;
import org.joml.Math;

public class AnimatedCameraRenderer extends EntityRenderer<AnimatedCameraEntity, AnimatedCameraRenderState> {

    private static final Vector3f NORMAL = new Vector3f(0, 0, 1);

    public AnimatedCameraRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public AnimatedCameraRenderState createRenderState() {
        return new AnimatedCameraRenderState();
    }

    @Override
    public void updateRenderState(AnimatedCameraEntity entity, AnimatedCameraRenderState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
        state.setFov(entity.getFov());

        state.setRotation(entity.getRotationQuat());

        state.setSelected(entity.isSelected());
        state.setActive(entity.isActive());
        state.setSceneCamera(entity.isSceneCamera());
        state.setAspectRatio(entity.getAspectRatio());
    }
    @Override
    public void render(AnimatedCameraRenderState state, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        super.render(state, matrices, vertexConsumers, light);

        matrices.push();
        matrices.multiply(state.getRotation());

        VertexConsumer lines = vertexConsumers.getBuffer(RenderLayer.LINES);

        float width = Math.min(1.0f, state.getAspectRatio());
        float height = width / state.getAspectRatio();

        float halfWidth = width / 2.0f;
        float halfHeight = height / 2.0f;

        float depth = halfHeight / Math.tan(Math.toRadians(state.getFov()) / 2f);

        int color;
        if (state.isActive()) {
            color = GizmoColors.ACTIVE;
        } else if (state.isSelected()) {
            color = GizmoColors.SELECTED;
        } else {
            color = GizmoColors.DEFAULT;
        }

        // Quad
        drawLine(matrices, lines,
                -halfWidth, -halfHeight, depth,
                halfWidth, -halfHeight, depth, color);

        drawLine(matrices, lines,
                halfWidth, -halfHeight, depth,
                halfWidth, halfHeight, depth, color);

        drawLine(matrices, lines,
                halfWidth, halfHeight, depth,
                -halfWidth, halfHeight, depth, color);

        drawLine(matrices, lines,
                -halfWidth, halfHeight, depth,
                -halfWidth, -halfHeight, depth, color);

        // Frustum
        drawLine(matrices, lines,
                0, 0, 0,
                -halfWidth, -halfHeight, depth, color);

        drawLine(matrices, lines,
                0, 0, 0,
                -halfWidth, halfHeight, depth, color);

        drawLine(matrices, lines,
                0, 0, 0,
                halfWidth, -halfHeight, depth, color);

        drawLine(matrices, lines,
                0, 0, 0,
                halfWidth, halfHeight, depth, color);


        // Indicator Triangle
        float triBottom = halfHeight + 0.05f;
        float triHeight = Math.min(0.5f, width * .6f);
        float triTop = halfHeight + triHeight;

        float p1x = -halfWidth;
        float p1y = triBottom;

        float p2x = halfWidth;
        float p2y = triBottom;

        float p3x = 0;
        float p3y = triTop;


        if (state.isSceneCamera()) {
            // Triangle doesn't play well with alpha
            color = ColorHelper.withAlpha(255, color);

            VertexConsumer solid = vertexConsumers.getBuffer(RenderLayer.getDebugFilledBox());
            MatrixStack.Entry entry = matrices.peek();

            tri(depth, color, p1x, p1y, p2x, p2y, p3x, p3y, solid, entry);
            tri(depth, color, p3x, p3y, p2x, p2y, p1x, p1y, solid, entry);

        } else {
            drawLine(matrices, lines, p1x, p1y, depth, p2x, p2y, depth, color);
            drawLine(matrices, lines, p2x, p2y, depth, p3x, p3y, depth, color);
            drawLine(matrices, lines, p3x, p3y, depth, p1x, p1y, depth, color);
        }


        matrices.pop();
    }

    private static void tri(float depth, int color, float p1x, float p1y, float p2x, float p2y, float p3x, float p3y,
                            VertexConsumer solid, MatrixStack.Entry entry) {
        solid.vertex(entry, p1x, p1y, depth).color(color).texture(0f, 0f).light(15).normal(entry, NORMAL);
        solid.vertex(entry, p2x, p2y, depth).color(color).texture(0f, 0f).light(15).normal(entry, NORMAL);
        solid.vertex(entry, p3x, p3y, depth).color(color).texture(0f, 0f).light(15).normal(entry, NORMAL);
        solid.vertex(entry, p3x, p3y, depth).color(color).texture(0f, 0f).light(15).normal(entry, NORMAL);
    }

    private static void drawLine(MatrixStack matrices, VertexConsumer vertexConsumer,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 int color) {
        MatrixStack.Entry entry = matrices.peek();
        Vector3f normal = new Vector3f(x2 - x1, y2 - y1, z2 - z1).normalize();
        vertexConsumer.vertex(entry, x1, y1, z1).color(color).normal(entry, normal);
        vertexConsumer.vertex(entry, x2, y2, z2).color(color).normal(entry, normal);
    }

    private float computeCamHeight(float fovRad) {
        return 2 * Math.tan(fovRad / 2);
    }
}
