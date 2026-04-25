package com.igrium.replaylab.ui.util;

import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.editor.KeySelectionSet;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.key.KeyChannel;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.ui.ReplayLabIcons;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import lombok.experimental.UtilityClass;

import java.util.*;

@UtilityClass
public class ChannelList {
    /**
     * Draw the channel list
     * @param scene Scene to use
     * @param objs Object IDs to render
     * @return A collection of all objects which are currently expanded. Needed for the dope sheet.
     */
    public static Collection<String> drawChannelList(ReplayScene scene, Collection<? extends String> objs, int width) {
        Set<String> expandedObjs = new HashSet<>(objs.size());
        for (var name : objs) {
            ReplayObject obj = scene.getObject(name);
            if (obj == null)
                continue;

            Boolean setAllLocked = null;
            Boolean setAllHidden = null;

            boolean anyUnlocked = false;
            for (KeyChannel channel : obj.getChannels().values()) {
                if (!channel.isLocked()) {
                    anyUnlocked = true;
                    break;
                }
            }

            boolean anyVisible = false;
            for (KeyChannel channel : obj.getChannels().values()) {
                if (!channel.isHidden()) {
                    anyVisible = true;
                    break;
                }
            }

            boolean renderObjDisabled = !anyVisible || !anyUnlocked;

            if (renderObjDisabled) {
                ImGui.pushStyleColor(ImGuiCol.Text, ImGui.getColorU32(ImGuiCol.TextDisabled));
            }
            boolean open = ImGui.treeNodeEx(name);
            if (renderObjDisabled) {
                ImGui.popStyleColor();
            }

            // Global object toggles
            ImGui.pushStyleColor(ImGuiCol.Button, 0);
            ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0f, 0f);

            ImGui.sameLine();
            ImGui.setCursorPosX(ImGui.getCursorStartPosX() + width - ImGui.getFontSize() * 3.5f);
            boolean toggleObjVisible = ReplayLabControls.iconButton(anyVisible ? ReplayLabIcons.ICON_EYE : ReplayLabIcons.ICON_EYE_OFF,
                    name + "hide", null);

            if (toggleObjVisible) {
                setAllHidden = anyVisible;
            }

            ImGui.sameLine();
            boolean toggleObjLock = ReplayLabControls.iconButton(anyUnlocked ? ReplayLabIcons.ICON_LOCK_OPEN : ReplayLabIcons.ICON_LOCK,
                    name + "hide", null);

            if (toggleObjLock) {
                setAllLocked = anyUnlocked;
            }

            ImGui.popStyleColor();
            ImGui.popStyleVar();

            if (open) {
                for (var entry : obj.getChannels().entrySet()) {
                    boolean disable = entry.getValue().isHidden() || entry.getValue().isLocked();

                    if (disable) {
                        ImGui.beginDisabled();
                    }
                    ImGui.text(entry.getKey());
                    ImGui.sameLine();
                    if (disable) {
                        ImGui.endDisabled();
                    }

                    ImGui.setCursorPosX(ImGui.getCursorStartPosX() + width - ImGui.getFontSize() * 3.5f);

                    boolean ctrlPressed = ImGui.getIO().getKeyCtrl();

                    ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0f, 0f);
                    ImGui.pushStyleColor(ImGuiCol.Button, 0);
                    boolean wasHidden = entry.getValue().isHidden();
                    boolean toggleHidden = ReplayLabControls.iconButton(wasHidden ? ReplayLabIcons.ICON_EYE_OFF : ReplayLabIcons.ICON_EYE,
                            name + entry.getKey() + "hide", null);

                    if (toggleHidden) {
                        if (ctrlPressed) {
                            setAllHidden = !wasHidden;
                        } else {
                            entry.getValue().setHidden(!wasHidden);
                        }
                    }
                    ImGui.sameLine();

                    boolean wasLocked = entry.getValue().isLocked();
                    boolean toggleLock = ReplayLabControls.iconButton(wasLocked ? ReplayLabIcons.ICON_LOCK : ReplayLabIcons.ICON_LOCK_OPEN,
                            name + entry.getKey() + "lock", null);
                    if (toggleLock) {
                        if (ctrlPressed) {
                            setAllLocked = !wasLocked;
                        } else {
                            entry.getValue().setLocked(!wasLocked);
                        }
                    }
                    ImGui.popStyleVar();
                    ImGui.popStyleColor();
                }
                ImGui.treePop();
                expandedObjs.add(name);
            }

            if (setAllLocked != null || setAllHidden != null) {
                for (var ch : obj.getChannels().values()) {
                    if (setAllLocked != null) {
                        ch.setLocked(setAllLocked);
                    }
                    if (setAllHidden != null) {
                        ch.setHidden(setAllHidden);
                    }
                }
            }
        }
        return expandedObjs;
    }
}
