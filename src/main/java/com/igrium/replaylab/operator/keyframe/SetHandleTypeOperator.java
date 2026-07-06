package com.igrium.replaylab.operator.keyframe;

import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.editor.KeySelectionSet;
import com.igrium.replaylab.editor.KeySelectionSet.ChannelReference;
import com.igrium.replaylab.editor.KeySelectionSet.KeyHandleReference;
import com.igrium.replaylab.operator.object.MultiObjectOperator;
import com.igrium.replaylab.scene.key.ChannelUtils;
import com.igrium.replaylab.scene.key.KeyChannel;
import com.igrium.replaylab.scene.key.Keyframe;
import com.igrium.replaylab.scene.key.Keyframe.HandleType;
import com.igrium.replaylab.scene.obj.ReplayObject;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.util.Collection;
import java.util.Map;

public class SetHandleTypeOperator extends MultiObjectOperator {

    private final KeySelectionSet selection;
    private final HandleType newType;

    public SetHandleTypeOperator(HandleType newType, KeySelectionSet selection) {
        this.selection = selection;
        this.newType = newType;
    }

    public SetHandleTypeOperator(HandleType newType, KeyHandleReference... selection) {
        this(newType, new KeySelectionSet());
        for (var ref : selection) {
            this.selection.selectHandle(ref);
        }
    }

    public SetHandleTypeOperator(HandleType newType, Iterable<? extends KeyHandleReference> selection) {
        this(newType, new KeySelectionSet());
        for (var ref : selection) {
            this.selection.selectHandle(ref);
        }
    }

    @Override
    protected Collection<? extends String> getTargetObjects(EditorState editor) {
        return selection.getSelectedObjects();
    }

    @Override
    protected boolean execute(EditorState editor, Map<String, ReplayObject> objects) throws Exception {
        boolean success = false;

        for (String objName : selection.getSelectedObjects()) {
            for (String chName : selection.getSelectedChannels(objName)) {
                ChannelReference chRef = new ChannelReference(objName, chName);
                KeyChannel ch = chRef.get(objects);
                if (ch == null) continue;

                for (var keyEntry : selection.getSelectedHandles(objName, chName).int2ObjectEntrySet()) {
                    int keyIdx = keyEntry.getIntKey();
                    if (keyIdx < 0 || keyIdx >= ch.getKeyframes().size()) continue;

                    Keyframe key = ch.getKeyframes().get(keyIdx);

                    IntSet selected = keyEntry.getValue();
                    boolean changeA = selected.contains(0) || selected.contains(1);
                    boolean changeB = selected.contains(0) || selected.contains(2);

                    if (changeA && changeB) {
                        key.setHandleType(newType);
                    } else if (changeA) {
                        key.setHandleAType(newType);
                        key.setHandleBType(ChannelUtils.updateOtherHandleType(newType, key.getHandleBType()));
                    } else if (changeB) {
                        key.setHandleBType(newType);
                        key.setHandleAType(ChannelUtils.updateOtherHandleType(newType, key.getHandleAType()));
                    }

                    success = true;
                }

                ChannelUtils.computeHandles(ch, null);
            }
        }
        return success;
    }
}
