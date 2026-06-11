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

    public static final char ICON_VIDEOCAM = 0xF03D;

    public static final char ICON_PLAY = 0x25B6;

    public static final char ICON_PAUSE = 0x23F8;
    public static final char ICON_TO_END = 0xF051;
    public static final char ICON_TO_END_ALT = 0x23ED;
    public static final char ICON_TO_START = 0xF048;
    public static final char ICON_TO_START_ALT = 0x23EE;

    public static final char ICON_MAGNET = 0xF076;

    public static final char ICON_RESIZE_FULL_ALT = 0xF424;

    public static final char ICON_GLOBE = 0xF0AC;
    public static final char ICON_CUBE = 0xF1B2;

    public static final char ICON_MOVE = 0xF047;
    public static final char ICON_ROTATE = 0xF021;
    public static final char ICON_SCALE = 0xE4BA;
    public static final char ICON_FREE_TRANSFORM = 0xE4F6;

    public static final char ICON_ARROWS_V = 0xF07D;

    public static final char ICON_ARROW_POINTER = 0xF245;
}
