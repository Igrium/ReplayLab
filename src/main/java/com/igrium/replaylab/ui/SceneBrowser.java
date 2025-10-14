package com.igrium.replaylab.ui;

import com.igrium.replaylab.editor.ReplayLabEditorState;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SceneBrowser {
    private static final Logger LOGGER = LoggerFactory.getLogger("ReplayLab/SceneSelector");

    private static final String POPUP_NAME = "Select Scene##ReplayLab";

    private final ImVec2 center = new ImVec2();
    private final ImBoolean isOpen = new ImBoolean();
    private final ImString sceneRenameStr = new ImString();

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

        float rightBarWidth = ImGui.getFontSize() * 6;

        if (ImGui.beginPopupModal(POPUP_NAME, isOpen, ImGuiWindowFlags.NoSavedSettings)) {
            ImGui.beginChild("Scenes", ImGui.getContentRegionAvailX() - rightBarWidth,
                    ImGui.getContentRegionAvailY() - (ImGui.getTextLineHeightWithSpacing() + ImGui.getStyle().getItemSpacingY() * 2) - 4);
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());

            if (ImGui.beginListBox("##scenes", ImGui.getContentRegionAvailX(), ImGui.getContentRegionAvailY())) {
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
            ImGui.sameLine();

            String selectedSceneName = isSelectionValid ? editorState.getScenes().get(selectedSceneIdx) : "";

            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            ImGui.beginGroup();
            if (ImGui.button("Add", ImGui.getContentRegionAvailX(), ImGui.getTextLineHeightWithSpacing())) {
                selectedSceneIdx = -1;
                sceneRenameStr.set("", false);
                ImGui.openPopup("###renameScene");
            }

            ImGui.beginDisabled(!isSelectionValid);
            if (ImGui.button("Rename", ImGui.getContentRegionAvailX(), ImGui.getTextLineHeightWithSpacing())) {
                sceneRenameStr.set(selectedSceneName);
                ImGui.openPopup("###renameScene");
            }

            ImGui.beginDisabled(selectedSceneName.equals(editorState.getSceneName()));
            if (ImGui.button("Delete", ImGui.getContentRegionAvailX(), ImGui.getTextLineHeightWithSpacing())) {
                ImGui.openPopup("Delete Scene?");
            }
            ImGui.endDisabled();
            ImGui.endDisabled();
            ImGui.endGroup();

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

            if (ImGui.beginPopupModal("Delete Scene?", ImGuiWindowFlags.AlwaysAutoResize | ImGuiWindowFlags.NoSavedSettings)) {
                ImGui.text("The scene may not be recoverable!");
                ImGui.separator();

                if (ImGui.button("OK")) {
                    ImGui.closeCurrentPopup();
                    editorState.removeScene(selectedSceneName);
                }
                ImGui.setItemDefaultFocus();
                ImGui.sameLine();
                if (ImGui.button("Cancel")) {
                    ImGui.closeCurrentPopup();
                }
                ImGui.endPopup();
            }

            // LOGIC: If there's a valid item selected, consider it a rename. Otherwise, make a new scene.
            ImGui.setNextWindowSize(300, 0);
            String windowName = isSelectionValid ? "Rename Scene" : "New Scene";
            boolean renameWantsGlobalClose = false;

            if (ImGui.beginPopupModal(windowName + "###renameScene", ImGuiWindowFlags.AlwaysAutoResize | ImGuiWindowFlags.NoSavedSettings)) {
                ImGui.text("Scene name");
                ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
                ImGui.inputText("##sceneName", sceneRenameStr);
                ImGui.setItemDefaultFocus();

                ImGui.separator();

                String name = sceneRenameStr.get();
                boolean nameValid = !editorState.getScenes().contains(name);

                ImGui.beginDisabled(!nameValid);
                if (ImGui.button(isSelectionValid ? "Rename" : "Create")) {
                    ImGui.closeCurrentPopup();

                    if (isSelectionValid) {
                        editorState.tryRenameScene(selectedSceneName, name);
                    } else {
                        editorState.newScene(name);
                        renameWantsGlobalClose = true;
                    }
                }
                if (!nameValid && ImGui.isItemHovered()) {
                    ImGui.setTooltip("Provided name is in use.");
                }

                ImGui.endDisabled();
                ImGui.sameLine();
                if (ImGui.button("Cancel")) {
                    ImGui.closeCurrentPopup();
                }
                ImGui.endPopup();
            }

            if (renameWantsGlobalClose) {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
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
