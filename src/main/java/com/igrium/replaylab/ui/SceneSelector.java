package com.igrium.replaylab.ui;

import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SceneSelector {
    private static final Logger LOGGER = LoggerFactory.getLogger("ReplayLab/SceneSelector");

    private static final String POPUP_NAME = "Select Scene##ReplayLab";

    private final ImVec2 center = new ImVec2();
    private final ImBoolean isOpen = new ImBoolean();

    private int selectedSceneIdx = -1;

    private boolean wantsOpenPopup;

    public void open() {
        wantsOpenPopup = true;
    }

    public void render(ReplayLabEditorState editorState) {
        if (wantsOpenPopup) {
            ImGui.openPopup(POPUP_NAME);
            isOpen.set(true);
            wantsOpenPopup = false;
        }

        ImGui.getMainViewport().getCenter(center);
        ImGui.setNextWindowPos(center.x, center.y, ImGuiCond.Appearing, 0.5f, 0.5f);
        ImGui.setNextWindowSize(640, 480, ImGuiCond.FirstUseEver);

        boolean isSelectionValid = selectedSceneIdx >= 0 && selectedSceneIdx < editorState.getScenes().size();
        if (!isSelectionValid) {
            selectedSceneIdx = -1;
        }

        if (ImGui.beginPopupModal(POPUP_NAME, isOpen, ImGuiWindowFlags.NoSavedSettings)) {
            ImGui.beginChild("Scenes");
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());

            if (ImGui.beginListBox("##scenes")) {
                for (int i = 0; i < editorState.getScenes().size(); i++) {
                    boolean selected = i == selectedSceneIdx;
                    if (ImGui.selectable(editorState.getScenes().get(i), selected)) {
                        selectedSceneIdx = i;
                    }

                    if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
                        openScene(editorState, i);
                    }
                }
                ImGui.endListBox();
            }

            ImGui.endChild();

            ImGui.separator();
            ImGui.beginDisabled(!isSelectionValid);
            if (ImGui.button("Open") && isSelectionValid) {
                openScene(editorState, selectedSceneIdx);
            }
            ImGui.endDisabled();

            ImGui.sameLine();
            if (ImGui.button("Cancel")) {
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
        } else {
//            isOpen.set(false);
        }

    }

    private void openScene(ReplayLabEditorState editorState, int idx) {
        String sceneName = editorState.getScenes().get(idx);

        // Don't actually load the scene until the next frame.
        // Probably not a good idea to swap it in the middle of the UI rendering
        MinecraftClient.getInstance().executeTask(() -> {
            editorState.loadScene(sceneName);
        });
        ImGui.closeCurrentPopup();
    }
}
