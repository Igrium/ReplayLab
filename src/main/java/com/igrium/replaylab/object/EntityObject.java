package com.igrium.replaylab.object;

import com.igrium.replaylab.math.MathUtils;
import com.igrium.replaylab.math.Transform3;
import com.igrium.replaylab.scene.ReplayScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.concurrent.atomic.AtomicInteger;

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
    public @Nullable T getEntity(ClientLevel world) {
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

    private boolean isEntValid(Entity entity, Level world) {
        return entity != null && !entity.isRemoved() && entity.level() == world;
    }

    @Override
    public void apply(int timestamp) {
        var world = Minecraft.getInstance().level;
        if (world == null)
            return;

        T ent = getEntity(world);
        applyToEntity(ent, timestamp);
    }

    @Override
    public void onAdded() {
        var world = Minecraft.getInstance().level;
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

        entity.setPosRaw(pos.x, pos.y, pos.z);
        entity.xo = pos.x;
        entity.yo = pos.y;
        entity.zo = pos.z;

        // TODO: double-check that this transform setup is compatible with entities
        var rot = MathUtils.toEntityRot(transform.getRot(new Quaternionf()));

        float pitch = rot.x;
        float yaw = rot.y;

        entity.setYRot(yaw);
        entity.setXRot(pitch);

        entity.yRotO = yaw;
        entity.xRotO = pitch;
    }

    /**
     * Create a new instance of the entity. <em>Make sure to add it to the world with
     * {@link #addToWorld}!</em>
     *
     * @param world World to put the entity in
     * @return The new entity instance
     */
    protected abstract T createEntity(ClientLevel world);

    /**
     * Client-side ID pool for entities ReplayLab spawns itself.
     * <p>
     * Since 26.2, {@code Level.getNextEntityId()} returns <code>0</code> on the client -- IDs are
     * expected to arrive from the server in the spawn packet, and {@link Entity#getId()} throws until
     * one has been assigned. Our entities never come off the wire, so we hand out our own from the top
     * of the range downwards, where they won't collide with the low IDs a replay stream carries.
     */
    private static final AtomicInteger NEXT_ENTITY_ID = new AtomicInteger(Integer.MAX_VALUE);

    /**
     * Assign a client-side entity ID and add the entity to the world.
     *
     * @param world  World to add the entity to.
     * @param entity Entity to add.
     */
    protected static void addToWorld(ClientLevel world, Entity entity) {
        int id;
        do {
            id = NEXT_ENTITY_ID.getAndDecrement();
            if (id <= 0) {
                // Astronomically unlikely, but wrapping past zero would hand out an ID getId() rejects.
                NEXT_ENTITY_ID.set(Integer.MAX_VALUE);
                id = NEXT_ENTITY_ID.getAndDecrement();
            }
        } while (world.getEntity(id) != null);

        entity.setId(id);
        world.addEntity(entity);
    }
}
