package com.igrium.replaylab.ui.subpanels;

import com.igrium.craftui.MaterialIcons;
import com.igrium.replaylab.editor.KeySelectionSet;
import com.igrium.replaylab.editor.KeySelectionSet.ChannelReference;
import com.igrium.replaylab.scene.key.KeyChannel;
import com.igrium.replaylab.scene.obj.ReplayObject;
import imgui.ImColor;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTreeNodeFlags;
import lombok.experimental.UtilityClass;
import net.minecraft.util.math.ColorHelper;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

@UtilityClass
public class ChannelList {

    private static final int SELECTED_COLOR = 0xFF22A2EB;

    /**
     * Draw the channel list
     *
     * @param selection Selected keyframes
     * @param objs      Object IDs to render
     * @return A collection of all objects which are currently expanded. Needed for the dope sheet.
     */
    public static Collection<String> drawChannelList(KeySelectionSet selection, Map<
            ? extends String, ? extends ReplayObject> objs, int width, int flags) {
        Set<String> expandedObjs = new HashSet<>(objs.size());

        // Channels which have "toggle visible" clicked (don't allocate until needed)
        Set<ChannelReference> toggleHiddenChannels = null;

        // Channels which have "toggle locked" clicked this frame
        Set<ChannelReference> toggleLockedChannels = null;
        for (var objEntry : objs.entrySet()) {
            String objName = objEntry.getKey();
            ReplayObject obj = objEntry.getValue();
            if (obj == null || obj.getType().hideInDopeSheet())
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
            boolean open = ImGui.treeNodeEx(obj.getDisplayName() + "###" + objName);
            if (renderObjDisabled) {
                ImGui.popStyleColor();
            }

            // Global object toggles
            ImGui.pushStyleColor(ImGuiCol.Button, 0);
            ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0f, 0f);

            ImGui.sameLine();
            ImGui.setCursorPosX(ImGui.getCursorStartPosX() + width - ImGui.getFontSize() * 3.5f);

            if (hasFlag(flags, ChannelListFlags.SHOW_HIDE)) {
                char objHideIcon = anyVisible ? MaterialIcons.ICON_VISIBILITY : MaterialIcons.ICON_VISIBILITY_OFF;
                boolean toggleObjHidden = ImGui.button(objHideIcon + "###obj." + objName + "hide");
                if (toggleObjHidden) {
                    if (toggleHiddenChannels == null) toggleHiddenChannels = new HashSet<>();
                    for (var chName : obj.getChannels().keySet()) {
                        toggleHiddenChannels.add(new ChannelReference(objName, chName));
                    }
                }
                ImGui.sameLine();
            }

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

                    float cursorX = ImGui.getCursorScreenPosX();
                    float cursorY = ImGui.getCursorScreenPosY();

                    boolean selected = hasFlag(flags, ChannelListFlags.HIGHLIGHT_SELECTION)
                            && selection.isChannelSelected(objName, chEntry.getKey());
                    if (selected) {
                        ImGui.pushStyleColor(ImGuiCol.Text, SELECTED_COLOR);
                    }

                    if (ImGui.treeNodeEx(chEntry.getKey(), ImGuiTreeNodeFlags.Leaf)) {
                        ImGui.treePop();
                    }
                    if (selected) {
                        ImGui.popStyleColor();
                    }
                    // CLICKED ON CHANNEL
                    if (hasFlag(flags, ChannelListFlags.ALLOW_SELECTION) && ImGui.isItemClicked()) {
                        if (!ImGui.getIO().getKeyCtrl()) {
                            selection.deselectAll();
                        }
                        selection.selectChannel(objName, chEntry.getKey(),
                                chEntry.getValue().getKeyframes().size());
                    }
                    ImGui.sameLine();
                    if (disable) {
                        ImGui.endDisabled();
                    }

                    ImGui.setCursorPosX(ImGui.getCursorStartPosX() + width - ImGui.getFontSize() * 3.5f);

                    ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0f, 0f);
                    ImGui.pushStyleColor(ImGuiCol.Button, 0);

                    if (hasFlag(flags, ChannelListFlags.SHOW_HIDE)) {
                        boolean wasHidden = chEntry.getValue().isHidden();
                        char hiddenIcon = wasHidden ? MaterialIcons.ICON_VISIBILITY_OFF : MaterialIcons.ICON_VISIBILITY;
                        if (ImGui.button(hiddenIcon + "###" + objName + chEntry.getKey() + "hide")) {
                            if (toggleHiddenChannels == null) toggleHiddenChannels = new HashSet<>();
                            toggleHiddenChannels.add(new ChannelReference(objName, chEntry.getKey()));
                        }

                        ImGui.sameLine();
                    }

                    boolean wasLocked = chEntry.getValue().isLocked();
                    char lockIcon = wasLocked ? MaterialIcons.ICON_LOCK : MaterialIcons.ICON_LOCK_OPEN;
                    if (ImGui.button(lockIcon + "###" + objName + chEntry.getKey() + "lock")) {
                        if (toggleLockedChannels == null) toggleLockedChannels = new HashSet<>();
                        toggleLockedChannels.add(new ChannelReference(objName, chEntry.getKey()));
                    }

                    // Color indicator
                    if (hasFlag(flags, ChannelListFlags.SHOW_COLORS)) {
                        ImDrawList drawList = ImGui.getWindowDrawList();
                        drawList.addRectFilled(cursorX, cursorY + 2, cursorX + 8, cursorY + ImGui.getFrameHeight() - 2,
                                ColorHelper.withAlpha(96, obj.getChannelColor(chEntry.getKey())));

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
                                        Collection<ChannelReference> channels, boolean solo,
                                        Predicate<KeyChannel> getter, BiConsumer<KeyChannel, Boolean> setter) {

        // Not the fastest thing in the world, but it's only called when the user shows/hides something
        if (solo) {
            List<ChannelReference> otherRefs = StreamSupport
                    .stream(KeySelectionSet.iterateAllHandles(objs).spliterator(), false)
                    .map(handle -> handle.keyRef().channelRef())
                    .distinct()
                    .filter(ref -> !channels.contains(ref))
                    .toList();

            boolean allOthersHidden = true;
            for (var otherRef : otherRefs) {
                KeyChannel other = otherRef.get(objs);
                if (other == null) continue;
                if (!getter.test(other)) {
                    allOthersHidden = false;
                    break;
                }
            }

            // If all other channels are hidden, show everything
            if (allOthersHidden) {
                for (var obj : objs.values()) {
                    for (var chan : obj.getChannels().values()) {
                        setter.accept(chan, false);
                    }
                }
            } else {
                for (var otherRef : otherRefs) {
                    KeyChannel other = otherRef.get(objs);
                    if (other != null) setter.accept(other, true);
                }
                for (var chanRef : channels) {
                    KeyChannel chan = chanRef.get(objs);
                    if (chan != null) setter.accept(chan, false);
                }
            }

        } else {
            // TOGGLE MODE: Smart toggle based on the majority state.
            int numTrue = 0, numFalse = 0;
            List<KeyChannel> validChannels = new ArrayList<>();

            for (ChannelReference chRef : channels) {
                KeyChannel ch = chRef.get(objs);
                if (ch != null) {
                    validChannels.add(ch);
                    if (getter.test(ch)) numTrue++;
                    else numFalse++;
                }
            }

            boolean newVal = numFalse > numTrue;
            for (KeyChannel ch : validChannels) {
                setter.accept(ch, newVal);
            }
        }
    }

    private static boolean hasFlag(int flags, int flag) {
        return (flags & flag) != 0;
    }
}