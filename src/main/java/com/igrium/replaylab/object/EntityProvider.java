package com.igrium.replaylab.object;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
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
    @Nullable T getEntity(ClientLevel world);

    /**
     * Get the entity this object references from the current client world.
     *
     * @return The entity. <code>null</code> if it does not exist or if the client isn't in a world.
     */
    default @Nullable T getEntity() {
        var world = Minecraft.getInstance().level;
        return (world != null) ? getEntity(world) : null;
    }
}
