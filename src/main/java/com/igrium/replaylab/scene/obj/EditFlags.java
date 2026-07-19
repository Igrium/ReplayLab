package com.igrium.replaylab.scene.obj;

import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.operator.object.CommitObjectUpdateOperator;
import lombok.experimental.UtilityClass;

/**
 * Flags for how the system should handle an object after calls to
 * <code>drawPropertiesPanel</code> and <code>drawGizmos</code>
 */
@UtilityClass
public final class EditFlags {
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

    public static void handleUpdate(EditorState editor, ReplayObject object, int state) {
        if (hasFlag(state, UPDATE_SCENE)) {
            editor.applyToGame(hasFlag(state, EditFlags.RESAMPLE) ? o -> true : o -> o != object);
        }
        if (hasFlag(state, EditFlags.CREATE_UNDO_STEP)) {
            editor.applyOperator(new CommitObjectUpdateOperator(false, object.getId()), false);
        }
    }

    private static boolean hasFlag(int flags, int flag) {
        return (flags & flag) != 0;
    }
}
