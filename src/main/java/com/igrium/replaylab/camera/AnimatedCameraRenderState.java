package com.igrium.replaylab.camera;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.render.entity.state.EntityRenderState;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

@Getter @Setter
public class AnimatedCameraRenderState extends EntityRenderState {
    private float fov;

    @Getter
    private final Quaternionf rotation = new Quaternionf();

    public void setRotation(Quaternionfc rotation) {
        this.rotation.set(rotation);
    }

    private boolean selected;
    private boolean active;
    private boolean sceneCamera;
    private float aspectRatio = 1;
}
