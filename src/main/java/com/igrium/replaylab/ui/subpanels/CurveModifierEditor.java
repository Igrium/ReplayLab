package com.igrium.replaylab.ui.subpanels;

import com.igrium.craftui.MaterialIcons;
import com.igrium.replaylab.anim.KeyChannel;
import com.igrium.replaylab.anim.modifier.CurveModifier;
import com.igrium.replaylab.anim.modifier.CurveModifierType;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.editor.KeySelectionSet.ChannelReference;
import com.igrium.replaylab.operator.keyframe.AddModifierOperator;
import com.igrium.replaylab.operator.keyframe.RemoveModifierOperator;
import com.igrium.replaylab.scene.obj.EditFlags;
import com.igrium.replaylab.ui.widgets.DraggableList;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import lombok.experimental.UtilityClass;
import net.minecraft.util.Language;
import org.jetbrains.annotations.Nullable;

@UtilityClass
public class CurveModifierEditor {

    public static int drawHeader(EditorState editorState, @Nullable ChannelReference chRef, @Nullable KeyChannel channel) {
        ImGui.beginDisabled(channel == null);

        if (ImGui.beginCombo("##addMod", t("gui.replaylab.add_mod"))) {
            drawAddModifier(editorState, chRef);
            ImGui.endCombo();
        }

        ImGui.endDisabled();
        return 0;
    }

    public static int drawEditor(EditorState editorState, @Nullable ChannelReference chRef, @Nullable KeyChannel channel) {
        int flags = EditFlags.NONE;

        if (channel == null) {
            ImGui.textWrapped(tt("gui.replaylab.one_channel"));
            return EditFlags.NONE;
        }
        DraggableList.begin("modifierEditor");

        // Duplicate to avoid concurrent modification
        CurveModifier[] mods = channel.getModifiers().toArray(CurveModifier[]::new);

        int toDelete = -1;
        float buttonSize = ImGui.getFrameHeight();
        for (int i = 0; i < mods.length; i++) {
            String key = tt(mods[i].getType().getTranslationKey()) + "###" + i;

            DraggableList.beginItem(key);
            ImGui.setNextItemAllowOverlap();
            boolean open = ImGui.collapsingHeader(key, ImGuiTreeNodeFlags.DefaultOpen);
            ImGui.sameLine(ImGui.getContentRegionAvailX() - buttonSize);
            if (ImGui.button("" + MaterialIcons.ICON_DELETE)) {
                toDelete = i;
            }
            if (open) {
                ImGui.indent();
                flags |= mods[i].drawPropertiesPanel(editorState);
                ImGui.unindent();
            }
            int targetPos = DraggableList.endItem();

            if (targetPos >= 0) {
                //noinspection SuspiciousListRemoveInLoop
                channel.getModifiers().remove(i);
                channel.getModifiers().add(targetPos, mods[i]);
                flags |= EditFlags.COMMIT | EditFlags.RESAMPLE;
            }
        }
        DraggableList.end();

        if (toDelete >= 0 && chRef != null) {
            editorState.applyOperator(new RemoveModifierOperator(chRef, toDelete));
        }
        return flags;
    }

    private static void drawAddModifier(EditorState editor, @Nullable ChannelReference ref) {
        for (var type : CurveModifierType.REGISTRY.values()) {
            assert type != null; // Idea's null checking can get a little over-eager
            if (ImGui.selectable(t(type.getTranslationKey())) && ref != null) {
                editor.applyOperator(new AddModifierOperator(ref, type.create()));
            }
        }
    }

    private static String tt(String key) {
        return Language.getInstance().get(key);
    }

    private static String t(String key) {
        return Language.getInstance().get(key) + "###" + key;
    }
}
