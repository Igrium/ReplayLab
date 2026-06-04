package com.igrium.replaylab.config;

import imgui.flag.ImGuiKey;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class Keybinds {

    private int undo = ImGuiKey.ModCtrl | ImGuiKey.Z;
    private int redo = ImGuiKey.ModCtrl | ImGuiKey.ModShift | ImGuiKey.Z;

    private int playPause = ImGuiKey.Space;
    private int cameraView = ImGuiKey.C;

    private int selectAll = ImGuiKey.ModCtrl | ImGuiKey.A;
    private int selectNone = ImGuiKey.ModAlt | ImGuiKey.A;
    private int deleteSelected = ImGuiKey.Delete;

    private int addKey = ImGuiKey.I;
    private int addKeySingle = ImGuiKey.ModAlt | ImGuiKey.I;

    private int localTransforms = ImGuiKey.Tab;
    private int gizmoAll = ImGuiKey.Q;
    private int gizmoPos = ImGuiKey.W;
    private int gizmoRot = ImGuiKey.E;
    private int gizmoScale = ImGuiKey.R;

    private int cameraRoll = ImGuiKey.R;

    private int sceneStart = ImGuiKey.I;
    private int sceneEnd = ImGuiKey.O;
    private int prevKey = ImGuiKey.J;
    private int nextKey = ImGuiKey.K;

    public void copyFrom(Keybinds other) {
        this.undo = other.undo;
        this.redo = other.redo;

        this.playPause = other.playPause;
        this.cameraView = other.cameraView;

        this.addKey = other.addKey;
        this.addKeySingle = other.addKeySingle;

        this.selectAll = other.selectAll;
        this.selectNone = other.selectNone;
        this.deleteSelected = other.deleteSelected;

        this.localTransforms = other.localTransforms;
        this.gizmoAll = other.gizmoAll;
        this.gizmoPos = other.gizmoPos;
        this.gizmoRot = other.gizmoRot;
        this.gizmoScale = other.gizmoScale;

        this.cameraRoll = other.cameraRoll;

        this.sceneStart = other.sceneStart;
        this.sceneEnd = other.sceneEnd;
        this.prevKey = other.prevKey;
        this.nextKey = other.nextKey;
    }

    public void reset() {
        copyFrom(new Keybinds());
    }

    public static int undo() {
        return getKeybinds().getUndo();
    }

    public static int redo() {
        return getKeybinds().getRedo();
    }

    public static int playPause() {
        return getKeybinds().getPlayPause();
    }

    public static int cameraView() {
        return getKeybinds().getCameraView();
    }

    public static int addKey() {
        return getKeybinds().getAddKey();
    }

    public static int addKeySingle() {
        return getKeybinds().getAddKeySingle();
    }

    public static int selectAll() {
        return getKeybinds().getSelectAll();
    }

    public static int selectNone() {
        return getKeybinds().getSelectNone();
    }

    public static int deleteSelected() {
        return getKeybinds().getDeleteSelected();
    }

    public static int localTransforms() {
        return getKeybinds().getLocalTransforms();
    }

    public static int gizmoAll() {
        return getKeybinds().getGizmoAll();
    }

    public static int gizmoPos() {
        return getKeybinds().getGizmoPos();
    }

    public static int gizmoRot() {
        return getKeybinds().getGizmoRot();
    }

    public static int gizmoScale() {
        return getKeybinds().getGizmoScale();
    }


    public static int cameraRoll() {
        return getKeybinds().getCameraRoll();
    }

    public static int sceneStart() {
        return getKeybinds().getSceneStart();
    }

    public static int sceneEnd() {
        return getKeybinds().getSceneEnd();
    }

    public static int prevKey() {
        return getKeybinds().getPrevKey();
    }

    public static int nextKey() {
        return getKeybinds().getNextKey();
    }

    private static Keybinds getKeybinds() {
        return ReplayLabConfig.getInstance().getKeybinds();
    }
}
