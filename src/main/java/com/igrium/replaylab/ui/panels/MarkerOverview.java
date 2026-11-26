package com.igrium.replaylab.ui.panels;

import com.igrium.craftui.api.icon.FontAwesome;
import com.igrium.craftui.api.style.CraftUIFonts;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.operator.scene.SetSceneStartOperator;
import com.igrium.replaylab.ui.util.ReplayLabControls;
import com.igrium.replaylab.util.Timestamps;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replaystudio.data.Marker;
import imgui.ImFont;
import imgui.ImGui;
import imgui.type.ImString;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;

import java.util.List;

public class MarkerOverview extends UIPanel {
    public MarkerOverview(Identifier id) {
        super(id, false);
    }

    private static final int RIGHT_BUTTONS_WIDTH_KEY = 69420; // Arbitrary value

    private final ImString nameCache = new ImString();
    private final int[] timeCache = new int[1];
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
        float timestampWidth = ImGui.calcTextSizeX("00:00:00");
        ImGui.popFont();

        List<Marker> markers = editorState.getReplayMarkers();

        ImGui.separator();
        if (markers.isEmpty()) {
            ImGui.textDisabled(tt("gui.replaylab.no_markers"));
            return;
        }

        if (ImGui.beginChild("##markerList", ImGui.getContentRegionAvailX(), ImGui.getContentRegionAvailY())) {

            int i = 0;

            for (Marker marker : editorState.getReplayMarkers()) {
                if (!query.isBlank() && !(marker.getName() != null && marker.getName().toLowerCase().contains(query))) {
                    continue;
                }

                ImGui.pushID("marker." + i++);

                ImGui.pushFont(timestampFont, 0);
                ImGui.textDisabled(Timestamps.toTimestamp(marker.getTime(), Timestamps.Display.SECONDS));
                ImGui.popFont();

                ImGui.sameLine(timestampWidth);

                float prevRightButtonsWidth = ImGui.getStateStorage().getFloat(RIGHT_BUTTONS_WIDTH_KEY);
                nameCache.set(marker.getName() != null ? marker.getName() : "");
                ImGui.setNextItemWidth(
                        Math.max(ImGui.getContentRegionAvailX() - prevRightButtonsWidth - ImGui.getStyle().getItemSpacingX(), 1));
                ImGui.inputTextWithHint("##name", tt("gui.replaylab.marker_name"), nameCache);

                ImGui.sameLine();
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

                }
                ImGui.endGroup();
                ImGui.getStateStorage().setFloat(RIGHT_BUTTONS_WIDTH_KEY, ImGui.getItemRectSizeX());

                ImGui.popID();
            }
        }
        ImGui.endChild();
    }

    private static String tt(String key) {
        return Language.getInstance().getOrDefault(key);
    }

    private static String t(String key) {
        return tt(key) + "###" + key;
    }
}
