package com.igrium.replaylab.ui;

import com.igrium.craftui.CraftUIFonts;
import imgui.ImFont;
import lombok.experimental.UtilityClass;
import net.minecraft.util.Identifier;

@UtilityClass
public final class PlaybackIcons {
    public static final Identifier PLAYBACK_ICONS = Identifier.of("replaylab:playback-icons");

    public static ImFont playbackIcons() {
        return CraftUIFonts.getFont(PLAYBACK_ICONS);
    }

    public static final String PLAY = "A";
    public static final String PAUSE = "B";
    public static final String NEXT_KEYFRAME = "C";
    public static final String JUMP_END = "D";
    public static final String PREV_KEYFRAME = "E";
    public static final String JUMP_START = "F";
}
