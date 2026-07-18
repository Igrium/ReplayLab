package com.igrium.replaylab.ui.subpanels;

import com.igrium.craftui.MaterialIcons;
import com.igrium.replaylab.anim.constraint.Constraint;
import com.igrium.replaylab.anim.constraint.ConstraintType;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.operator.object.NewConstraintOperator;
import com.igrium.replaylab.operator.object.RemoveConstraintOperator;
import com.igrium.replaylab.scene.obj.ObjectEditState;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.ui.widgets.DraggableList;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImString;
import net.minecraft.util.Language;

import java.util.AbstractMap;
import java.util.Map;

public class ConstraintEditor {
    private record ConstraintEntry(String key, Constraint<?> value) {
        public ConstraintEntry(Map.Entry<String, Constraint<?>> entry) {
            this(entry.getKey(), entry.getValue());
        }
    }

    private static final ImString strBuffer = new ImString(64);

    public static int draw(ReplayObject obj, EditorState editor) {
        int flags = ObjectEditState.NONE;

        if (ImGui.button("+")) {
            ImGui.openPopup("new");
        }

        if (ImGui.beginPopup("new")) {
            for (var type : ConstraintType.REGISTRY.values()) {
                if (type.getObjectClass().isAssignableFrom(obj.getClass())) {
                    if (ImGui.selectable(t(type.translationKey()))) {
                        editor.applyOperator(new NewConstraintOperator(obj.getId(), tt(type.translationKey()), type));
                        ImGui.closeCurrentPopup();
                    }
                }
            }
            ImGui.endPopup();
        }

        var constraints = obj.getConstraints();
        if (constraints.getValues().isEmpty()) {
            ImGui.text(tt("gui.replaylab.noconstraints"));
            return flags;
        }

        // Avoid concurrent modification
        ConstraintEntry[] entries = constraints.getValues().entryList()
                .stream().map(ConstraintEntry::new).toArray(ConstraintEntry[]::new);


        DraggableList.begin("constraintEditor");
        int toDelete = -1;
        float buttonSize = ImGui.getFrameHeight();
        for (int i = 0; i < entries.length; i++) {
            String key = entries[i].key();
            Constraint<?> value = entries[i].value();
            DraggableList.beginItem(key);

            ImGui.setNextItemAllowOverlap();
            boolean open = ImGui.collapsingHeader("##header" + key, ImGuiTreeNodeFlags.DefaultOpen);
            ImGui.sameLine();
            strBuffer.set(key);
            ImGui.inputText("##label" + key, strBuffer);
            if (ImGui.isItemDeactivatedAfterEdit()) {
                constraints.rename(key, strBuffer.get());
                flags |= ObjectEditState.COMMIT;
            }

            ImGui.sameLine();
            ImGui.text("[" + tt(value.getType().translationKey()) + "]");

            ImGui.sameLine(ImGui.getContentRegionAvailX() - buttonSize);
            if (ImGui.button("" + MaterialIcons.ICON_DELETE)) {
                toDelete = i;
            }

            if (open) {
                ImGui.indent();
                flags |= value.drawPropertiesPanel(editor);
                ImGui.unindent();
            }

            int targetPos = DraggableList.endItem();
            if (targetPos >= 0) {
                var list = constraints.getValues().entryList();
                var entry = list.remove(i);
                list.add(targetPos, entry);
                flags |= ObjectEditState.COMMIT;
            }
        }

        DraggableList.end();

        if (toDelete >= 0) {
            editor.applyOperator(new RemoveConstraintOperator(obj.getId(), entries[toDelete].key));
        }

        return flags;
    }

    private static String tt(String key) {
        return Language.getInstance().get(key);
    }

    private static String t(String key) {
        return tt(key) + "###" + key;
    }
}
