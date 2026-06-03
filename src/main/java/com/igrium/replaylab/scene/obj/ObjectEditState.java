package com.igrium.replaylab.scene.obj;

import lombok.experimental.UtilityClass;

/**
 * Flags for how the system should handle an object after calls to
 * <code>drawPropertiesPanel</code> and <code>drawGizmos</code>
 */
@UtilityClass
public final class ObjectEditState {
    public static final int NONE = 0;

    /**
     * Call applyToScene with the new object properties
     */
    public static final int UPDATE_SCENE = 1;

    /**
     * Add an undo step with this object's new values
     */
    public static final int CREATE_UNDO_STEP = 2;

    /**
     * Re-sample this object's channels
     */
    public static final int RESAMPLE = 4;


    public static final int COMMIT = UPDATE_SCENE | CREATE_UNDO_STEP;
}
