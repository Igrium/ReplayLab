package com.igrium.replaylab.camera;

import com.igrium.replaylab.ui.GizmoColors;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import org.joml.*;
import org.joml.Math;

public class AnimatedCameraRenderer extends EntityRenderer<AnimatedCameraEntity, AnimatedCameraRenderState> {

    private static final float LINE_WIDTH = 1.5f;

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

        state.setPitch(entity.getPitch());
        state.setYaw(entity.getYaw());
        state.setRoll(entity.getRoll());

        state.setSelected(entity.isSelected());
        state.setActive(entity.isActive());
    }

    @SuppressWarnings("SuspiciousNameCombination")
    @Override
    public void render(AnimatedCameraRenderState state, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        super.render(state, matrices, vertexConsumers, light);

        matrices.push();

        Quaternionf rot = new Quaternionf().rotationYXZ(
                Math.toRadians(-state.getYaw()),
                Math.toRadians(state.getPitch()),
                Math.toRadians(state.getRoll())
        );
        matrices.multiply(rot);

        VertexConsumer lines = vertexConsumers.getBuffer(RenderLayer.LINES);

//        float height = computeCamHeight(Math.toRadians(state.getFov()));
        float height = 1;
        float halfHeight = height / 2;

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
                -halfHeight, -halfHeight, 1,
                halfHeight, -halfHeight, 1, color);

        drawLine(matrices, lines,
                halfHeight, -halfHeight, 1,
                halfHeight, halfHeight, 1, color);

        drawLine(matrices, lines,
                halfHeight, halfHeight, 1,
                -halfHeight, halfHeight, 1, color);

        drawLine(matrices, lines,
                -halfHeight, halfHeight, 1,
                -halfHeight, -halfHeight, 1, color);

        // Frustum
        drawLine(matrices, lines,
                0, 0, 0,
                -halfHeight, -halfHeight, 1, color);

        drawLine(matrices, lines,
                0, 0, 0,
                -halfHeight, halfHeight, 1, color);

        drawLine(matrices, lines,
                0, 0, 0,
                halfHeight, -halfHeight, 1, color);

        drawLine(matrices, lines,
                0, 0, 0,
                halfHeight, halfHeight, 1, color);

        matrices.pop();
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
