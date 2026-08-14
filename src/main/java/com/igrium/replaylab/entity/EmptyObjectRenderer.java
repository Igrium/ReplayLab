package com.igrium.replaylab.entity;

import com.igrium.replaylab.ui.gizmos.GizmoColors;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Vector3f;

public class EmptyObjectRenderer extends EntityRenderer<EmptyObjectEntity, EmptyObjectRenderState> {

    private static final float ARM_LENGTH = 0.5f;
    private static final float LINE_WIDTH = 2.5f;

    public EmptyObjectRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public EmptyObjectRenderState createRenderState() {
        return new EmptyObjectRenderState();
    }

    @Override
    public void extractRenderState(EmptyObjectEntity entity, EmptyObjectRenderState state, float tickDelta) {
        super.extractRenderState(entity, state, tickDelta);
        state.setRotation(entity.getRotation());
        state.setScale(entity.getScale());
        state.setSelected(entity.isSelected());
        state.setActive(entity.isActive());
    }

    @Override
    public void submit(EmptyObjectRenderState state, PoseStack matrices, SubmitNodeCollector submitNodeCollector,
                       CameraRenderState camera) {
        super.submit(state, matrices, submitNodeCollector, camera);

        matrices.pushPose();
        matrices.mulPose(state.getRotation());
        matrices.scale(state.getScale().x(), state.getScale().y(), state.getScale().z());

        final int color;
        if (state.isActive()) {
            color = GizmoColors.ACTIVE;
        } else if (state.isSelected()) {
            color = GizmoColors.SELECTED;
        } else {
            color = GizmoColors.DEFAULT;
        }

        submitNodeCollector.submitCustomGeometry(matrices, RenderTypes.LINES, (entry, lines) -> {
            drawLine(entry, lines, -ARM_LENGTH, 0, 0, ARM_LENGTH, 0, 0, color);
            drawLine(entry, lines, 0, -ARM_LENGTH, 0, 0, ARM_LENGTH, 0, color);
            drawLine(entry, lines, 0, 0, -ARM_LENGTH, 0, 0, ARM_LENGTH, color);
        });

        matrices.popPose();
    }

    private static void drawLine(PoseStack.Pose entry, VertexConsumer vertexConsumer,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 int color) {
        Vector3f normal = new Vector3f(x2 - x1, y2 - y1, z2 - z1).normalize();
        vertexConsumer.addVertex(entry, x1, y1, z1).setColor(color).setNormal(entry, normal).setLineWidth(LINE_WIDTH);
        vertexConsumer.addVertex(entry, x2, y2, z2).setColor(color).setNormal(entry, normal).setLineWidth(LINE_WIDTH);
    }
}
