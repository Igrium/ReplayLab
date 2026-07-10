package com.igrium.replaylab.operator.keyframe;

import com.igrium.replaylab.anim.KeyChannel;
import com.igrium.replaylab.anim.modifier.CurveModifier;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.editor.KeySelectionSet.ChannelReference;
import com.igrium.replaylab.operator.ReplayOperator;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class RemoveModifierOperator implements ReplayOperator {
    private final ChannelReference channel;
    private final int index;

    private @Nullable CurveModifier mod;

    public RemoveModifierOperator(ChannelReference channel, int index) {
        this.channel = channel;
        this.index = index;
    }

    @Override
    public boolean execute(EditorState editor) throws Exception {
        KeyChannel chan = channel.get(editor.getScene().getObjects());
        if (chan == null) return false;
        if (index < 0 || index >= chan.getModifiers().size()) return false;

        mod = chan.getModifiers().remove(index);
        editor.getScene().saveObject(channel.objectName());
        return true;
    }

    @Override
    public void undo(EditorState editor) {
        KeyChannel chan = Objects.requireNonNull(channel.get(editor.getScene().getObjects()));
        chan.getModifiers().add(index, mod);
        editor.getScene().saveObject(channel.objectName());
    }

    @Override
    public void redo(EditorState editor)  {
        KeyChannel chan = Objects.requireNonNull(channel.get(editor.getScene().getObjects()));
        mod = chan.getModifiers().remove(index);
        editor.getScene().saveObject(channel.objectName());
    }
}
