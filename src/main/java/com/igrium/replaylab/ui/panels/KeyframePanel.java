package com.igrium.replaylab.ui.panels;

import com.igrium.replaylab.config.Keybinds;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.editor.KeySelectionSet.KeyHandleReference;
import com.igrium.replaylab.editor.KeySelectionSet.KeyframeReference;
import com.igrium.replaylab.operator.RemoveKeyframesOperator;
import com.igrium.replaylab.operator.SetHandleTypeOperator;
import com.igrium.replaylab.operator.SetInterpModeOperator;
import com.igrium.replaylab.scene.key.InterpolationMode;
import com.igrium.replaylab.scene.key.Keyframe;
import com.igrium.replaylab.scene.key.Keyframe.HandleType;
import imgui.ImGui;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;

import java.util.Collection;
import java.util.List;

/**
 * A panel designed to render keyframes (auto-listens to hotkeys, etc)
 */
public abstract class KeyframePanel extends UIPanel {
    public KeyframePanel(Identifier id) {
        super(id);
    }

    @Override
    protected void drawContents(EditorState editorState) {
        if (ImGui.shortcut(Keybinds.deleteSelected())) {
            // we need to clear selected keyframes
            var selected = editorState.getKeySelection().getSelectedKeyframes();
            editorState.getKeySelection().deselectAll();

            editorState.applyOperator(new RemoveKeyframesOperator(selected));
        }

        if (ImGui.shortcut(Keybinds.selectAll())) {
            editorState.getKeySelection().selectAll(editorState.getScene().getObjects());
        }

        if (ImGui.shortcut(Keybinds.selectNone())) {
            editorState.getKeySelection().deselectAll();
        }

        if (ImGui.shortcut(Keybinds.copy())) {
            ImGui.setClipboardText(editorState.copyKeyframes());
        }

        if (ImGui.shortcut(Keybinds.paste())) {
            editorState.pasteKeyframes(ImGui.getClipboardText());
        }

        testAddKeyShortcut(editorState);
    }

    protected static void keyContextMenu(EditorState editor, Collection<KeyHandleReference> selHandles) {
        if (ImGui.beginMenu(t("gui.replaylab.handle_type"))) {

            // If every selected handle has the same handle type, find it.
            HandleType handleType = null;
            for (var hRef : selHandles) {
                Keyframe key = hRef.keyRef().get(editor.getScene().getObjects());
                if (key == null) continue;

                HandleType type = switch(hRef.handleIndex()) {
                    case 1 -> key.getHandleAType();
                    case 2 -> key.getHandleBType();
                    default -> null;
                };

                if (type == null) continue;

                if (handleType == null) {
                    handleType = type;
                } else if (handleType != type) {
                    handleType = null;
                    break;
                }
            }

            HandleType newHandleType = null;
            for (var type : HandleType.values()) {
                if (ImGui.menuItem(t(type.getTranslationKey()), "", handleType == type)) {
                    newHandleType = type;
                }
            }

            if (newHandleType != null) {
                editor.applyOperator(new SetHandleTypeOperator(newHandleType, selHandles));
            }
            ImGui.endMenu();
        }

        if (ImGui.beginMenu(t("gui.replaylab.interp_mode"))) {
            List<KeyframeReference> selKeys = selHandles.stream()
                    .map(KeyHandleReference::keyRef)
                    .distinct()
                    .toList();


            // If every keyframe has the same interpolation mode, find it.
            InterpolationMode interpMode = null;
            for (var keyRef : selKeys) {
                Keyframe key = keyRef.get(editor.getScene().getObjects());
                if (key == null) continue;

                if (interpMode == null) {
                    interpMode = key.getInterpolationMode();
                } else if (interpMode != key.getInterpolationMode()) {
                    interpMode = null;
                    break;
                }
            }

            InterpolationMode newInterpMode = null;
            for (var mode : InterpolationMode.values()) {
                if (ImGui.menuItem(t(mode.getTranslationKey()), "", interpMode == mode)) {
                    newInterpMode = mode;
                }
            }

            if (newInterpMode != null) {
                editor.applyOperator(new SetInterpModeOperator(newInterpMode, selKeys));
            }

            ImGui.endMenu();
        }
    }



    private static String t(String key) {
        return Language.getInstance().get(key) + "###" + key;
    }
}
