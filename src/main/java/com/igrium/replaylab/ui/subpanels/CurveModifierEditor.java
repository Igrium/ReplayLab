package com.igrium.replaylab.ui.subpanels;

import com.igrium.replaylab.anim.KeyChannel;
import com.igrium.replaylab.anim.modifier.CurveModifier;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.scene.obj.ObjectEditState;
import com.igrium.replaylab.ui.util.DraggableList;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import lombok.experimental.UtilityClass;
import net.minecraft.util.Language;
import org.jetbrains.annotations.Nullable;

@UtilityClass
public class CurveModifierEditor {

    public static int drawHeader(EditorState editorState, @Nullable KeyChannel channel) {
        ImGui.beginDisabled(channel == null);

        ImGui.button("+");

        ImGui.endDisabled();
        return 0;
    }

    public static int drawEditor(EditorState editorState, @Nullable KeyChannel channel) {
        int flags = ObjectEditState.NONE;

        if (channel == null) {
            ImGui.textWrapped(tt("gui.replaylab.one_channel"));
            return ObjectEditState.NONE;
        }
        DraggableList.begin("modifierEditor");

        // Duplicate to avoid concurrent modification
        CurveModifier[] mods = channel.getModifiers().toArray(CurveModifier[]::new);

        for (int i = 0; i < mods.length; i++) {
            String key = tt(mods[i].getType().getTranslationKey()) + "###" + i;

            DraggableList.beginItem(key);
            if (ImGui.collapsingHeader(key, ImGuiTreeNodeFlags.DefaultOpen)) {
                flags |= mods[i].drawPropertiesPanel(editorState);
            }
            int targetPos = DraggableList.endItem();

            if (targetPos >= 0) {
                //noinspection SuspiciousListRemoveInLoop
                channel.getModifiers().remove(i);
                channel.getModifiers().add(targetPos, mods[i]);
                flags |= ObjectEditState.COMMIT | ObjectEditState.RESAMPLE;
            }
        }

        DraggableList.end();
        return flags;
    }

    private static String tt(String key) {
        return Language.getInstance().get(key);
    }
}
