package com.igrium.replaylab.scene.obj;

import com.igrium.replaylab.scene.ReplayScene;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.DisplayEntity;

public class ObjectBlockDisplay extends EntityObject<DisplayEntity.BlockDisplayEntity> {
    public ObjectBlockDisplay(ReplayObjectType<?> type, ReplayScene scene) {
        super(type, scene);
    }

    @Override
    protected DisplayEntity.BlockDisplayEntity createEntity(ClientWorld world) {
        DisplayEntity.BlockDisplayEntity ent = EntityType.BLOCK_DISPLAY.create(world, SpawnReason.COMMAND);
        if (ent != null) {
            ent.setBlockState(Blocks.DIRT.getDefaultState());
        }

        world.addEntity(ent);
        return ent;
    }
}
