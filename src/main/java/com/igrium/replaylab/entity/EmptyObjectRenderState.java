package com.igrium.replaylab.entity;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public class EmptyObjectRenderState extends EntityRenderState {
    @Getter
    private final Quaternionf rotation = new Quaternionf();

    @Getter
    private final Vector3f scale = new Vector3f(1);

    public void setRotation(Quaternionfc rotation) {
        this.rotation.set(rotation);
    }

    public void setScale(Vector3fc scale) {
        this.scale.set(scale);
    }

    @Getter @Setter
    private boolean selected;

    @Getter @Setter
    private boolean active;
}
