package com.igrium.replaylab.ui.gizmos;

import lombok.experimental.UtilityClass;
import net.minecraft.util.ARGB;

@UtilityClass
public class GizmoColors {
    public static final int DEFAULT = ARGB.color(128, 0, 0, 0);
    public static final int ACTIVE = ARGB.color(252, 186, 0);
    public static final int SELECTED = ARGB.color(204, 97, 2);
}
