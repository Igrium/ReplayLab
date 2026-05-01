package com.igrium.replaylab.scene.obj;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A replay object that binds to an entity in the client world
 */
public interface EntityProvider<T extends Entity> {
    /**
     * Get the entity this object references.
     *
     * @param world World to search in.
     * @return The entity. <code>null</code> if it does not exist.
     */
    @Nullable T getEntity(World world);

    /**
     * Get the entity this object references from the current client world.
     *
     * @return The entity. <code>null</code> if it does not exist.
     */
    default @Nullable T getEntity() {
        var world = MinecraftClient.getInstance().world;
        return (world != null) ? getEntity(world) : null;
    }
}
