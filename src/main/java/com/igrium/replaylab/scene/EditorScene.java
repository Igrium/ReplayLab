package com.igrium.replaylab.scene;

import com.igrium.replaylab.operator.ApplyKeyManifestOperator;
import com.igrium.replaylab.operator.UndoStep;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Keeps track of all the runtime stuff regarding a scene.
 */
public class EditorScene {
    /**
     * The keyframe manifest that gets saved into the file, used in undo/redo, etc.
     * <b>CONSIDERED EFFECTIVELY IMMUTABLE</b>
     */
    @Getter @Setter @NonNull
    private KeyframeManifest internalKeyManifest = new KeyframeManifest();

    /**
     * The version of the keyframe manifest that gets edited, displayed, etc.
     * before getting saved to the undo stack.
     */
    @Getter @Setter @NonNull
    private KeyframeManifest keyManifest = new KeyframeManifest();

    private final Deque<UndoStep> undoStack = new ArrayDeque<>();
    private final Deque<UndoStep> redoStack = new ArrayDeque<>();

    /**
     * The global start position of the rendered scene in ticks.
     */
    @Getter
    private double startTick;

    public void setStartTick(double startTick) {
        if (startTick < 0) {
            throw new IllegalArgumentException("Start tick may not be negative.");
        }
        this.startTick = startTick;
    }

    /**
     * The length of the scene in ticks.
     */
    @Getter
    private double length;

    public void setLength(double length) {
        if (length < 0) {
            throw new IllegalArgumentException("Length may not be negative.");
        }
        this.length = length;
    }

    /**
     * Restore the working keyframe manifest to a copy of the internal one
     */
    public void resetKeyManifest() {
        keyManifest = internalKeyManifest.copy();
    }

    /**
     * Apply the working keyframe manifest into the core keyframe manifest,
     * creating an undo step in the process.
     */
    public void commitKeyframeUpdates() {
        KeyframeManifest updated = keyManifest.copy();
        ApplyKeyManifestOperator op = new ApplyKeyManifestOperator(internalKeyManifest, updated);
        setInternalKeyManifest(updated);
        pushUndoStep(op);
    }

    /**
     * Push an undo step onto the stack, clearing the redo stack in the process.
     * @param step Step to push.
     * @apiNote It is expected that the actual action the step undoes has already completed.
     */
    public void pushUndoStep(UndoStep step) {
        redoStack.clear();
        undoStack.push(step);
    }

    /**
     * Attempt to undo the last operation.
     * @return If there was an operation to undo.
     */
    public boolean undo() {
        if (!undoStack.isEmpty()) {
            UndoStep step = undoStack.pop();
            step.undo(this);
            redoStack.push(step);
            return true;
        }
        return false;
    }

    /**
     * Attempt to redo the last undone operation.
     * @return If there was an operation to redo.
     */
    public boolean redo() {
        if (!redoStack.isEmpty()) {
            UndoStep step = redoStack.pop();
            step.redo(this);
            undoStack.push(step);
            return true;
        }
        return false;
    }


    public EditorScene() {
        // Init temporary values
        KeyChannelCategory cat1 = new KeyChannelCategory("Category 1");

        KeyChannel ch1 = new KeyChannel("Channel 1");
        ch1.getKeys().add(new Keyframe(0, 0));
        ch1.getKeys().add(new Keyframe(10, 0));
        cat1.getChannels().add(ch1);

        KeyChannel ch2 = new KeyChannel("Channel 2");
        ch2.getKeys().add(new Keyframe(10, 0));
        ch2.getKeys().add(new Keyframe(5, 2));
        cat1.getChannels().add(ch2);

        getInternalKeyManifest().getCategories().add(cat1);

        KeyChannelCategory cat2 = new KeyChannelCategory("This category has a very long name");

        KeyChannel ch3 = new KeyChannel("Channel 3");
        ch3.getKeys().add(new Keyframe(0, 20));
        cat2.getChannels().add(ch3);

        getInternalKeyManifest().getCategories().add(cat2);

        resetKeyManifest();
    }
}
