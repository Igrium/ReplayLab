package com.igrium.replaylab.ui.subpanels;

import com.igrium.craftui.util.RaycastUtils;
import com.igrium.replaylab.config.Keybinds;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.operator.object.RemoveObjectsOperator;
import com.igrium.replaylab.operator.object.RenameObjectOperator;
import com.igrium.replaylab.object.ReplayObject;
import com.igrium.replaylab.ui.widgets.EntityPicker;
import com.igrium.replaylab.ui.panels.UIPanel;
import com.igrium.replaylab.config.ShortcutUtils;
import imgui.ImGui;
import imgui.type.ImString;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.Language;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.jetbrains.annotations.Nullable;

public class ViewportControls {
    private @Nullable ReplayObject contextObject;
    private @Nullable String renamingObject;

    private final ImString renameStr = new ImString(20);


    public void drawViewport(EditorState editorState) {
        EntityPicker.drawPicker();

        if (ImGui.isWindowHovered() && (ImGui.isMouseClicked(0))) {
            raycastSelect(editorState);
        }

        if (ImGui.isWindowHovered() && ImGui.isMouseReleased(1)) {
            contextObject = raycastReplayObject(editorState);
            if (contextObject != null) {
                ImGui.openPopup("objectCtx");
            }
        }

        String renameId = t("gui.replaylab.obj.rename");

        boolean wantsBeginRename = false;
        if (ImGui.beginPopup("objectCtx")) {
            if (contextObject != null) {
                int cFlags = ObjectContextMenu.drawObjectContextMenu(contextObject, editorState);
                if ((cFlags & ObjectContextMenu.WANT_RENAME) != 0) {
                    wantsBeginRename = true;
                }
                if ((cFlags & ObjectContextMenu.WANT_DELETE) != 0) {
                    editorState.applyOperator(new RemoveObjectsOperator(contextObject.getId()));
                }
            }
            ImGui.endPopup();
        }

        if (wantsBeginRename) {
            ImGui.openPopup(renameId);
            renameStr.set(contextObject.getId());
            renamingObject = contextObject.getId();
        }

        if (ImGui.beginPopup(renameId)) {
            if (renamingObject != null) {
                ImGui.text(tt("gui.replaylab.obj.rename"));
                if (wantsBeginRename) {
                    ImGui.setKeyboardFocusHere();
                }
                ImGui.inputText("##newName", renameStr);
                if (!ImGui.isItemActive() && !wantsBeginRename) {
                    ImGui.closeCurrentPopup();
                }
            } else {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        } else if (renamingObject != null) {
            editorState.applyOperator(new RenameObjectOperator(renamingObject, renameStr.get()));
            renamingObject = null;
        }

        /// === GIZMO HOTKEYS ===
        if (ImGui.shortcut(Keybinds.gizmoAll())) {
            editorState.toggleGizmos(true, true, true);
        }

        if (ImGui.shortcut(Keybinds.gizmoPos())) {
            editorState.toggleGizmos(true, false, false);
        }

        if (ImGui.shortcut(Keybinds.gizmoRot())) {
            editorState.toggleGizmos(false, true, false);
        }

        if (ImGui.shortcut(Keybinds.gizmoScale())) {
            editorState.toggleGizmos(false, false, true);
        }

        if (ImGui.shortcut(Keybinds.localTransforms())) {
            editorState.setLocalGizmos(!editorState.isLocalGizmos());
        }

        if (ImGui.shortcut(Keybinds.deleteSelected())) {
            editorState.applyOperator(new RemoveObjectsOperator(editorState.getSelectedObjects()));
        }

        if (ImGui.shortcut(Keybinds.frameSelected())) {
            editorState.snapViewportToSelected();
        }


        /// === ROLL ===
        editorState.setRollingCamera(editorState.isPilotingCamera()
                && ShortcutUtils.isKeyChordDown(Keybinds.cameraRoll()));

        UIPanel.processGlobalHotkeys(editorState);
        UIPanel.testAddKeyShortcut(editorState);
    }

    private void raycastSelect(EditorState editorState) {
        Mouse mouse = MinecraftClient.getInstance().mouse;

        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return;

        HitResult raycast = RaycastUtils.raycastViewport((float) mouse.getX(), (float) mouse.getY(), 1000, e -> true, false);
        if (raycast instanceof EntityHitResult hit) {
            Entity ent = hit.getEntity();
            if (ImGui.getIO().getKeyCtrl() || ImGui.getIO().getKeyShift()) {
                /*
                    When the user ctrl-clicks an entity in the viewport:When the user ctrl-clicks an entity in the viewport:
                    If any scene object referencing that entity is currently the active object, the entire group is deselected and the active object is cleared.
                    Otherwise, all scene objects referencing that entity are selected, and the first one in the group becomes the active object.
                 */
                Iterable<ReplayObject> iter = () -> editorState.getScene().referencingObjects(ent).iterator();
                boolean anyIsActive = false;
                for (ReplayObject replayObject : iter) {
                    String id = replayObject.getId();
                    if (editorState.isObjectActive(id)) {
                        anyIsActive = true;
                        break;
                    }
                }
                String firstId = null;
                for (ReplayObject replayObject : iter) {
                    String id = replayObject.getId();
                    editorState.setObjectSelected(id, !anyIsActive);
                    if (firstId == null)
                        firstId = id;
                }
                editorState.setActiveObject(anyIsActive ? null : firstId);
            } else {
                ReplayObject replayObject = editorState.getScene().firstReferencingObject(ent);
                if (replayObject != null) {
                    String id = replayObject.getId();
                    editorState.getSelectedObjects().clear();
                    editorState.getSelectedObjects().add(id);
                    editorState.setActiveObject(id);
                }
            }
        }
    }

    private @Nullable ReplayObject raycastReplayObject(EditorState editorState) {
        Mouse mouse = MinecraftClient.getInstance().mouse;

        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return null;

        HitResult raycast = RaycastUtils.raycastViewport((float) mouse.getX(), (float) mouse.getY(), 1000, e -> true, false);
        if (raycast instanceof EntityHitResult entHit) {
            return editorState.getScene().referencingObjects(entHit.getEntity()).findFirst().orElse(null);
        }
        return null;
    }

    private static String t(String key) {
        return Language.getInstance().get(key) + "###" + key;
    }
    private static String tt(String key) {
        return Language.getInstance().get(key);
    }
}
