package com.igrium.replaylab.ui.widgets;

import com.igrium.replaylab.ui.ReplayLabIcons;
import com.igrium.replaylab.ui.util.ReplayLabControls;
import imgui.ImGui;
import imgui.type.ImInt;
import lombok.experimental.UtilityClass;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

import static com.igrium.replaylab.ui.util.ReplayLabControls.getRenderedText;

@UtilityClass
public class EntitySelector {

    public static boolean selector(String name, ImInt entId, Predicate<? super Entity> predicate) {
        Entity ent = getEnt(entId.get());
        String entName = ent != null ? ent.getName().getString() : "[NONE]";
        float width = ImGui.calcItemWidth();
        float iconWidth = ImGui.getFrameHeight();
        float mainWidth = width - iconWidth - ImGui.getStyle().getItemSpacingX();

        ImGui.pushID(name);
        ImGui.beginGroup();

        if (ImGui.button(entName + "###entButton", mainWidth, 0)) {
            ImGui.openPopup("selector");
        }

        boolean modified = false;
        if (ImGui.beginPopup("selector")) {
            if (EntitySelectorWindow.selectEntity(name, entId, predicate)) {
                modified = true;
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }

        ImGui.sameLine();
        ImGui.pushFont(ReplayLabIcons.getFont());
        if (ImGui.button(ReplayLabIcons.ICON_EYE_DROPPER + "###pickButton", iconWidth, 0)) {
            EntityPicker.open("picker");
        }
        EntityPicker picker = EntityPicker.get("picker");
        if (picker != null) {
            Entity picked = picker.getPickedEntity();
            if (picked != null) {
                entId.set(picked.getId());
                picker.close();
                modified = true;
            }
        }
        ImGui.popFont();

        ImGui.sameLine();
        ImGui.text(getRenderedText(name));

        ImGui.endGroup();
        ImGui.popID();

        return modified;
    }

    private static @Nullable Entity getEnt(int id) {
        ClientWorld world = MinecraftClient.getInstance().world;
        return world != null ? world.getEntityById(id) : null;
    }

}
