package com.igrium.replaylab.object;

import com.igrium.replaylab.scene.ReplayScene;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Display;

public class ObjectBlockDisplay extends EntityObject<Display.BlockDisplay> {
    public ObjectBlockDisplay(ReplayObjectType<?> type, ReplayScene scene) {
        super(type, scene);
    }

    @Override
    protected Display.BlockDisplay createEntity(ClientLevel world) {
        Display.BlockDisplay ent = EntityTypes.BLOCK_DISPLAY.create(world, EntitySpawnReason.COMMAND);
        if (ent != null) {
            ent.setBlockState(Blocks.DIRT.defaultBlockState());
        }

        world.addEntity(ent);
        return ent;
    }
}
