package com.igrium.replaylab.ui.util;

import com.mojang.blaze3d.systems.RenderSystem;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Displays a modal with an error message.
 */
public class ExceptionPopup {

    private static final String POPUP_NAME = "Exception thrown:";

    private final Queue<Throwable> exeptionQueue = new ConcurrentLinkedQueue<>();

    @Nullable
    private Throwable currentException;

    @Nullable
    private String stackTrace;

    /**
     * Display an exception. May be called on any thread.
     * @param e Exception to display.
     */
    public void displayException(@NonNull Throwable e) {
        exeptionQueue.add(e);
    }

    private final ImVec2 center = new ImVec2();
    private final ImBoolean isOpen = new ImBoolean();
    public void render() {
        RenderSystem.assertOnRenderThread();
        boolean firstFrame = false;

        if (currentException == null) {
            currentException = exeptionQueue.poll();
            firstFrame = true;
        }
        if (currentException == null) {
            return;
        }

        if (firstFrame) {
            ImGui.openPopup(POPUP_NAME);
            isOpen.set(true);
        }


        ImGui.getMainViewport().getCenter(center);
        ImGui.setNextWindowPos(center.x, center.y, ImGuiCond.Appearing, 0.5f, 0.5f);

        if (ImGui.beginPopupModal(POPUP_NAME, isOpen, ImGuiWindowFlags.NoSavedSettings)) {
            ImGui.text(currentException.getLocalizedMessage());
            ImGui.separator();

            if (ImGui.beginChild("stacktrace", ImGui.getContentRegionAvailX(),
                    ImGui.getContentRegionAvailY() - (ImGui.getTextLineHeightWithSpacing() + ImGui.getStyle().getItemSpacingY() * 2))) {
                if (stackTrace == null) {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);

                    currentException.printStackTrace(pw);
                    stackTrace = sw.toString();
                }
                ImGui.text(stackTrace);
            }
            ImGui.endChild();

            if (ImGui.button("Close")) {
                ImGui.closeCurrentPopup();
            }
            ImGui.setItemDefaultFocus();
            ImGui.endPopup();
        } else {
            currentException = null;
            stackTrace = null;
            isOpen.set(false);
        }

    }

}
