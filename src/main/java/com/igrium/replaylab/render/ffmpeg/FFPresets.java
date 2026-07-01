package com.igrium.replaylab.render.ffmpeg;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class FFPresets {
    public static final Map<String, String> PRESET = ImmutableMap.of(
            "mp4", "-an -c:v libx264 -b:v %BITRATE% -pix_fmt yuv420p \"%FILENAME%\""
    );

}
