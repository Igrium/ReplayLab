package com.igrium.replaylab.ui.widgets;

import com.google.common.collect.Iterables;
import com.igrium.craftui.MaterialIcons;
import com.replaymod.replay.camera.CameraEntity;
import imgui.ImGui;
import imgui.ImGuiStorage;
import imgui.flag.ImGuiChildFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.experimental.UtilityClass;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Collections;
import java.util.function.Predicate;

@UtilityClass
public class EntitySelectorWindow {

    // Slight memory leak, but it's only one per unique window name per process lifetime.
    private static final Int2ObjectMap<ImString> filterStrs = new Int2ObjectOpenHashMap<>();

    private static final ImBoolean onlyPlayers = new ImBoolean(false);

    public static boolean selectEntity(String name, ImInt entId) {
        return selectEntity(name, entId, ent -> !(ent instanceof CameraEntity));
    }

    public static boolean selectEntity(String name, ImInt entId, Predicate<? super Entity> predicate) {
        ClientWorld world = MinecraftClient.getInstance().world;
        Iterable<Entity> iterable = world != null ? Iterables.filter(world.getEntities(), predicate::test) : Collections.emptyList();
        return selectEntity(name, entId, iterable);
    }

    public static boolean selectEntity(String name, ImInt entId, Iterable<? extends Entity> entities) {
        boolean modified = false;
        try {
            ImGui.pushID(name);

            int filterKey = ImGui.getID("filter");
            if (ImGui.isWindowAppearing()) {
                filterStrs.remove(filterKey);
            }

            ImString filter = filterStrs.computeIfAbsent(filterKey, id -> new ImString());
            ImGui.inputTextWithHint("##filter", "" + MaterialIcons.ICON_SEARCH, filter);
            ImGui.sameLine();

            ImGuiStorage storage = ImGui.getStateStorage();

            int onlyPlayerKey = ImGui.getID("onlyPlayers");
            onlyPlayers.set(storage.getBool(onlyPlayerKey));
            ImGui.checkbox("Only Players", onlyPlayers);
            storage.setBool(onlyPlayerKey, onlyPlayers.get());

            ImGui.beginChild("entList", ImGui.getContentRegionAvailX(), -1, ImGuiChildFlags.ResizeY);
            String filterStr = filter.get().toLowerCase();
            int index = 0;
            for (var ent : entities) {
                if (onlyPlayers.get() && !(ent instanceof PlayerEntity)) {
                    continue;
                }

                String entName = ent.getName().getString();
                if (!filterStr.isBlank() && !entName.toLowerCase().contains(filterStr)) {
                    continue;
                }

                int id = ent.getId();
                if (ImGui.selectable(entName + "##" + index, id == entId.get())) {
                    entId.set(id);
                    modified = true;
                }
                index++;
            }
            ImGui.endChild();

        } finally {
            ImGui.popID();
        }

        return modified;
    }
}