package com.igrium.replaylab.ui.panels;

import com.igrium.craftui.api.icon.FontAwesome;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.entity.AnimatedCameraEntity;
import com.mojang.authlib.GameProfile;
import com.replaymod.replay.camera.CameraEntity;
import imgui.ImGui;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

public class PlayerBrowser extends UIPanel {

    private List<? extends Player> players = Collections.emptyList();

    private final ImString filter = new ImString();

    private float reloadButtonWidth = 0;

    public PlayerBrowser(Identifier id) {
        super(id, false);
    }

    @Override
    protected void drawContents(EditorState editorState) {
        if (ImGui.isWindowAppearing()) {
            findPlayers();
        }

        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - reloadButtonWidth - ImGui.getStyle().getItemSpacingX());
        ImGui.inputTextWithHint("##filter", "" + FontAwesome.ICON_MAGNIFYING_GLASS, filter);

        ImGui.sameLine();
        if (ImGui.button("" + FontAwesome.ICON_ROTATE)) {
            findPlayers();
        }
        reloadButtonWidth = ImGui.getItemRectSizeX();

        if (ImGui.beginChild("PlayerList", ImGui.getContentRegionAvailX(), ImGui.getContentRegionAvailY())) {
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
//            if (ImGui.beginListBox("##players")) {
                for (var player : players) {
                    GameProfile profile = player.getGameProfile();
                    if (ImGui.selectable(profile.name() + "###" + profile.id().toString())) {
                        LoggerFactory.getLogger("ReplayLab/PlayerBrowser").info("Player {} selected", player.getName());
                    }
                }
//                ImGui.endListBox();
//            }
        }
        ImGui.endChild();
    }

    public void findPlayers() {
        Level level = Minecraft.getInstance().level;
        if (level != null) {
            players = level.players().stream()
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
            return p1.getGameProfile().name().compareTo(p2.getGameProfile().name());
        }
    }

}
