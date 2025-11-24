package com.igrium.replaylab.debug;

import imgui.ImGui;
import lombok.Getter;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class TimelineDebugScreen {
    @Getter
    private static final List<String> sampling = new ArrayList<>();

    public static void drawDebugScreen() {
        if (ImGui.begin("Timeline Debugger")) {
            if (ImGui.treeNode("Sampling")) {
                for (String idx : sampling) {
                    ImGui.bulletText(idx);
                }
                ImGui.treePop();
            }
        }
        ImGui.end();
        sampling.clear();
    }
}
