package com.igrium.replaylab.scene.obj;

import com.igrium.replaylab.scene.ReplayScene;
import lombok.Getter;
import lombok.NonNull;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * A replay object that spawns a virtual "entity" in the scene. Used for cameras and display elements
 */
public abstract class EntityObject<T extends Entity> extends ReplayObject3D {
    public EntityObject(ReplayObjectType<?> type, ReplayScene scene) {
        super(type, scene);
    }

    /**
     * The current instantiated entity
     */
    @Getter
    private @Nullable T entity;

    /**
     * Get or create the entity instance.
     * @param world Ensure the entity belongs to this world.
     */
    public final T getOrCreateEntity(@NonNull ClientWorld world) {
        if (entity == null || entity.isRemoved() || entity.getWorld() != world) {
            entity = createEntity(world);
        }
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

    @Override
    public void apply(int timestamp) {
        var world = MinecraftClient.getInstance().world;
        if (world == null)
            return;

        T ent = getOrCreateEntity(world);
        applyEntityTransform(ent, timestamp);
    }

    // Cache so we're not re-allocating every frame
    private final Vector3d globalPos = new Vector3d();
    private final Vector3d globalRot = new Vector3d();

    /**
     * Sample and apply this object's transform to the entity.
     *
     * @param entity    Entity to apply to.
     * @param timestamp Current timestamp. Transform values are already applied, so it's likely not used.
     */
    protected void applyEntityTransform(T entity, int timestamp) {
        getCombinedTransform(globalPos, globalRot, null);
        entity.setPos(globalPos.x(), globalPos.y(), globalPos.z());
        // TODO: double-check that this transform setup is compatible with entities
        entity.setYaw((float) globalRot.y());
        entity.setPitch((float) globalRot.x());
    }

    /**
     * Create a new instance of the entity.
     *
     * @param world World to put the entity in
     * @return The new entity instance
     */
    protected abstract T createEntity(ClientWorld world);
}
