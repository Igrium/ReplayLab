package com.igrium.replaylab.ui.panels;

import com.igrium.craftui.api.icon.FontAwesome;
import com.igrium.replaylab.config.ReplayLabConfig;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.entity.AnimatedCameraEntity;
import com.igrium.replaylab.ui.util.ReplayLabControls;
import com.mojang.authlib.GameProfile;
import com.replaymod.replay.camera.CameraEntity;
import imgui.ImGui;
import imgui.flag.ImGuiSelectableFlags;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
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

        if (ImGui.beginListBox("##players", ImGui.getContentRegionAvailX(), ImGui.getContentRegionAvailY())) {
            for (var player : players) {
                GameProfile profile = player.getGameProfile();
                if (!profile.name().contains(filter.get())) continue;

                float cursorX = ImGui.getCursorPosX();
                float cursorY = ImGui.getCursorPosY();
                float rowHeight = ImGui.getTextLineHeight();

                boolean clicked = ImGui.selectable("##" + profile.id().toString(), false,
                        ImGuiSelectableFlags.AllowOverlap, 0, rowHeight);

                ImGui.setCursorPos(cursorX, cursorY);
                if (player instanceof AbstractClientPlayer clientPlayer) {
                    drawProfilePic(clientPlayer);
                    ImGui.sameLine();
                }
                ImGui.text(profile.name());

                if (clicked) {
                    selectPlayer(editorState, player);
                }
            }
            ImGui.endListBox();
        }
    }

    private void drawProfilePic(AbstractClientPlayer player) {
        float cursorX = ImGui.getCursorPosX();
        float cursorY = ImGui.getCursorPosY();

        float size = ImGui.getTextLineHeight();

        Minecraft mc = Minecraft.getInstance();
        AbstractTexture tex = mc.getTextureManager().getTexture(player.getSkin().body().texturePath());
        ReplayLabControls.image(tex.getTexture(), size, size, .125f, .125f, .25f, .25f);

        if (player.isModelPartShown(PlayerModelPart.HAT)) {
            ImGui.setCursorPos(cursorX, cursorY);
            ReplayLabControls.image(tex.getTexture(), size, size, .625f, .125f, .75f, .25f);
        }
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

    private void selectPlayer(EditorState editor, Player player) {
        var obj = editor.getScene().firstReferencingObject(player);
        if (obj != null) {
            editor.getSelectedObjects().clear();
            editor.getSelectedObjects().add(obj.getId());
            editor.setActiveObject(obj.getId());
        }
        editor.snapViewportTo(player.position());
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
