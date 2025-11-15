package com.igrium.replaylab.operator;

import com.igrium.replaylab.editor.EditorState;

/**
 * An operator that can be applied, undone, and redone.
 */
public interface ReplayOperator {
    /**
     * Apply the operator for the first time.
     *
     * @param editor Editor to use
     * @return <code>true</code> if the operation was successful and should be added to the undo stack.</true>
     * @throws Exception If the operation throws an exception.
     * @apiNote If the execution returns <code>false</code>, the scene should not be modified.
     * Otherwise, the undo/redo stack may be corrupted.
     */
    boolean execute(EditorState editor) throws Exception;

    /**
     * Undo the operator. Expects the scene is in an identical state to when the operator initially finished.
     *
     * @param editor Editor to use
     * @throws Exception If the undo operation fails.
     */
    void undo(EditorState editor) throws Exception;

    /**
     * Redo the operator. Expects the scene is in an identical state to when the operator was initially run.
     *
     * @param editor Editor to use
     * @throws Exception If the redo operation fails.
     */
    void redo(EditorState editor) throws Exception;
}
