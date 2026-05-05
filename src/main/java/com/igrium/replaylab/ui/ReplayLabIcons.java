package com.igrium.replaylab.ui;

import com.igrium.craftui.CraftUIFonts;
import imgui.ImFont;
import lombok.experimental.UtilityClass;
import net.minecraft.util.Identifier;

@UtilityClass
public class ReplayLabIcons {
    public static final Identifier FONT_ID = Identifier.of("replaylab:font-awesome");

    public static ImFont getFont() {
        return CraftUIFonts.getFont(FONT_ID);
    }

    public static final char ICON_VIDEOCAM = 0xF076;
    public static final char ICON_LOCK = 0xF023;
    public static final char ICON_LOCK_OPEN = 0xF3C1;
    public static final char ICON_PLAY = 0x25B6;
    public static final char ICON_STOP = 0x23F9;
    public static final char ICON_PAUSE = 0x23F8;
    public static final char ICON_TO_END = 0xF051;
    public static final char ICON_TO_END_ALT = 0x23ED;
    public static final char ICON_TO_START = 0xF048;
    public static final char ICON_TO_START_ALT = 0x23EE;
    public static final char ICON_FAST_FW = 0x23E9;
    public static final char ICON_FAST_BW = 0x23EA;
    public static final char ICON_RESIZE_SMALL = 0xF422;
    public static final char ICON_RESIZE_FULL = 0xF424;
    public static final char ICON_MAGNET = 0xF076;
    public static final char ICON_EYE_OFF = 0xF06E;
    public static final char ICON_EYE = 0xF070;
    public static final char ICON_RESIZE_FULL_ALT = 0xF424;
    public static final char ICON_EYEDROPPER = 0xF1FB;

}
