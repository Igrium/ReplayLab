package com.igrium.replaylab.ui.panels;

import com.igrium.replaylab.anim.InterpolationMode;
import com.igrium.replaylab.anim.KeyChannel;
import com.igrium.replaylab.anim.Keyframe;
import com.igrium.replaylab.anim.Keyframe.HandleType;
import com.igrium.replaylab.config.Keybinds;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.editor.KeySelectionSet.ChannelReference;
import com.igrium.replaylab.editor.KeySelectionSet.KeyHandleReference;
import com.igrium.replaylab.editor.KeySelectionSet.KeyframeReference;
import com.igrium.replaylab.operator.keyframe.RemoveKeyframesOperator;
import com.igrium.replaylab.operator.keyframe.SetHandleTypeOperator;
import com.igrium.replaylab.operator.keyframe.SetInterpModeOperator;
import com.igrium.replaylab.operator.keyframe.SetKeyPosOperator;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.obj.ObjectEditState;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.ui.subpanels.ChannelList;
import com.igrium.replaylab.ui.subpanels.ChannelListFlags;
import com.igrium.replaylab.ui.subpanels.CurveModifierEditor;
import com.igrium.replaylab.ui.subpanels.TimelineHeader;
import com.igrium.replaylab.ui.util.ReplayLabControls;
import imgui.ImGui;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImBoolean;
import imgui.type.ImDouble;
import imgui.type.ImInt;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;

import java.util.*;

/**
 * A panel designed to render keyframes (auto-listens to hotkeys, etc)
 */
public abstract class KeyframePanel extends UIPanel {

    private static final float MAX_ZOOM_X = 64f;
    private static final float MAX_ZOOM_Y = 1024f;

    @Getter
    private final TimelineHeader header = new TimelineHeader();

    /**
     * All the channels which are currently expanded in the channel list
     */
    @Getter
    private Collection<String> expandedChannels = Collections.emptySet();

    @Getter @Setter
    private double offsetX;


    @Getter @Setter
    private double offsetY;

    /**
     * The amount of pixels per millisecond
     */
    @Getter
    private float zoomFactorX = 0.1f;

    @Getter @Setter
    private boolean separateChannelScrolling;

    @Getter
    private final ImBoolean selectedOnlyRef = new ImBoolean(true);

    public boolean isSelectedOnly() {
        return selectedOnlyRef.get();
    }

    public void setSelectedOnly(boolean selectedOnly) {
        selectedOnlyRef.set(selectedOnly);
    }

    public void setZoomFactorX(float zoomFactorX) {
        this.zoomFactorX = Math.clamp(zoomFactorX, 0 , MAX_ZOOM_X);
    }

    public final void setZoomFactorX(float zoomFactorX, double center) {
        zoomFactorX = Math.clamp(zoomFactorX, 0, MAX_ZOOM_X);
        if (zoomFactorX == this.zoomFactorX) return;

        double newOffset = center - (center - offsetX) * (this.zoomFactorX / zoomFactorX);
        setZoomFactorX(zoomFactorX);
        setOffsetX(newOffset);
    }

    @Getter @Setter
    private float zoomFactorY = 0.1f;
    public final void setZoomFactorY(float zoomFactorY, double center) {
        double newOffset = center - (center - offsetY) * (this.zoomFactorY / zoomFactorY);
        setZoomFactorY(zoomFactorY);
        setOffsetY(newOffset);
    }

    protected int channelListFlags = ChannelListFlags.ALLOW_SELECTION | ChannelListFlags.SHOW_HIDE;

    /**
     * Every ms timestamp that has a keyframe on it, as of the last time {@link #drawInternal} ran.
     * Subclasses should clear and repopulate this each time they draw their keyframes; it's used
     * to snap the playhead to keyframes in the header.
     */
    protected final IntSet keyTimes = new IntOpenHashSet();

    private float cachedContentHeight = 100f;

    public KeyframePanel(Identifier id) {
        super(id);
    }

    /**
     * Flags passed to the header when it's drawn. Override to customize snapping behavior.
     */
    protected int getHeaderFlags() {
        return 0;
    }

    public final boolean isScrubbing() {
        return header.isScrubbing();
    }

    public final boolean stoppedScrubbing() {
        return header.stoppedScrubbing();
    }

    /**
     * Called when a modifier has been updated with <code>RESAMPLE</code>
     */
    protected void onUpdateMods() {};

    @Override
    protected void drawContents(EditorState editorState) {
        int maxEditState = ObjectEditState.NONE;
        ReplayScene scene = editorState.getScene();

        Map<String, ReplayObject> objs;
        if (isSelectedOnly()) {
            objs = new HashMap<>(scene.getObjects().size());
            for (var entry : scene.getObjects().entrySet()) {
                if (editorState.isObjectSelected(entry.getKey())) {
                    objs.put(entry.getKey(), entry.getValue());
                }
            }
        } else {
            objs = scene.getObjects();
        }

        // Don't allocate unless we need it
        Object2IntMap<String> editStates = null;

        List<ChannelReference> channels;
        // Shortcut if no keys selected
        if (editorState.getKeySelection().isEmpty()) {
            channels = List.of();
        } else {
            channels = editorState.getKeySelection()
                    .streamSelectedChannelRefs()
                    .filter(chRef -> objs.containsKey(chRef.objectName()))
                    .toList();
        }

        int flags = ImGuiTableFlags.Resizable;
        if (!separateChannelScrolling) {
            flags |= ImGuiTableFlags.ScrollY;
        }
        if (ImGui.beginTable("panelTable", 3, flags, ImGui.getContentRegionAvailX(), ImGui.getContentRegionAvailY())) {

            ImGui.tableSetupColumn("##channels", ImGuiTableColumnFlags.WidthFixed);
            ImGui.tableSetupColumn("##contents", ImGuiTableColumnFlags.WidthStretch);
            ImGui.tableSetupColumn("##mods", ImGuiTableColumnFlags.WidthFixed);

            ImGui.tableSetupScrollFreeze(0, 1);


            // Control buttons
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            drawControlButtons(editorState);

            // Header
            ImGui.tableNextColumn();
            header.drawHeader(editorState, ImGui.getTextLineHeight() * 2f, getZoomFactorX(), (float) getOffsetX(),
                    editorState.getScene().getLength(), editorState.getPlayheadRef(), cachedContentHeight,
                    keyTimes.toIntArray(), getHeaderFlags());

            // Modifiers header
            ImGui.tableNextColumn();

            ChannelReference selChanRef = channels.size() == 1 ? channels.getFirst() : null;
            KeyChannel selChannel = selChanRef != null ? selChanRef.get(objs) : null;

            int modEditState = CurveModifierEditor.drawHeader(editorState, selChanRef, selChannel);
            int chanEditState = modEditState;

            // Channel list
            ImGui.tableNextRow();
            float rowStartY = ImGui.getCursorPosY();
            ImGui.tableNextColumn();

            if (separateChannelScrolling && ImGui.beginChild("channels",
                    ImGui.getContentRegionAvailX(), ImGui.getContentRegionAvailY())) {
                expandedChannels = ChannelList.drawChannelList(editorState.getKeySelection(), objs,
                        ImGui.getContentRegionAvailX(), channelListFlags);
                ImGui.endChild();
            } else {
                expandedChannels = ChannelList.drawChannelList(editorState.getKeySelection(), objs,
                        ImGui.getContentRegionAvailX(), channelListFlags);
            }

            // Main
            ImGui.tableNextColumn();
            drawInternal(editorState, objs);

            // Modifiers
            ImGui.tableNextColumn();

            if (ImGui.beginChild("modifiers", ImGui.getContentRegionAvailX(), ImGui.getContentRegionAvailY())) {

                modEditState = CurveModifierEditor.drawEditor(editorState, selChanRef, selChannel);
                chanEditState |= modEditState;
                ImGui.endChild();
            }

            if (selChanRef != null && chanEditState != ObjectEditState.NONE) {
                ObjectEditState.handleUpdate(editorState, objs.get(selChanRef.objectName()), chanEditState);
            }

            if (hasFlag(modEditState, ObjectEditState.RESAMPLE)) {
                onUpdateMods();
            }

            ImGui.endTable();
            cachedContentHeight = ImGui.getCursorPosY() - rowStartY;
        }

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

    protected abstract void drawControlButtons(EditorState editorState);
    protected abstract void drawInternal(EditorState editorState, Map<String, ReplayObject> objects);

    private static final ImInt tsIn = new ImInt();
    private static final ImDouble doubleIn = new ImDouble();

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

        List<KeyframeReference> selKeys = selHandles.stream()
                .map(KeyHandleReference::keyRef)
                .distinct()
                .toList();

        if (ImGui.beginMenu(t("gui.replaylab.interp_mode"))) {

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

        if (selKeys.size() == 1) {
            KeyframeReference keyRef = selKeys.getFirst();
            Keyframe key = keyRef.get(editor.getScene().getObjects());
            if (key != null) {
                tsIn.set(key.getTimeInt());
                doubleIn.set(key.getValue());

                ReplayLabControls.inputTimestamp("Time", tsIn);
                if (ImGui.isItemDeactivatedAfterEdit()) {
                    editor.applyOperator(new SetKeyPosOperator(keyRef, tsIn.get(), key.getValue()));
                }

                ImGui.inputDouble("Value", doubleIn);
                if (ImGui.isItemDeactivatedAfterEdit()) {
                    editor.applyOperator(new SetKeyPosOperator(keyRef, key.getTime(), doubleIn.get()));
                }
            }
        }
    }


    private static boolean hasFlag(int flags, int flag) {
        return (flags & flag) != 0;
    }
    private static String t(String key) {
        return Language.getInstance().get(key) + "###" + key;
    }
}
