package com.igrium.replaylab.operator;

import com.igrium.replaylab.scene.ReplayScene;

/**
 * An operator that can be applied, undone, and redone.
 */
public interface ReplayOperator {
    /**
     * Apply the operator for the first time.
     *
     * @param scene Scene to use
     * @return <code>true</code> if the operation was successful and should be added to the undo stack.</true>
     * @throws Exception If the operation throws an exception.
     */
    boolean execute(ReplayScene scene) throws Exception;

    /**
     * Undo the operator. Expects the scene is in an identical state to when the operator initially finished.
     *
     * @param scene Scene to use
     * @throws Exception If the undo operation fails.
     */
    void undo(ReplayScene scene) throws Exception;

    /**
     * Redo the operator. Expects the scene is in an identical state to when the operator was initially run.
     *
     * @param scene Scene to use
     * @throws Exception If the redo operation fails.
     */
    void redo(ReplayScene scene) throws Exception;
}
