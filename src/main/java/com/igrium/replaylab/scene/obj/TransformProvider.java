package com.igrium.replaylab.scene.obj;

import org.joml.Matrix4d;
import org.joml.Vector3d;

public interface TransformProvider {
    /**
     * Compute the final transform of this object, including all constraints and parents.
     * @param dest Destination matrix.
     * @apiNote Applies on top of any existing transform in <code>dest</code>.
     */
    void getCombinedTransform(Matrix4d dest);
}
