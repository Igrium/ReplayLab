package com.igrium.replaylab.operator.keyframe;

import com.igrium.replaylab.anim.KeyChannel;
import com.igrium.replaylab.anim.modifier.CurveModifier;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.editor.KeySelectionSet.ChannelReference;
import com.igrium.replaylab.operator.ReplayOperator;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class AddModifierOperator implements ReplayOperator {

    // Used for undo; curve modifier instance may have been replaced since execution
    private @Nullable CurveModifier addedModifier;

    private final ChannelReference channel;
    private final CurveModifier modifier;

    public AddModifierOperator(ChannelReference channel, CurveModifier modifier) {
        this.channel = channel;
        this.modifier = modifier;
    }

    @Override
    public boolean execute(EditorState editor)  {
        KeyChannel chan = channel.get(editor.getScene().getObjects());
        if (chan == null) return false;

        chan.getModifiers().add(modifier);
        editor.getScene().saveObject(channel.objectName());
        return true;
    }

    @Override
    public void undo(EditorState editor) {
        KeyChannel chan = Objects.requireNonNull(channel.get(editor.getScene().getObjects()));
        addedModifier = chan.getModifiers().removeLast();
        editor.getScene().saveObject(channel.objectName());
    }

    @Override
    public void redo(EditorState editor)  {
        KeyChannel chan = Objects.requireNonNull(channel.get(editor.getScene().getObjects()));
        chan.getModifiers().add(Objects.requireNonNull(addedModifier));
        editor.getScene().saveObject(channel.objectName());
    }
}
