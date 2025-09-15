package com.igrium.replaylab.scene.obj;

import com.igrium.replaylab.scene.key.KeyChannelCategory;
import com.igrium.replaylab.scene.ReplayScene;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;

/**
 * An animation object that consists of a 3D transform
 */
public abstract class AnimationObject3D extends AnimationObject {

    protected AnimationObject3D(AnimationObjectType<?> type, ReplayScene scene) {
        super(type, scene);
    }

    protected abstract boolean hasLocation();
    protected abstract boolean hasRotation();
    protected abstract boolean hasScale();

    @Override
    public List<String> listChannelNames() {
        List<String> list = new ArrayList<>(9);
        if (hasLocation()) {
            list.add("Location X");
            list.add("Location Y");
            list.add("Location Z");
        }
        if (hasRotation()) {
            list.add("Rotation X");
            list.add("Rotation Y");
            list.add("Rotation Z");
        }
        if (hasScale()) {
            list.add("Scale X");
            list.add("Scale Y");
            list.add("Scale Z");
        }
        return list;
    }

    @Override
    public void apply(KeyChannelCategory keyframes, int timestamp) {
        Vector3d locVec;
        if (hasLocation()) {
            locVec = new Vector3d();
            locVec.x = keyframes.getChannel(0).sample(timestamp);
            locVec.y = keyframes.getChannel(1).sample(timestamp);
            locVec.z = keyframes.getChannel(2).sample(timestamp);
        } else {
            locVec = null;
        }

        Vector3d rotVec;
        if (hasRotation()) {
            int rotStart = rotationStartIndex();
            rotVec = new Vector3d();
            rotVec.x = keyframes.getChannel(rotStart).sample(timestamp);
            rotVec.y = keyframes.getChannel(rotStart + 1).sample(timestamp);
            rotVec.z = keyframes.getChannel(rotStart + 2).sample(timestamp);
        } else {
            rotVec = null;
        }

        Vector3d scaleVec;
        if (hasScale()) {
            int scaleStart = scaleStartIndex();
            scaleVec = new Vector3d();
            scaleVec.x = keyframes.getChannel(scaleStart).sample(timestamp);
            scaleVec.y = keyframes.getChannel(scaleStart + 1).sample(timestamp);
            scaleVec.z = keyframes.getChannel(scaleStart + 2).sample(timestamp);
        } else {
            scaleVec = null;
        }

        applyTransform(locVec, rotVec, scaleVec, timestamp);
    }

    /**
     * Apply the object's visual transform to the game.
     * @param location Global location of the object if present.
     * @param rotation Global rotation of the object if present.
     * @param scale Global scale of the object if present.
     * @param timestamp Current timestamp
     */
    protected abstract void applyTransform(@Nullable Vector3dc location,
                                           @Nullable Vector3dc rotation,
                                           @Nullable Vector3dc scale, int timestamp);


    /**
     * Get the index of the Rotation X channel.
     * @return Rotation X index.
     * @apiNote May be incorrect if <code>hasRotation() == false</code>
     */
    public int rotationStartIndex() {
        return hasLocation() ? 3 : 0;
    }

    /**
     * Get the index of the Scale X channel.
     * @return Scale X index.
     * @apiNote May be incorrect if <code>hasScale() == false</code>
     */
    public int scaleStartIndex() {
        if (hasRotation()) {
            return hasLocation() ? 6 : 3;
        } else {
            return hasLocation() ? 3 : 0;
        }
    }
}
