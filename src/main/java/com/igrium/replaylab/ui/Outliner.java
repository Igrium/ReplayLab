package com.igrium.replaylab.ui;

import com.igrium.replaylab.operator.AddObjectOperator;
import com.igrium.replaylab.scene.obj.ReplayObjectType;
import com.igrium.replaylab.scene.obj.ReplayObjects;
import imgui.ImGui;
import net.minecraft.util.Language;

public class Outliner {
    public static void drawOutliner(ReplayLabEditorState editorState) {
//        if (ImGui.button("+")) {
//            addObject(editorState);
//            editorState.setSelectedObject("testObj");
//        }
        drawAddObjectButton(editorState);

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

    private static void drawAddObjectButton(ReplayLabEditorState editorState) {
        if (ImGui.button("+")) {
            ImGui.openPopup("New Object");
        }

        if (ImGui.beginPopup("New Object")) {
            ReplayObjects.getSpawnableTypes().forEach(type -> {
                String name = Language.getInstance().get(type.getTranslationKey());
                if (ImGui.selectable(name)) {
                    var obj = type.create(editorState.getScene());
                    editorState.applyOperator(new AddObjectOperator(editorState.getScene().makeNameUnique(name), obj));
                }
            });
            ImGui.endPopup();
        }
    }


}
