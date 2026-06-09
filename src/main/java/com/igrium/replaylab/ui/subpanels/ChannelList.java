package com.igrium.replaylab.ui.subpanels;

import com.igrium.craftui.MaterialIcons;
import com.igrium.replaylab.editor.KeySelectionSet;
import com.igrium.replaylab.editor.KeySelectionSet.ChannelReference;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.key.KeyChannel;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.ui.ReplayLabIcons;
import com.igrium.replaylab.ui.util.ReplayLabControls;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import it.unimi.dsi.fastutil.objects.Object2BooleanFunction;
import lombok.experimental.UtilityClass;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

@UtilityClass
public class ChannelList {
    /**
     * Draw the channel list
     *
     * @param scene Scene to use
     * @param objs  Object IDs to render
     * @return A collection of all objects which are currently expanded. Needed for the dope sheet.
     */
    public static Collection<String> drawChannelList(ReplayScene scene,
                                                     Map<? extends String, ? extends ReplayObject> objs, int width) {
        Set<String> expandedObjs = new HashSet<>(objs.size());

        // Channels which have "toggle visible" clicked (don't allocate until needed)
        Set<ChannelReference> toggleHiddenChannels = null;

        // Channels which have "toggle locked" clicked this frame
        Set<ChannelReference> toggleLockedChannels = null;
        for (var objEntry : objs.entrySet()) {
            String objName = objEntry.getKey();
            ReplayObject obj = objEntry.getValue();
            if (obj == null)
                continue;

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
            ImGui.alignTextToFramePadding();
            boolean open = ImGui.treeNodeEx(objName);
            if (renderObjDisabled) {
                ImGui.popStyleColor();
            }

            // Global object toggles
            ImGui.pushStyleColor(ImGuiCol.Button, 0);
            ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0f, 0f);

            ImGui.sameLine();
            ImGui.setCursorPosX(ImGui.getCursorStartPosX() + width - ImGui.getFontSize() * 3.5f);

            char objHideIcon = anyVisible ? MaterialIcons.ICON_VISIBILITY : MaterialIcons.ICON_VISIBILITY_OFF;
            boolean toggleObjHidden = ImGui.button( objHideIcon + "###obj." + objName + "hide");

            if (toggleObjHidden) {
                if (toggleHiddenChannels == null) toggleHiddenChannels = new HashSet<>();
                for (var chName : obj.getChannels().keySet()) {
                    toggleHiddenChannels.add(new ChannelReference(objName, chName));
                }
            }


            ImGui.sameLine();
            char objLockIcon = anyUnlocked ? MaterialIcons.ICON_LOCK_OPEN : MaterialIcons.ICON_LOCK;
            boolean toggleObjLock = ImGui.button(objLockIcon + "###obj." + objName + "lock");

            if (toggleObjLock) {
                if (toggleLockedChannels == null) toggleLockedChannels = new HashSet<>();
                for (var chName : obj.getChannels().keySet()) {
                    toggleLockedChannels.add(new ChannelReference(objName, chName));
                }
            }

            ImGui.popStyleColor();
            ImGui.popStyleVar();

            if (open) {
                for (var chEntry : obj.getChannels().entrySet()) {
                    boolean disable = chEntry.getValue().isHidden() || chEntry.getValue().isLocked();

                    if (disable) {
                        ImGui.beginDisabled();
                    }
                    ImGui.alignTextToFramePadding();
                    ImGui.text(chEntry.getKey());
                    ImGui.sameLine();
                    if (disable) {
                        ImGui.endDisabled();
                    }

                    ImGui.setCursorPosX(ImGui.getCursorStartPosX() + width - ImGui.getFontSize() * 3.5f);


                    ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0f, 0f);
                    ImGui.pushStyleColor(ImGuiCol.Button, 0);

                    boolean wasHidden = chEntry.getValue().isHidden();
                    char hiddenIcon = wasHidden ? MaterialIcons.ICON_VISIBILITY_OFF : MaterialIcons.ICON_VISIBILITY;
                    if (ImGui.button(hiddenIcon + "###" + objName + chEntry.getKey() + "hide")) {
                        if (toggleHiddenChannels == null) toggleHiddenChannels = new HashSet<>();
                        toggleHiddenChannels.add(new ChannelReference(objName, chEntry.getKey()));
                    }

                    ImGui.sameLine();

                    boolean wasLocked = chEntry.getValue().isLocked();
                    char lockIcon = wasLocked ? MaterialIcons.ICON_LOCK : MaterialIcons.ICON_LOCK_OPEN;
                    if (ImGui.button(lockIcon + "###" + objName + chEntry.getKey() + "lock")) {
                        if (toggleLockedChannels == null) toggleLockedChannels = new HashSet<>();
                        toggleLockedChannels.add(new ChannelReference(objName, chEntry.getKey()));
                    }

                    ImGui.popStyleVar();
                    ImGui.popStyleColor();
                }
                ImGui.treePop();
                expandedObjs.add(objName);
            }

        }

        if (toggleHiddenChannels != null) {
            updateBoolValue(objs, toggleHiddenChannels, ImGui.getIO().getKeyCtrl(),
                    KeyChannel::isHidden, KeyChannel::setHidden);
        }
        if (toggleLockedChannels != null) {
            updateBoolValue(objs, toggleLockedChannels, ImGui.getIO().getKeyCtrl(),
                    KeyChannel::isLocked, KeyChannel::setLocked);
        }

        return expandedObjs;
    }

    private static void updateBoolValue(Map<? extends String, ? extends ReplayObject> objs,
                                        Collection<ChannelReference> channels,
                                        boolean invert,
                                        Predicate<KeyChannel> getter,
                                        BiConsumer<KeyChannel, Boolean> setter) {

        // Not the fastest thing in the world, but it's only called when the user clicks a button.

        Collection<ChannelReference> finalChannels; // I hate lambda rules
        if (invert) {
            finalChannels = StreamSupport.stream(KeySelectionSet.iterateAllHandles(objs).spliterator(), false)
                    .map(handle -> handle.keyRef().channelRef())
                    .distinct()
                    .filter(v -> !channels.contains(v))
                    .toList();
//            newVal = !newVal;
        } else {
            finalChannels = channels;
        }

        int numTrue = 0;
        int numFalse = 0;

        for (var chRef : finalChannels) {
            KeyChannel ch = chRef.get(objs);
            if (ch == null) continue;

            if (getter.test(ch)) {
                numTrue++;
            } else {
                numFalse++;
            }
        }

        boolean newVal = numFalse > numTrue;

        for (var chRef : finalChannels) {
            KeyChannel ch = chRef.get(objs);
            if (ch == null) continue;
            setter.accept(ch, newVal);
        }
    }
}