package com.igrium.replaylab.camera;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
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
        VertexRendering.drawBox(matrices, vertexConsumers.getBuffer(RenderLayer.getLines()), new Box(-2, -2, -2, 2, 2, 2), 1, 1, 1, 1);
//        matrices.multiply(rot);

        VertexConsumer lines = vertexConsumers.getBuffer(RenderLayer.LINES);
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f posMat = entry.getPositionMatrix();
        Matrix3f normMat = entry.getNormalMatrix();

        float height = computeCamHeight(Math.toRadians(state.getFov()));
        float halfHeight = height / 2;

        drawLine(lines, entry,
                -halfHeight, -halfHeight, 1,
                halfHeight, -halfHeight, 1,
                1, 1, 1);

        drawLine(lines, entry,
                halfHeight, -halfHeight, 1,
                halfHeight, halfHeight, 1,
                1, 1, 1);

        drawLine(lines, entry,
                halfHeight, halfHeight, 1,
                -halfHeight, halfHeight, 1,
                1, 1, 1);

        drawLine(lines, entry,
                -halfHeight, halfHeight, 1,
                -halfHeight, -halfHeight, 1,
                1, 1, 1);

        matrices.pop();
//        matrices.multiply(new Quaternionf().rotateY);
    }

    private static final Vector3f normCache = new Vector3f();

    private static void drawLine(VertexConsumer lines,
                                 MatrixStack.Entry entry,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 float r, float g, float b) {
        normCache.set(x2, y2, z2).sub(x1, y1, z1).normalize();

        lines.vertex(entry, x1, y1, z1)
                .color(r, g, b, 1)
                .normal(normCache.x, normCache.y, normCache.z);

        lines.vertex(entry, x2, y2, z2)
                .color(r, g, b, 1)
                .normal(normCache.x, normCache.y, normCache.z);
//        lines.vertex(posMat, x1, y1, z1)
//                .color(r, g, b, 1)
//                .normal(normMat, normCache.x, normCache.y, normCache.z);
    }
    private float computeCamHeight(float fovRad) {
        return 2 * Math.tan(fovRad / 2);
    }
}
