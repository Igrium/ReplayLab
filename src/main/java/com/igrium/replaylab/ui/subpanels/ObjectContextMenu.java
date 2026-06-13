package com.igrium.replaylab.ui.subpanels;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.math.Transform3;
import com.igrium.replaylab.operator.CommitObjectUpdateOperator;
import com.igrium.replaylab.operator.RemoveObjectsOperator;
import com.igrium.replaylab.operator.SetSceneCameraOperator;
import com.igrium.replaylab.scene.obj.EntityProvider;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.scene.obj.ReplayObject3D;
import com.igrium.replaylab.scene.obj.TransformProvider;
import imgui.ImGui;
import net.minecraft.util.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ObjectContextMenu {
    private static final Logger LOGGER = LoggerFactory.getLogger("ReplayLab/ObjectContextMenu");

    public static int WANT_RENAME = 1;
    public static int WANT_DELETE = 2;

    private static Exception parseException;

    public static int drawObjectContextMenu(ReplayObject object, EditorState editorState) {
        int rFlags = 0;

        if (ImGui.menuItem(t("gui.replaylab.obj.reveal"))) {
            editorState.setActiveObject(object.getId());
            editorState.setWantOpenInspector(true);
        }

        if (ImGui.menuItem(t("gui.replaylab.obj.rename"))) {
            rFlags |= WANT_RENAME;
        }
//
//        if (ImGui.menuItem(t("gui.replaylab.obj.copy"))) {
//            // TODO: copy/paste logic
//            LOGGER.info("Copy");
//        }
//
//        if (ImGui.menuItem(t("gui.replaylab.obj.paste"))) {
//            LOGGER.info("Paste");
//        }

        if (ImGui.menuItem(t("gui.replaylab.obj.delete"))) {
            rFlags |= WANT_DELETE;
        }

        ImGui.separator();

        ImGui.beginDisabled(!(object instanceof EntityProvider));

        if (ImGui.menuItem(t("gui.replaylab.obj.setcam"))) {
            editorState.applyOperator(new SetSceneCameraOperator(object.getId()));
        }

        ImGui.endDisabled();

        ImGui.separator();

        ImGui.beginDisabled(!(object instanceof TransformProvider));

        if (ImGui.menuItem(t("gui.replaylab.obj.copyTransform")) && object instanceof TransformProvider t) {
            String transform = new Gson().toJson(t.getTransform(new Transform3()));
            ImGui.setClipboardText(transform);
            LOGGER.info("Copied transform to clipboard: {}", transform);
        }

        ImGui.endDisabled();


        ImGui.beginDisabled(!(object instanceof ReplayObject3D));
        if (ImGui.menuItem(t("gui.replaylab.obj.pasteTransform")) && object instanceof ReplayObject3D t) {
            try {
                Transform3 transform = new Gson().fromJson(ImGui.getClipboardText(), Transform3.class);
                t.setBaseTransform(transform);
                editorState.applyOperator(new CommitObjectUpdateOperator(object.getId()));
            } catch (JsonParseException e) {
                LOGGER.error("Error pasting transform: {}", e.toString());
            }
        }

        ImGui.endDisabled();

        return rFlags;
    }

    private static String t(String key) {
        return Language.getInstance().get(key) + "###" + key;
    }
}
