package com.igrium.replaylab.ui.panels;

import com.igrium.replaylab.config.Keybinds;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.operator.keyframe.InsertKeyframeOperator;
import com.igrium.replaylab.operator.ReplayOperator;
import com.igrium.replaylab.operator.scene.SetSceneCameraOperator;
import com.igrium.replaylab.object.ReplayObject;
import imgui.ImGui;
import imgui.type.ImBoolean;
import lombok.Getter;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a single panel in the UI
 */
public abstract class UIPanel {
    @Getter
    private final Identifier id;
    private final ImBoolean visible = new ImBoolean(true);

    private boolean requestFocus;

    public UIPanel(Identifier id) {
        this.id = id;
    }

    public UIPanel(Identifier id, boolean visible) {
        this.id = id;
        this.visible.set(visible);
    }

    public boolean isVisible() {
        return visible.get();
    }

    public void setVisible(boolean visible) {
        this.visible.set(visible);
    }

    public String getTitle() {
        return Language.getInstance().get(id.toTranslationKey("panel"));
    }

    public final String getPanelName() {
        return getTitle() + "###" + id;
    }

    public void requestFocus() {
        requestFocus = true;
    }

    public final void draw(EditorState editorState, int imGuiWindowFlags, @Nullable Runnable callback) {
        if (visible.get()) {

            if (requestFocus) {
                ImGui.setNextWindowFocus();
                requestFocus = false;
            }

            if (ImGui.begin(getPanelName(), visible, imGuiWindowFlags)) {
                drawContents(editorState);
                if (callback != null) {
                    callback.run();
                }
                processGlobalHotkeys(editorState);
            }
            ImGui.end();
        }

    }

    protected abstract void drawContents(EditorState editorState);

    public static void processGlobalHotkeys(EditorState editorState) {
        var io = ImGui.getIO();
        if (io.getWantTextInput()) return;

        if (ImGui.shortcut(Keybinds.redo())) {
            editorState.redo();
        }

        if (ImGui.shortcut(Keybinds.undo())) {
            editorState.undo();
        }

        if (ImGui.shortcut(Keybinds.playPause())) {
            editorState.togglePlayback();
        }

        if (ImGui.shortcut(Keybinds.sceneStart())) {
            editorState.jumpSceneStart();
        }

        if (ImGui.shortcut(Keybinds.sceneEnd())) {
            editorState.jumpSceneEnd();
        }

        if (ImGui.shortcut(Keybinds.prevKey())) {
            editorState.jumpPrevKeyframe();
        }

        if (ImGui.shortcut(Keybinds.nextKey())) {
            editorState.jumpNextKeyframe();
        }

        if (ImGui.shortcut(Keybinds.cameraView())) {
            editorState.setCameraView(!editorState.isCameraView());
        }

        if (ImGui.shortcut(Keybinds.activeToCam())) {
            editorState.applyOperator(new SetSceneCameraOperator(editorState.getActiveObject()));
        }

        if (ImGui.shortcut(Keybinds.quickMode())) {
            editorState.setQuickMode(!editorState.isQuickMode());
        }
    }

    public static void testAddKeyShortcut(EditorState editorState) {
        // Test keyframes before validating object so we still consume it keybind
        boolean wantKeyPos = ImGui.shortcut(Keybinds.addKeyPos());
        boolean wantKeyRot = ImGui.shortcut(Keybinds.addKeyRot());
        boolean wantKeyScale = ImGui.shortcut(Keybinds.addKeyScale());
        if (ImGui.shortcut(Keybinds.addKey())) {
            wantKeyPos = true;
            wantKeyRot = true;
            wantKeyScale = true;
        }
        ReplayObject selected = editorState.getScene().getObject(editorState.getActiveObject());
        if (selected != null && (wantKeyPos || wantKeyRot || wantKeyScale)) {
            editorState.applyOperator(new InsertKeyframeOperator(editorState.getPlayhead(),
                    wantKeyPos, wantKeyRot, wantKeyScale, selected.getId()));
        }
    }

    public void onAppliedOperator(ReplayOperator op, EditorState editorState) {}
}
