package com.igrium.replaylab.operator.replay;

import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.operator.ReplayOperator;
import com.replaymod.replaystudio.data.Marker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class AddMarkerOperator implements ReplayOperator {

    private final int time;

    private @Nullable Marker marker;

    public AddMarkerOperator(int time) {
        this.time = time;
    }

    @Override
    public boolean execute(EditorState editor) throws Exception {
        Marker marker = new Marker();
        marker.setTime(time);
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            marker.setPitch(player.getXRot());
            marker.setYaw(player.getYRot());
            marker.setX(player.getX());
            marker.setY(player.getY());
            marker.setZ(player.getZ());
        }
        this.marker = marker;
        editor.getReplayMarkers().add(marker);
        editor.saveMarkers();
        return true;
    }

    @Override
    public void undo(EditorState editor) throws Exception {
        editor.getReplayMarkers().remove(marker);
        editor.saveMarkers();
    }

    @Override
    public void redo(EditorState editor) throws Exception {
        editor.getReplayMarkers().add(Objects.requireNonNull(marker));
        editor.saveMarkers();
    }

    @Override
    public boolean wantsSampleCurves() {
        return false;
    }
}
