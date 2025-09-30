package com.igrium.replaylab.ui;

import com.igrium.replaylab.scene.obj.ReplayObjects;
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
                    if (id.equals("scene")) {
                        continue; // Don't show the scene object
                    }
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
        var obj = ReplayObjects.CAMERA.create(scene);
        scene.addObject("camera", obj);
    }
}
