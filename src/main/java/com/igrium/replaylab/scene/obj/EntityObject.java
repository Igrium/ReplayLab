package com.igrium.replaylab.scene.obj;

import com.igrium.replaylab.math.MathUtils;
import com.igrium.replaylab.math.Transform3;
import com.igrium.replaylab.scene.ReplayScene;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

/**
 * A replay object that spawns a virtual "entity" in the scene. Used for cameras and display elements
 */
public abstract class EntityObject<T extends Entity> extends ReplayObject3D implements EntityProvider<T> {
    public EntityObject(ReplayObjectType<?> type, ReplayScene scene) {
        super(type, scene);
    }

    /**
     * The current instantiated entity
     */
    private @Nullable T entity;

    @Override
    public @Nullable T getEntity(ClientWorld world) {
        if (!isEntValid(entity, world)) {
            entity = createEntity(world);
        }
        return entity;
    }

    /**
     * Get the current instantiated entity, regardless of whether it's still valid.
     */
    public @Nullable T getInstantiatedEntity() {
        return entity;
    }

    @Override
    protected boolean hasPosition() {
        return true;
    }

    @Override
    protected boolean hasRotation() {
        return true;
    }

    @Override
    protected boolean hasScale() {
        return false;
    }

    private boolean isEntValid(Entity entity, World world) {
        return entity != null && !entity.isRemoved() && entity.getWorld() == world;
    }

    @Override
    public void apply(int timestamp) {
        var world = MinecraftClient.getInstance().world;
        if (world == null)
            return;

        T ent = getEntity(world);
        applyToEntity(ent, timestamp);
    }

    @Override
    public void onAdded() {
        var world = MinecraftClient.getInstance().world;
        if (world == null)
            return;

        getEntity(world);
    }

    @Override
    public void onRemoved() {
        var ent = getEntity();
        if (ent != null) {
            ent.remove(Entity.RemovalReason.KILLED);
        }
    }

    /**
     * Apply this object's properties to the entity.
     *
     * @param entity    Entity to apply to.
     * @param timestamp Current timestamp. Transform values are already applied, so it's likely not used.
     */
    protected void applyToEntity(T entity, int timestamp) {
        var transform = getTransform(new Transform3());
        var pos = transform.pos();

        entity.setPos(pos.x, pos.y, pos.z);
        entity.prevX = pos.x;
        entity.prevY = pos.y;
        entity.prevZ = pos.z;

        // TODO: double-check that this transform setup is compatible with entities
        var rot = MathUtils.entityRot(transform.getRot(new Quaternionf()));

        float pitch = rot.x;
        float yaw = rot.y;

        entity.setYaw(yaw);
        entity.setPitch(pitch);

        entity.prevYaw = yaw;
        entity.prevPitch = pitch;
    }

    /**
     * Create a new instance of the entity. <em>Make sure to add it to the world!</em>
     *
     * @param world World to put the entity in
     * @return The new entity instance
     */
    protected abstract T createEntity(ClientWorld world);
}
