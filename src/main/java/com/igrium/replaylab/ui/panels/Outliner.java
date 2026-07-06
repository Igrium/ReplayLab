package com.igrium.replaylab.ui.panels;

import com.igrium.replaylab.config.Keybinds;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.operator.object.AddObjectOperator;
import com.igrium.replaylab.operator.object.RemoveObjectsOperator;
import com.igrium.replaylab.operator.object.RenameObjectOperator;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.scene.obj.ReplayObjects;
import com.igrium.replaylab.ui.subpanels.ObjectContextMenu;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiKey;
import imgui.type.ImString;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import net.minecraft.util.math.ColorHelper;
import org.jetbrains.annotations.Nullable;

public class Outliner extends UIPanel {

    /**
     * The original of the item that's currently being renamed
     */
    private @Nullable String currentRenamingItem;

    private final ImString renamingString = new ImString();

    private boolean firstRenamingFrame;

    private RenameObjectOperator queuedRename;
    private String[] queuedDelete;


    public Outliner(Identifier id) {
        super(id);
    }

    @Override
    protected void drawContents(EditorState editorState) {
        drawOutliner(editorState);
    }

    public void drawOutliner(EditorState editorState) {

        // HEADER
        drawAddObjectButton(editorState);
        ImGui.sameLine();
        drawRemoveObjectButton(editorState);

        // Selectable uses header colors
        ImGui.pushStyleColor(ImGuiCol.Header, ColorHelper.withAlpha(48, ImGui.getColorU32(ImGuiCol.HeaderActive)));

        if (ImGui.beginChild("objects")) {
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());

            if (ImGui.beginListBox("##objects")) {
                String[] objs = editorState.getScene().getObjects().keySet().toArray(new String[0]);

                int clickedIdx = -1;
                int activeIdx = -1;

                for (int i = 0; i < objs.length; i++) {
                    String id = objs[i];
                    ReplayObject obj = editorState.getScene().getObject(id);
                    if (obj == null || obj.getType().hideInOutliner())
                        continue;

                    if (id.equals(currentRenamingItem)) {
                        if (firstRenamingFrame) {} {
                            ImGui.setKeyboardFocusHere();
                        }
                        ImGui.inputText("##name", renamingString);
                        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
                            currentRenamingItem = null;
                        } else if (ImGui.isKeyPressed(ImGuiKey.Enter) || !(ImGui.isItemActive() || firstRenamingFrame)) {
                            commitNameChange();
                        }
                        firstRenamingFrame = false;
                    } else {
                        boolean isSelected = editorState.isObjectSelected(id);
                        boolean isActive = editorState.isObjectActive(id);

                        // Active object gets brighter color.
                        if (isActive) {
                            ImGui.pushStyleColor(ImGuiCol.Header, ColorHelper.withAlpha(128, ImGui.getColorU32(ImGuiCol.Header)));
                            activeIdx = i;
                        }
                        if (ImGui.selectable(obj.getDisplayName() + "###" + id, isSelected)) {
                            clickedIdx = i;
                        }
                        if (isActive) {
                            ImGui.popStyleColor();
                        }

                        if (ImGui.beginPopupContextItem()) {
                            drawContextMenu(editorState, id);
                            ImGui.endPopup();
                        }

                        if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
                            editorState.setActiveObject(id);
                            editorState.setWantOpenInspector(true);
                        }
                    }
                }

                if (clickedIdx >= 0) {
                    String id = objs[clickedIdx];
                    if (ImGui.getIO().getKeyCtrl()) {
                        boolean isSelected = editorState.isObjectSelected(id);
                        editorState.setObjectSelected(id, !isSelected);
                        if (isSelected && editorState.isObjectActive(id)) {
                            editorState.setActiveObject(null);
                        } else if (!isSelected) {
                            editorState.setActiveObject(id);
                        }
                    } else if (ImGui.getIO().getKeyShift() && activeIdx >= 0) {
                        for (int i = Math.min(clickedIdx, activeIdx); i <= Math.max(clickedIdx, activeIdx); i++) {
                            editorState.setObjectSelected(objs[i], true);
                        }
                    } else {
                        editorState.getSelectedObjects().clear();
                        editorState.getSelectedObjects().add(id);
                        editorState.setActiveObject(id);
                    }
                }

                ImGui.endListBox();
            }

        }
        ImGui.endChild();
        ImGui.popStyleColor();

        if (ImGui.shortcut(Keybinds.deleteSelected())) {
            queuedDelete = editorState.getSelectedObjects().toArray(String[]::new);
        }

        if (ImGui.shortcut(Keybinds.copy())) {
            ImGui.setClipboardText(editorState.copyObjects());
        }

        if (ImGui.shortcut(Keybinds.paste())) {
            editorState.pasteObjects(ImGui.getClipboardText());
        }

        // Delay rename until the end to avoid concurrent modification
        if (queuedRename != null) {
            editorState.applyOperator(queuedRename);
            queuedRename = null;
        }

        if (queuedDelete != null) {
            editorState.applyOperator(new RemoveObjectsOperator(queuedDelete));
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

    private void drawContextMenu(EditorState editor, String id) {
        ReplayObject obj = editor.getScene().getObject(id);
        if (obj == null) return;

        int cFlags = ObjectContextMenu.drawObjectContextMenu(obj, editor);

        if ((cFlags & ObjectContextMenu.WANT_RENAME) != 0) {
            beginRenaming(id);
        }

        if ((cFlags & ObjectContextMenu.WANT_DELETE) != 0) {
            queuedDelete = editor.getSelectedObjects().toArray(String[]::new);
        }

//        if (ImGui.menuItem("Rename")) {
//            beginRenaming(id);
//        }
//        if (ImGui.menuItem("Delete")) {
//            queuedDelete = editor.getSelectedObjects().toArray(new String[0]);
//        }
    }

    private void drawAddObjectButton(EditorState editorState) {
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

    private static void drawRemoveObjectButton(EditorState editorState) {
        var selectedObjects = editorState.getSelectedObjects();
        ImGui.beginDisabled(selectedObjects.isEmpty());

        if (ImGui.button("-") && !selectedObjects.isEmpty()) {
            editorState.applyOperator(new RemoveObjectsOperator(selectedObjects));
        }

        ImGui.endDisabled();
    }
}
