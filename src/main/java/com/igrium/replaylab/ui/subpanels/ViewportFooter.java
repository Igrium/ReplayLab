package com.igrium.replaylab.ui.subpanels;

import com.igrium.craftui.CraftUIFonts;
import com.igrium.craftui.icon.FontAwesome;
import com.igrium.replaylab.config.ReplayLabConfig;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.mixin.AccessorHud;
import com.igrium.replaylab.ui.util.ReplayLabControls;
import com.igrium.replaylab.util.Timestamps;
import imgui.ImGui;
import imgui.flag.ImGuiMouseCursor;
import imgui.type.ImBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.locale.Language;
import net.minecraft.util.Util;

public class ViewportFooter {

    private float prevCameraControlsGroupWidth;
    private float prevPlaybackControlsWidth;

    private long clipboardCopyTime;

    private final ImBoolean cameraViewInput = new ImBoolean();
    private final ImBoolean tmpBoolean = new ImBoolean();

    public void drawPlaybackControls(EditorState editorState) {
        float buttonSize = ImGui.getTextLineHeightWithSpacing() * 1.25f;
        float viewportFooterHeight = buttonSize + ImGui.getStyle().getWindowPaddingY() * 2;

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

        timestampView("##replayTime", editorState.getSceneTime(), tt("gui.replaylab.replay_time"));
        ImGui.sameLine();

        if (playbackIcon(FontAwesome.ICON_BACKWARD_FAST +
                "##sceneStart", tt("key.replaylab.scene_start"), buttonSize)) {
            editorState.jumpSceneStart();
        }
        playbackIcon(FontAwesome.ICON_BACKWARD_STEP + "##prevKey", tt("key.replaylab.prev_key"), buttonSize);

        char playPauseIcon = editorState.isPlaying() ? FontAwesome.ICON_PAUSE : FontAwesome.ICON_PLAY;
        if (playbackIcon(playPauseIcon + "##playPause", tt("key.replaylab.playpause"), buttonSize)) {
            editorState.togglePlayback();
        }

        playbackIcon(FontAwesome.ICON_FORWARD_STEP + "##nextKey", tt("key.replaylab.next_key"), buttonSize);
        if (playbackIcon(FontAwesome.ICON_FORWARD_FAST + "##sceneEnd", tt("key.replaylab.scene_end"), buttonSize)) {
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
        if (ReplayLabControls.toggleButton(FontAwesome.ICON_GROUP_ARROWS_ROTATE, "gizmoAll", tmpBoolean,
                "key.replaylab.gizmo_all")) {
            editorState.showGizmoPos(tmpBoolean.get());
            editorState.showGizmoRot(tmpBoolean.get());
            editorState.showGizmoScale(tmpBoolean.get());
        }
        ImGui.sameLine();
        tmpBoolean.set(editorState.showGizmoPos());
        if (ReplayLabControls.toggleButton(FontAwesome.ICON_ARROWS_UP_DOWN_LEFT_RIGHT, "gizmoPos", tmpBoolean,
                "key.replaylab.gizmo_pos")) {
            editorState.showGizmoPos(tmpBoolean.get());
            if (!ImGui.getIO().getKeyCtrl()) {
                editorState.showGizmoRot(false);
                editorState.showGizmoScale(false);
            }
        }
        ImGui.sameLine();
        tmpBoolean.set(editorState.showGizmoRot());
        if (ReplayLabControls.toggleButton(FontAwesome.ICON_ROTATE, "gizmoRot", tmpBoolean,
                "key.replaylab.gizmo_rot")) {
            editorState.showGizmoRot(tmpBoolean.get());
            if (!ImGui.getIO().getKeyCtrl()) {
                editorState.showGizmoPos(false);
                editorState.showGizmoScale(false);
            }
        }
        ImGui.sameLine();
        tmpBoolean.set(editorState.showGizmoScale());
        if (ReplayLabControls.toggleButton(FontAwesome.ICON_ARROWS_UP_DOWN_LEFT_RIGHT, "gizmoScale", tmpBoolean,
                "key.replaylab.gizmo_scale")) {
            editorState.showGizmoScale(tmpBoolean.get());
            if (!ImGui.getIO().getKeyCtrl()) {
                editorState.showGizmoPos(false);
                editorState.showGizmoRot(false);
            }
        }
        ImGui.sameLine();
        tmpBoolean.set(editorState.isLocalGizmos());
        char localIcon = tmpBoolean.get() ? FontAwesome.ICON_CUBE : FontAwesome.ICON_GLOBE;
        if (ReplayLabControls.toggleButton(localIcon, "freeTransform", tmpBoolean,
                "key.replaylab.local_transforms")) {
            editorState.setLocalGizmos(tmpBoolean.get());
        }
        ImGui.sameLine();
        cameraViewInput.set(editorState.isCameraView());
        if (ReplayLabControls.toggleButton(FontAwesome.ICON_VIDEO, "cameraView", cameraViewInput,
                "key.replaylab.cameraview")) {
            editorState.setCameraView(cameraViewInput.get());
        }
        ImGui.sameLine();
        Hud hud = Minecraft.getInstance().gui.hud;
        tmpBoolean.set(!hud.isHidden());
        if (ReplayLabControls.toggleButton(FontAwesome.ICON_TV, "hideWidgets", tmpBoolean, "key.replaylab.hide_widgets")) {
            ((AccessorHud) hud).setIsHidden(!tmpBoolean.get());
        }


        ImGui.endGroup();
        prevCameraControlsGroupWidth = ImGui.getItemRectSizeX();

        ImGui.endChild();
    }

    private void timestampView(String id, int timestamp, String tooltip) {
        ImGui.beginGroup();

        ImGui.pushFont(CraftUIFonts.getFont(ReplayLabControls.ROBOTO_MONO), 0);
        String str = Timestamps.toTimestamp(timestamp, 3, ReplayLabConfig.getInstance().getTimestampMode());
        ImGui.text(str);
        ImGui.popFont();


        float rectMinX = ImGui.getItemRectMinX();
        float rectMinY = ImGui.getItemRectMinY();
        float rectSizeX = ImGui.getItemRectSizeX();
        float rectSizeY = ImGui.getItemRectSizeY();

        ImGui.setCursorScreenPos(rectMinX, rectMinY);
        if (ImGui.invisibleButton(id, rectSizeX, rectSizeY)) {
            clipboardCopyTime = Util.getMillis();
            ImGui.setClipboardText(str);
        }

        boolean hovered = ImGui.isItemHovered();

        // The invisible button occupies the text's exact rect, so it already leaves the cursor where
        // the text did -- restoring it by hand would only trip ImGui 1.92's EndGroup assertion, which
        // fires when SetCursorPos is the last thing a group does.
        ImGui.endGroup();

        if (hovered) {
            ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
        }
        if (Util.getMillis() - clipboardCopyTime < 1000) {
            ImGui.setTooltip(tt("gui.replaylab.clipboarded"));
        } else {
            ImGui.setItemTooltip(tooltip);
        }

    }

    private boolean playbackIcon(String icon, String tooltip, float buttonSize) {
        boolean res = ImGui.button(String.valueOf(icon), buttonSize, buttonSize);

        ImGui.setItemTooltip(tooltip);
        ImGui.sameLine();
        return res;
    }

    private static String t(String key) {
        return Language.getInstance().getOrDefault(key) + "###" + key;
    }

    private static String tt(String key) {
        return Language.getInstance().getOrDefault(key);
    }
}
