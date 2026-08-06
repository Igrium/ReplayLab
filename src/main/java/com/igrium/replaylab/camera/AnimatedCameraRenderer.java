package com.igrium.replaylab.camera;

import com.igrium.replaylab.ui.gizmos.GizmoColors;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.util.ARGB;
import org.joml.*;
import org.joml.Math;

public class AnimatedCameraRenderer extends EntityRenderer<AnimatedCameraEntity, AnimatedCameraRenderState> {

    private static final Vector3f NORMAL = new Vector3f(0, 0, 1);

    public AnimatedCameraRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public AnimatedCameraRenderState createRenderState() {
        return new AnimatedCameraRenderState();
    }

    @Override
    public void extractRenderState(AnimatedCameraEntity entity, AnimatedCameraRenderState state, float tickDelta) {
        super.extractRenderState(entity, state, tickDelta);
        state.setFov(entity.getFov());

        state.setRotation(entity.getRotationQuat());

        state.setSelected(entity.isSelected());
        state.setActive(entity.isActive());
        state.setSceneCamera(entity.isSceneCamera());
        state.setAspectRatio(entity.getAspectRatio());
    }
    @Override
    public void submit(AnimatedCameraRenderState state, PoseStack matrices, SubmitNodeCollector submitNodeCollector,
                       CameraRenderState camera) {
        super.submit(state, matrices, submitNodeCollector, camera);

        matrices.pushPose();
        matrices.mulPose(state.getRotation());

        float width = Math.min(1.0f, state.getAspectRatio());
        float height = width / state.getAspectRatio();

        float halfWidth = width / 2.0f;
        float halfHeight = height / 2.0f;

        float depth = halfHeight / Math.tan(Math.toRadians(state.getFov()) / 2f);

        final int color;
        if (state.isActive()) {
            color = GizmoColors.ACTIVE;
        } else if (state.isSelected()) {
            color = GizmoColors.SELECTED;
        } else {
            color = GizmoColors.DEFAULT;
        }

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

        boolean sceneCamera = state.isSceneCamera();

        // 26.2 defers geometry: we hand over a callback that is invoked with a vertex consumer
        // once the render pass for this render type actually runs.
        submitNodeCollector.submitCustomGeometry(matrices, RenderTypes.LINES, (entry, lines) -> {
            // Quad
            drawLine(entry, lines,
                    -halfWidth, -halfHeight, depth,
                    halfWidth, -halfHeight, depth, color);

            drawLine(entry, lines,
                    halfWidth, -halfHeight, depth,
                    halfWidth, halfHeight, depth, color);

            drawLine(entry, lines,
                    halfWidth, halfHeight, depth,
                    -halfWidth, halfHeight, depth, color);

            drawLine(entry, lines,
                    -halfWidth, halfHeight, depth,
                    -halfWidth, -halfHeight, depth, color);

            // Frustum
            drawLine(entry, lines,
                    0, 0, 0,
                    -halfWidth, -halfHeight, depth, color);

            drawLine(entry, lines,
                    0, 0, 0,
                    -halfWidth, halfHeight, depth, color);

            drawLine(entry, lines,
                    0, 0, 0,
                    halfWidth, -halfHeight, depth, color);

            drawLine(entry, lines,
                    0, 0, 0,
                    halfWidth, halfHeight, depth, color);

            if (!sceneCamera) {
                drawLine(entry, lines, p1x, p1y, depth, p2x, p2y, depth, color);
                drawLine(entry, lines, p2x, p2y, depth, p3x, p3y, depth, color);
                drawLine(entry, lines, p3x, p3y, depth, p1x, p1y, depth, color);
            }
        });

        if (sceneCamera) {
            // Triangle doesn't play well with alpha
            int solidColor = ARGB.color(255, color);

            submitNodeCollector.submitCustomGeometry(matrices, RenderTypes.debugFilledBox(), (entry, solid) -> {
                tri(depth, solidColor, p1x, p1y, p2x, p2y, p3x, p3y, solid, entry);
                tri(depth, solidColor, p3x, p3y, p2x, p2y, p1x, p1y, solid, entry);
            });
        }

        matrices.popPose();
    }

    private static void tri(float depth, int color, float p1x, float p1y, float p2x, float p2y, float p3x, float p3y,
                            VertexConsumer solid, PoseStack.Pose entry) {
        solid.addVertex(entry, p1x, p1y, depth).setColor(color).setUv(0f, 0f).setLight(15).setNormal(entry, NORMAL);
        solid.addVertex(entry, p2x, p2y, depth).setColor(color).setUv(0f, 0f).setLight(15).setNormal(entry, NORMAL);
        solid.addVertex(entry, p3x, p3y, depth).setColor(color).setUv(0f, 0f).setLight(15).setNormal(entry, NORMAL);
        solid.addVertex(entry, p3x, p3y, depth).setColor(color).setUv(0f, 0f).setLight(15).setNormal(entry, NORMAL);
    }

    private static void drawLine(PoseStack.Pose entry, VertexConsumer vertexConsumer,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 int color) {
        Vector3f normal = new Vector3f(x2 - x1, y2 - y1, z2 - z1).normalize();
        vertexConsumer.addVertex(entry, x1, y1, z1).setColor(color).setNormal(entry, normal);
        vertexConsumer.addVertex(entry, x2, y2, z2).setColor(color).setNormal(entry, normal);
    }

    private float computeCamHeight(float fovRad) {
        return 2 * Math.tan(fovRad / 2);
    }
}
