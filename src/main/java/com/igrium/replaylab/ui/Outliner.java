package com.igrium.replaylab.ui;

import com.igrium.replaylab.operator.AddObjectOperator;
import com.igrium.replaylab.scene.obj.AnimationObjectType;
import imgui.ImGui;

public class Outliner {
    public static void drawOutliner(ReplayLabEditorState editorState) {
        if (ImGui.button("+")) {
            addObject(editorState);
            editorState.setSelectedObject("testObj");
        }

        if (ImGui.beginChild("objects")) {
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            if (ImGui.beginListBox("##objects")) {
                for (String id : editorState.getScene().getObjects().keySet()) {
                    boolean isSelected = id.equals(editorState.getSelectedObject());
                    if (ImGui.selectable(id, isSelected)) {
                        editorState.setSelectedObject(id);
                    }
                }

                ImGui.endListBox();
            }
        }
        ImGui.endChild();
    }

    private static void addObject(ReplayLabEditorState editorState) {
        var scene = editorState.getScene();
        scene.applyOperator(new AddObjectOperator(AnimationObjectType.DUMMY, "testObj"));
    }
}
