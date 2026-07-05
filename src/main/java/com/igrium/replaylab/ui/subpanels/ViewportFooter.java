package com.igrium.replaylab.ui.subpanels;

import com.igrium.craftui.CraftUIFonts;
import com.igrium.replaylab.config.ReplayLabConfig;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.ui.ReplayLabIcons;
import com.igrium.replaylab.ui.util.ReplayLabControls;
import com.igrium.replaylab.util.Timestamps;
import imgui.ImGui;
import imgui.flag.ImGuiMouseCursor;
import imgui.type.ImBoolean;
import net.minecraft.util.Language;
import net.minecraft.util.Util;

public class ViewportFooter {

    private float prevCameraControlsGroupWidth;
    private float prevPlaybackControlsWidth;

    private long clipboardCopyTime;

    private final ImBoolean cameraViewInput = new ImBoolean();
    private final ImBoolean tmpBoolean = new ImBoolean();

    public void drawPlaybackControls(EditorState editorState) {
        ImGui.pushFont(ReplayLabIcons.getFont());
        float buttonSize = ImGui.getTextLineHeightWithSpacing() * 1.25f;
        float viewportFooterHeight = buttonSize + ImGui.getStyle().getWindowPaddingY() * 2;
        ImGui.popFont();

        ImGui.setCursorPosY(ImGui.getContentRegionMaxY() - viewportFooterHeight);
        ImGui.setNextWindowBgAlpha(1);

        if (!ImGui.beginChild("Playback", ImGui.getContentRegionAvailX(), viewportFooterHeight, true)) {
            ImGui.endChild();
            return;
        }

        // Scene name (left-aligned)
        ImGui.alignTextToFramePadding();
        ImGui.text("Scene: " + editorState.getSceneName());
        ImGui.sameLine();

        // Transport buttons (center-aligned)
        ImGui.setCursorPosX(ImGui.getContentRegionMaxX() / 2 - prevPlaybackControlsWidth / 2);
        ImGui.beginGroup();
        ImGui.alignTextToFramePadding();

        timestampView("##replayTime", editorState.getSceneTime(), tt("gui.replaylab.scene_time"));
        ImGui.sameLine();

        if (playbackIcon(ReplayLabIcons.ICON_TO_START_ALT +
                "##sceneStart", tt("key.replaylab.scene_start"), buttonSize)) {
            editorState.jumpSceneStart();
        }
        playbackIcon(ReplayLabIcons.ICON_TO_START + "##prevKey", tt("key.replaylab.prev_key"), buttonSize);

        char playPauseIcon = editorState.isPlaying() ? ReplayLabIcons.ICON_PAUSE : ReplayLabIcons.ICON_PLAY;
        if (playbackIcon(playPauseIcon + "##playPause", tt("key.replaylab.playpause"), buttonSize)) {
            editorState.togglePlayback();
        }

        playbackIcon(ReplayLabIcons.ICON_TO_END + "##nextKey", tt("key.replaylab.next_key"), buttonSize);
        if (playbackIcon(ReplayLabIcons.ICON_TO_END_ALT + "##sceneEnd", tt("key.replaylab.scene_end"), buttonSize)) {
            editorState.jumpSceneEnd();
        }

        timestampView("##sceneTime", editorState.getPlayhead(), tt("gui.replaylab.scene_time"));

        ImGui.endGroup();
        prevPlaybackControlsWidth = ImGui.getItemRectSizeX();
        ImGui.sameLine();


        // Camera controls & gizmos (right-aligned)
        ImGui.setCursorPosX(ImGui.getContentRegionMaxX() - prevCameraControlsGroupWidth);
        ImGui.beginGroup();

        tmpBoolean.set(editorState.showGizmoPos() && editorState.showGizmoRot() && editorState.showGizmoScale());
        if (ReplayLabControls.toggleButton(ReplayLabIcons.ICON_FREE_TRANSFORM, "gizmoAll", tmpBoolean,
                "key.replaylab.gizmo_all")) {
            editorState.showGizmoPos(tmpBoolean.get());
            editorState.showGizmoRot(tmpBoolean.get());
            editorState.showGizmoScale(tmpBoolean.get());
        }
        ImGui.sameLine();
        tmpBoolean.set(editorState.showGizmoPos());
        if (ReplayLabControls.toggleButton(ReplayLabIcons.ICON_MOVE, "gizmoPos", tmpBoolean,
                "key.replaylab.gizmo_pos")) {
            editorState.showGizmoPos(tmpBoolean.get());
            if (!ImGui.getIO().getKeyCtrl()) {
                editorState.showGizmoRot(false);
                editorState.showGizmoScale(false);
            }
        }
        ImGui.sameLine();
        tmpBoolean.set(editorState.showGizmoRot());
        if (ReplayLabControls.toggleButton(ReplayLabIcons.ICON_ROTATE, "gizmoRot", tmpBoolean,
                "key.replaylab.gizmo_rot")) {
            editorState.showGizmoRot(tmpBoolean.get());
            if (!ImGui.getIO().getKeyCtrl()) {
                editorState.showGizmoPos(false);
                editorState.showGizmoScale(false);
            }
        }
        ImGui.sameLine();
        tmpBoolean.set(editorState.showGizmoScale());
        if (ReplayLabControls.toggleButton(ReplayLabIcons.ICON_SCALE, "gizmoScale", tmpBoolean,
                "key.replaylab.gizmo_scale")) {
            editorState.showGizmoScale(tmpBoolean.get());
            if (!ImGui.getIO().getKeyCtrl()) {
                editorState.showGizmoPos(false);
                editorState.showGizmoRot(false);
            }
        }
        ImGui.sameLine();
        tmpBoolean.set(editorState.isLocalGizmos());
        char localIcon = tmpBoolean.get() ? ReplayLabIcons.ICON_CUBE : ReplayLabIcons.ICON_GLOBE;
        if (ReplayLabControls.toggleButton(localIcon, "freeTransform", tmpBoolean,
                "key.replaylab.local_transforms")) {
            editorState.setLocalGizmos(tmpBoolean.get());
        }
        ImGui.sameLine();
        cameraViewInput.set(editorState.isCameraView());
        if (ReplayLabControls.toggleButton(ReplayLabIcons.ICON_VIDEOCAM, "cameraView", cameraViewInput,
                "key.replaylab.cameraview")) {
            editorState.setCameraView(cameraViewInput.get());
        }


        ImGui.endGroup();
        prevCameraControlsGroupWidth = ImGui.getItemRectSizeX();

        ImGui.endChild();
    }

    private void timestampView(String id, int timestamp, String tooltip) {
        ImGui.pushFont(CraftUIFonts.getFont(ReplayLabControls.ROBOTO_MONO));
        ImGui.beginGroup();

        String str = Timestamps.toTimestamp(timestamp, 3, ReplayLabConfig.getInstance().getTimestampMode());
        ImGui.text(str);

        float rectMinX = ImGui.getItemRectMinX();
        float rectMinY = ImGui.getItemRectMinY();
        float rectSizeX = ImGui.getItemRectSizeX();
        float rectSizeY = ImGui.getItemRectSizeY();
        float cursorX = ImGui.getCursorPosX();
        float cursorY = ImGui.getCursorPosY();

        ImGui.setCursorScreenPos(rectMinX, rectMinY);
        if (ImGui.invisibleButton(id, rectSizeX, rectSizeY)) {
            clipboardCopyTime = Util.getMeasuringTimeMs();
            ImGui.setClipboardText(str);
        }

        boolean hovered = ImGui.isItemHovered();

        ImGui.setCursorPosX(cursorX);
        ImGui.setCursorPosY(cursorY);
        ImGui.endGroup();

        if (hovered) {
            ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
        }
        if (Util.getMeasuringTimeMs() - clipboardCopyTime < 1000) {
            ImGui.setTooltip(tt("gui.replaylab.clipboarded"));
        } else if (hovered) {
            ImGui.setTooltip(tooltip);
        }

        ImGui.popFont();
    }

    private boolean playbackIcon(String icon, String tooltip, float buttonSize) {
        ImGui.pushFont(ReplayLabIcons.getFont());
        boolean res = ImGui.button(String.valueOf(icon), buttonSize, buttonSize);
        ImGui.popFont();

        ImGui.setItemTooltip(tooltip);
        ImGui.sameLine();
        return res;
    }

    private static String t(String key) {
        return Language.getInstance().get(key) + "###" + key;
    }

    private static String tt(String key) {
        return Language.getInstance().get(key);
    }
}
