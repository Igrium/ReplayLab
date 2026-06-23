package com.igrium.replaylab.operator;

import com.igrium.replaylab.ReplayLab;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.editor.KeySelectionSet;
import com.igrium.replaylab.scene.obj.ReplayObject;
import org.slf4j.Logger;

import java.util.*;

public class InsertKeyframeOperator extends MultiObjectOperator {

    private static final Logger LOGGER = ReplayLab.getLogger();

    private final int timestamp;
    private final boolean pos;
    private final boolean rot;
    private final boolean scale;
    private final List<String> objNames;

    public InsertKeyframeOperator(int timestamp, boolean pos, boolean rot, boolean scale, String... objNames) {
        this.timestamp = timestamp;
        this.pos = pos;
        this.rot = rot;
        this.scale = scale;
        this.objNames = List.of(objNames);
    }

    public InsertKeyframeOperator(int timestamp, boolean pos, boolean rot, boolean scale, Collection<? extends String> objNames) {
        this.timestamp = timestamp;
        this.pos = pos;
        this.rot = rot;
        this.scale = scale;
        this.objNames = List.copyOf(objNames);
    }

    @Override
    protected Collection<? extends String> getTargetObjects(EditorState editor) {
        return objNames;
    }

    @Override
    protected boolean execute(EditorState editor, Map<String, ReplayObject> objects) throws Exception {
        editor.getKeySelection().deselectAll();

        Set<KeySelectionSet.KeyframeReference> newKeys = new HashSet<>(objNames.size() * 10);
        for (var objName : objNames) {
            ReplayObject obj = objects.get(objName);
            if (obj == null) {
                LOGGER.warn("No object with name {} found", objName);
                continue;
            }

            newKeys.addAll(obj.insertKeyframe(editor, timestamp, pos, rot, scale));
        }

        for (var newKey : newKeys) {
            editor.getKeySelection().selectKeyframe(newKey);
        }
        return !newKeys.isEmpty();
    }
}
