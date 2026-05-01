package com.igrium.replaylab.ui;

import lombok.experimental.UtilityClass;
import net.minecraft.util.math.ColorHelper;

@UtilityClass
public class GizmoColors {
    public static final int DEFAULT = ColorHelper.getArgb(0, 0, 0);
    public static final int ACTIVE = ColorHelper.getArgb(252, 186, 0);
    public static final int SELECTED = ColorHelper.getArgb(204, 97, 2);
}
