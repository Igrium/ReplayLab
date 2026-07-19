package com.igrium.replaylab.operator.constraint;

import com.igrium.replaylab.anim.KeyChannel;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.object.ReplayObject;
import com.igrium.replaylab.operator.object.MultiObjectOperator;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renames a constraint along with all channels that reference it
 */
public class RenameConstraintOperator extends MultiObjectOperator {

    private final String objName;

    private final String oldName;
    private final String newName;

    public RenameConstraintOperator(String objName, String oldName, String newName) {
        this.objName = objName;
        this.oldName = oldName;
        this.newName = newName;
    }

    @Override
    protected Collection<? extends String> getTargetObjects(EditorState editor) {
        return List.of(objName);
    }

    @Override
    protected boolean execute(EditorState editor, Map<String, ReplayObject> objects) throws Exception {
        if (newName.isBlank()) {
            return false; // new name may not be blank
        }
        ReplayObject obj = objects.get(objName);
        if (obj == null) return false;

        String realNewName = obj.getConstraints().rename(oldName, newName);
        if (realNewName.isBlank()) return false;

        Map<String, KeyChannel> channels = new HashMap<>();
        var chIterator = obj.getChannels().entrySet().iterator();
        while (chIterator.hasNext()) {
            var entry = chIterator.next();
            String[] split = entry.getKey().split(":", 2);
            if (split[0].equals(oldName)) {
                channels.put(split[1], entry.getValue());
                chIterator.remove();
            }
        }

        for (var entry : channels.entrySet()) {
            obj.getChannels().put(realNewName + ":" + entry.getKey(), entry.getValue());
        }

        return true;
    }
}
