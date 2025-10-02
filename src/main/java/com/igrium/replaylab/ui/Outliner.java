package com.igrium.replaylab.ui;

import com.igrium.replaylab.operator.AddObjectOperator;
import com.igrium.replaylab.operator.RemoveObjectOperator;
import com.igrium.replaylab.operator.RenameObjectOperator;
import com.igrium.replaylab.scene.obj.ReplayObjectType;
import com.igrium.replaylab.scene.obj.ReplayObjects;
import imgui.ImGui;
import imgui.type.ImString;
import net.minecraft.util.Language;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

public class Outliner {

    /**
     * The original of the item that's currently being renamed
     */
    private @Nullable String currentRenamingItem;

    private final ImString renamingString = new ImString();

    private boolean firstRenamingFrame;

    private RenameObjectOperator queuedRename;
    private String queuedDelete;

    public void drawOutliner(ReplayLabEditorState editorState) {

        // HEADER
        drawAddObjectButton(editorState);
        ImGui.sameLine();
        drawRemoveObjectButton(editorState);

        if (ImGui.beginChild("objects")) {
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            if (ImGui.beginListBox("##objects")) {
                for (String id : editorState.getScene().getObjects().keySet()) {
                    if (id.equals("scene")) {
                        continue; // Don't show the scene object
                    }
                    if (id.equals(currentRenamingItem)) {
                        if (firstRenamingFrame) {
                            ImGui.setKeyboardFocusHere();
                        }
                        ImGui.inputText("##name", renamingString);
                        if (ImGui.isKeyDown(GLFW.GLFW_KEY_ESCAPE)) {
                            currentRenamingItem = null;
                        } else if (ImGui.isKeyDown(GLFW.GLFW_KEY_ENTER) || !(ImGui.isItemActive() || firstRenamingFrame)) {
                            commitNameChange();
                        }
                        firstRenamingFrame = false;
                    } else {
                        boolean isSelected = id.equals(editorState.getSelectedObject());
                        if (ImGui.selectable(id, isSelected)) {
                            editorState.setSelectedObject(id);
                        }
                        if (ImGui.beginPopupContextItem()) {
                            drawContextMenu(id);
                            ImGui.endPopup();
                        }
                        if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
                            beginRenaming(id);
                        }
                    }
                }

                ImGui.endListBox();
            }
        }
        ImGui.endChild();

        // Delay rename until the end to avoid concurrent modification
        if (queuedRename != null) {
            editorState.applyOperator(queuedRename);
            queuedRename = null;
        }

        if (queuedDelete != null) {
            editorState.applyOperator(new RemoveObjectOperator(queuedDelete));
            queuedDelete = null;
        }
    }

    private void beginRenaming(String id) {
        currentRenamingItem = id;
        renamingString.set(id);
        firstRenamingFrame = true;
    }

    private void commitNameChange() {
        queuedRename = new RenameObjectOperator(currentRenamingItem, renamingString.get());
        currentRenamingItem = null;
    }

    private void drawContextMenu(String id) {
        if (ImGui.menuItem("Rename")) {
            beginRenaming(id);
        }
        if (ImGui.menuItem("Delete")) {
            queuedDelete = id;
        }
    }

    private void drawAddObjectButton(ReplayLabEditorState editorState) {
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

    private static void drawRemoveObjectButton(ReplayLabEditorState editorState) {
        String selected = editorState.getSelectedObject();
        ImGui.beginDisabled(selected == null);

        if (ImGui.button("-") && selected != null) {
            editorState.applyOperator(new RemoveObjectOperator(selected));
        }

        ImGui.endDisabled();
    }
}
