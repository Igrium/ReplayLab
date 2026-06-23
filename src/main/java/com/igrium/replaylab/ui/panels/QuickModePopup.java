package com.igrium.replaylab.ui.panels;


import com.igrium.replaylab.ui.util.QuickModeInitCallback;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import lombok.Getter;
import lombok.Setter;

public class QuickModePopup implements QuickModeInitCallback {
    @Getter
    private float progress;

    @Getter @Setter
    private boolean isOpen;


    @Override
    public void openPopup() {
        isOpen = true;
    }

    @Override
    public void closePopup() {
        isOpen = false;
    }

    @Override
    public void onProgress(float progress) {
        this.progress = progress;
    }

    public void render() {
        if (!isOpen) return;

        ImGui.beginPopup("quickMode");
        if (ImGui.beginPopupModal("quickMode", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("Initializing Quick Mode");
            ImGui.progressBar(progress);
            ImGui.endPopup();
        }
    }
}
