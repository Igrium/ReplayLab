package com.igrium.replaylab.camera;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.render.entity.state.EntityRenderState;

@Getter @Setter
public class AnimatedCameraRenderState extends EntityRenderState {
    private float fov;

    private float pitch;
    private float yaw;
    private float roll;
}
