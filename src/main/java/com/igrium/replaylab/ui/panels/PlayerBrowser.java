package com.igrium.replaylab.ui.panels;

import com.igrium.craftui.api.icon.FontAwesome;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.entity.AnimatedCameraEntity;
import com.igrium.replaylab.object.ReplayObject;
import com.igrium.replaylab.object.ReplayObjects;
import com.igrium.replaylab.object.types.ObjectEntityProxy;
import com.igrium.replaylab.operator.object.AddObjectOperator;
import com.igrium.replaylab.ui.subpanels.ObjectContextMenu;
import com.mojang.authlib.GameProfile;
import com.replaymod.replay.camera.CameraEntity;
import imgui.ImGui;
import imgui.flag.ImGuiMouseButton;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;

public class PlayerBrowser extends UIPanel {

    private List<? extends Player> players = Collections.emptyList();

    private final ImString filter = new ImString();

    private float reloadButtonWidth = 0;

    public PlayerBrowser(Identifier id) {
        super(id, false);
    }

    Player contextPlayer = null;

    @Override
    protected void drawContents(EditorState editorState) {
        if (ImGui.isWindowAppearing()) {
            findPlayers();
        }

        if (contextPlayer != null && contextPlayer.isRemoved())
            contextPlayer = null;

        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - reloadButtonWidth - ImGui.getStyle().getItemSpacingX());
        ImGui.inputTextWithHint("##filter", "" + FontAwesome.ICON_MAGNIFYING_GLASS, filter);

        ImGui.sameLine();
        if (ImGui.button("" + FontAwesome.ICON_ROTATE)) {
            findPlayers();
        }
        reloadButtonWidth = ImGui.getItemRectSizeX();
        String filterStr = filter.get().toLowerCase();

        if (ImGui.beginChild("PlayerList", ImGui.getContentRegionAvailX(), ImGui.getContentRegionAvailY())) {
            for (var player : players) {
                GameProfile profile = player.getGameProfile();
                if (!profile.name().toLowerCase().contains(filterStr)) continue;
                ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
                if (ImGui.selectable(profile.name() + "###" + profile.id().toString())) {
                    LoggerFactory.getLogger("ReplayLab/PlayerBrowser").info("Player {} selected", player.getName());
                    ReplayObject obj = editorState.getScene().firstReferencingObject(player);
                    if (obj != null) {
                        editorState.getSelectedObjects().clear();
                        editorState.getSelectedObjects().add(obj.getId());
                        editorState.setActiveObject(obj.getId());
                        editorState.snapViewportToSelected();
                    } else {
                        editorState.snapViewportTo(player.position());
                    }
                }

                if (ImGui.isItemClicked(ImGuiMouseButton.Right)) {
                    contextPlayer = player;
                    ImGui.openPopup("playerContext");
                }
            }
        }

        if (contextPlayer != null && ImGui.beginPopup("playerContext")) {
            ReplayObject obj = editorState.getScene().firstReferencingObject(contextPlayer);
            if (obj != null) {
                ObjectContextMenu.drawObjectContextMenu(obj, editorState,
                        ObjectContextMenu.NO_DELETE | ObjectContextMenu.NO_RENAME);
            } else {
                if (ImGui.menuItem(t("gui.replaylab.ent.add_ent_proxy"))) {
                    var proxy = ReplayObjects.ENTITY_PROXY.create(editorState.getScene());
                    proxy.setEntId(contextPlayer.getId());
                    editorState.applyOperator(new AddObjectOperator(contextPlayer.getGameProfile().name(), proxy));

                    editorState.getSelectedObjects().clear();
                    editorState.getSelectedObjects().add(proxy.getId());
                    editorState.setActiveObject(proxy.getId());
                }
                ImGui.setItemTooltip(tt("gui.replaylab.ent.add_ent_proxy.tooltip"));
            }
            ImGui.endPopup();
        }
        ImGui.endChild();
    }

    public void findPlayers() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {

            // Iterate over players instead of level.getPlayers so we only show the players in loaded chunks
            players = StreamSupport.stream(level.entitiesForRendering().spliterator(), false)
                    .map(ent -> ent instanceof Player p ? p : null)
                    .filter(Objects::nonNull)
                    .filter(this::isPlayerAllowed)
                    .sorted(this::comparePlayers)
                    .toList();
        } else {
            players = Collections.emptyList();
        }
    }

    private boolean isPlayerAllowed(Entity player) {
        return !(player instanceof CameraEntity) && !(player instanceof AnimatedCameraEntity);
    }

    private int comparePlayers(Player p1, Player p2) {
        if (p1.isSpectator() && !p2.isSpectator()) {
            return 1;
        } else if (!p1.isSpectator() && p2.isSpectator()) {
            return -1;
        } else {
            return p1.getGameProfile().name().toLowerCase().compareTo(p2.getGameProfile().name().toLowerCase());
        }
    }

    private static String tt(String key) {
        return Language.getInstance().getOrDefault(key);
    }

    private static String t(String key) {
        return tt(key) + "###" + key;
    }
}
