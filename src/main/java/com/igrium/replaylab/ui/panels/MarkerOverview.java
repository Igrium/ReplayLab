package com.igrium.replaylab.ui.panels;

import com.igrium.craftui.api.icon.FontAwesome;
import com.igrium.craftui.api.style.CraftUIFonts;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.operator.ReplayOperator;
import com.igrium.replaylab.operator.replay.DeleteMarkerOperator;
import com.igrium.replaylab.operator.replay.MoveMarkerOperator;
import com.igrium.replaylab.operator.replay.RenameMarkerOperator;
import com.igrium.replaylab.operator.scene.SetSceneStartOperator;
import com.igrium.replaylab.ui.util.ReplayLabControls;
import com.igrium.replaylab.util.Timestamps;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replaystudio.data.Marker;
import imgui.ImFont;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;
import imgui.type.ImString;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class MarkerOverview extends UIPanel {
    public MarkerOverview(Identifier id) {
        super(id, false);
    }

    private static final int RIGHT_BUTTONS_WIDTH_KEY = 69420; // Arbitrary value


    private @Nullable Marker renamingMarker;
    private @Nullable Marker retimingMarker;
    private final ImString editBuffer = new ImString();
    /**
     * Whether the edit field has actually become active yet. Focus can't be granted on the first frame:
     * the double-click lands on a non-interactive label, which ImGui treats as starting a window move,
     * and SetKeyboardFocusHere is ignored until the mouse is released.
     */
    private boolean editActivated;


    private final ImString filter = new ImString();

    @Override
    protected void drawContents(EditorState editorState) {
        ReplayHandler replayHandler = EditorState.getReplayHandler();
        int replayLength = replayHandler != null ? replayHandler.getReplayDuration() : 0;

        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        ImGui.inputTextWithHint("##search", FontAwesome.ICON_MAGNIFYING_GLASS + "", filter);
        String query = filter.get().toLowerCase();

        ImFont timestampFont = CraftUIFonts.getFont(ReplayLabControls.ROBOTO_MONO);

        ImGui.pushFont(timestampFont, 0);
        float timestampWidth = ImGui.calcTextSizeX("0000.00");
        ImGui.popFont();
        // The retime field shows hh:mm:ss.mmm, which is wider than the seconds-only label
        float retimeFieldWidth = ImGui.calcTextSizeX("00:00:00.000") + ImGui.getStyle().getFramePaddingX() * 2;
        float timeColumnWidth = Math.max(timestampWidth, retimeFieldWidth) + ImGui.getStyle().getItemSpacingX();

        List<Marker> markers = editorState.getReplayMarkers();

        // Marker being edited was removed out from under us (delete, undo, etc.)
        if (renamingMarker != null && !markers.contains(renamingMarker)) renamingMarker = null;
        if (retimingMarker != null && !markers.contains(retimingMarker)) retimingMarker = null;

        ImGui.separator();
        if (markers.isEmpty()) {
            ImGui.textDisabled(tt("gui.replaylab.no_markers"));
            return;
        }

        if (ImGui.beginChild("##markerList", ImGui.getContentRegionAvailX(), ImGui.getContentRegionAvailY())) {

            for (int i = 0; i < markers.size(); i++) {
                Marker marker = markers.get(i);
                if (!query.isBlank() && !(marker.getName() != null && marker.getName().toLowerCase().contains(query))) {
                    continue;
                }

                ImGui.pushID(i);

                if (marker == retimingMarker) {
                    if (!editActivated) {
                        ImGui.setKeyboardFocusHere();
                    }
                    ImGui.setNextItemWidth(retimeFieldWidth);
                    ImGui.inputText("##time", editBuffer, ImGuiInputTextFlags.AutoSelectAll);
                    if (ImGui.isItemActive()) {
                        editActivated = true;
                    }
                    if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
                        retimingMarker = null;
                    } else if (editActivated && (ImGui.isKeyPressed(ImGuiKey.Enter) || !ImGui.isItemActive())) {
                        commitRetime(editorState);
                    }
                } else {
                    ImGui.pushFont(timestampFont, 0);
                    ImGui.textDisabled(Timestamps.toTimestamp(marker.getTime(), Timestamps.Display.SECONDS));
                    ImGui.popFont();

                    if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
                        beginRetiming(marker);
                    }
                }

                ImGui.sameLine(timeColumnWidth);

                float prevRightButtonsWidth = ImGui.getStateStorage().getFloat(RIGHT_BUTTONS_WIDTH_KEY);
                if (marker == renamingMarker) {
                    if (!editActivated) {
                        ImGui.setKeyboardFocusHere();
                    }

                    ImGui.setNextItemWidth(
                            Math.max(ImGui.getContentRegionAvailX() - prevRightButtonsWidth - ImGui.getStyle().getItemSpacingX(), 1));

                    ImGui.inputText("##name", editBuffer, ImGuiInputTextFlags.AutoSelectAll);
                    if (ImGui.isItemActive()) {
                        editActivated = true;
                    }
                    if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
                        renamingMarker = null;
                    } else if (editActivated && (ImGui.isKeyPressed(ImGuiKey.Enter) || !ImGui.isItemActive())) {
                        commitRename(editorState);
                    }
                } else {
                    if (marker.getName() != null) {
                        ImGui.text(marker.getName());
                    } else {
                        ImGui.textDisabled(tt("gui.replaylab.marker.unnamed"));
                    }

                    if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
                        beginRenaming(marker);
                    }
                }

                ImGui.sameLine(ImGui.getContentRegionAvailX() - prevRightButtonsWidth);
                ImGui.beginGroup();
                {
                    int sceneTime = editorState.getScene().replayToSceneTime(marker.getTime());
                    boolean oob = sceneTime < 0 || sceneTime > editorState.getScene().getLength();

                    ImGui.beginDisabled(oob);
                    if (ImGui.button(t("gui.replaylab.marker.goto")) && !oob) {
                        editorState.setPlayhead(sceneTime);
                        editorState.snapViewportTo(marker.getX(), marker.getY(), marker.getZ(), marker.getYaw(), marker.getPitch());
                    }
                    if (oob) {
                        ImGui.setItemTooltip(tt("gui.replaylab.marker.oob"));
                    }
                    ImGui.endDisabled();

                    ImGui.sameLine();
                    if (ImGui.button(t("gui.replaylab.marker.set_start"))) {
                        editorState.applyOperator(new SetSceneStartOperator(marker.getTime(), false));
                    }

                    ImGui.sameLine();
                    if (ImGui.button(FontAwesome.ICON_TRASH_CAN + "")) {
                        editorState.scheduleOperator(new DeleteMarkerOperator(marker));
                    }
                }
                ImGui.endGroup();
                ImGui.getStateStorage().setFloat(RIGHT_BUTTONS_WIDTH_KEY, ImGui.getItemRectSizeX());

                ImGui.popID();
            }
        }
        ImGui.endChild();

    }

    private void beginRenaming(Marker marker) {
        retimingMarker = null;
        renamingMarker = marker;
        editBuffer.set(marker.getName() != null ? marker.getName() : "");
        editActivated = false;
    }

    private void beginRetiming(Marker marker) {
        renamingMarker = null;
        retimingMarker = marker;
        // Must round-trip through Timestamps.fromTimestamp. Display.MILLISECONDS gives a bare
        // number, which fromTimestamp parses as seconds (1000x too late).
        editBuffer.set(Timestamps.toTimestamp(marker.getTime(), 3, Timestamps.Display.HOURS));
        editActivated = false;
    }

    private void commitRename(EditorState editorState) {
        Marker marker = renamingMarker;
        renamingMarker = null;
        if (marker == null) return;

        String newName = editBuffer.get().strip();
        if (newName.isEmpty()) newName = null;

        if (!Objects.equals(newName, marker.getName())) {
            editorState.scheduleOperator(new RenameMarkerOperator(marker, newName));
        }
    }

    private void commitRetime(EditorState editorState) {
        Marker marker = retimingMarker;
        retimingMarker = null;
        if (marker == null) return;

        int newTime;
        try {
            newTime = Timestamps.fromTimestamp(editBuffer.get());
        } catch (NumberFormatException e) {
            return;
        }

        newTime = Math.max(newTime, 0);

        if (newTime != marker.getTime()) {
            editorState.scheduleOperator(new MoveMarkerOperator(marker, marker.getTime(), newTime));
        }
    }

    private static String tt(String key) {
        return Language.getInstance().getOrDefault(key);
    }

    private static String t(String key) {
        return tt(key) + "###" + key;
    }
}
