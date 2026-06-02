package com.igrium.replaylab.scene.obj;

import com.igrium.replaylab.math.Transform3;
import org.joml.Matrix4d;

public interface TransformProvider {

    /**
     * Get the final transform of this object <em>with all modifiers applied!</em>
     * @param dest Will hold the result.
     * @return <code>dest</code>
     */
    Transform3 getTransform(Transform3 dest);
}
