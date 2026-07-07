package com.igrium.replaylab.ui;

import imgui.ImGui;
import lombok.experimental.UtilityClass;
import net.minecraft.util.Language;
import net.minecraft.util.Util;

@UtilityClass
public class NoFFmpegPopup {

    private static final String LINK = "https://www.replaymod.com/docs/#installing-ffmpeg";

    private static String tt(String key) {
        return Language.getInstance().get(key);
    }

    private static String t(String key) {
        return Language.getInstance().get(key) + "###" + key;
    }

    /**
     * Open a URL in the system's default browser.
     * @param url URL to open.
     */
    private static void openURL(String url) {
        // TODO: implement
    }

    public static void render() {
        ImGui.text(tt("replaymod.gui.rendering.error.message"));
        ImGui.text(LINK);

//        if (ImGui.button(t("chat.link.open"))) {
//            Util.getOperatingSystem().open(LINK);
//        }
//
//        ImGui.sameLine();
//        if (ImGui.button(t("chat.copy"))) {
//            ImGui.setClipboardText(LINK);
//        }
    }
}
