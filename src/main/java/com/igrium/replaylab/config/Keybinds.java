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

    private int gizmoAll = ImGuiKey.Q;
    private int gizmoPos = ImGuiKey.W;
    private int gizmoRot = ImGuiKey.E;
    private int gizmoScale = ImGuiKey.R;

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

        this.gizmoAll = other.gizmoAll;
        this.gizmoPos = other.gizmoPos;
        this.gizmoRot = other.gizmoRot;
        this.gizmoScale = other.gizmoScale;
    }

    public void reset() {
        copyFrom(new Keybinds());
    }

    public static int undo() {
        return ReplayLabConfig.getInstance().getKeybinds().getUndo();
    }

    public static int redo() {
        return ReplayLabConfig.getInstance().getKeybinds().getRedo();
    }

    public static int playPause() {
        return ReplayLabConfig.getInstance().getKeybinds().getPlayPause();
    }

    public static int cameraView() {
        return ReplayLabConfig.getInstance().getKeybinds().getCameraView();
    }

    public static int addKey() {
        return ReplayLabConfig.getInstance().getKeybinds().getAddKey();
    }

    public static int addKeySingle() {
        return ReplayLabConfig.getInstance().getKeybinds().getAddKeySingle();
    }

    public static int selectAll() {
        return ReplayLabConfig.getInstance().getKeybinds().getSelectAll();
    }

    public static int selectNone() {
        return ReplayLabConfig.getInstance().getKeybinds().getSelectNone();
    }

    public static int deleteSelected() {
        return ReplayLabConfig.getInstance().getKeybinds().getDeleteSelected();
    }

    public static int gizmoAll() {
        return ReplayLabConfig.getInstance().getKeybinds().getGizmoAll();
    }

    public static int gizmoPos() {
        return ReplayLabConfig.getInstance().getKeybinds().getGizmoPos();
    }

    public static int gizmoRot() {
        return ReplayLabConfig.getInstance().getKeybinds().getGizmoRot();
    }

    public static int gizmoScale() {
        return ReplayLabConfig.getInstance().getKeybinds().getGizmoScale();
    }
}
