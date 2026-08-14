package com.igrium.replaylab.entity;

import com.igrium.replaylab.math.SimpleBox;
import com.igrium.replaylab.object.types.ObjectEmpty;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * An "empty" entity used to draw {@link ObjectEmpty}
 */
@SuppressWarnings("NullableProblems")
public class EmptyObjectEntity extends Entity {

    @Getter @Setter
    private boolean selected;

    @Getter @Setter
    private boolean active;

    @Getter
    private final Vector3f scale = new Vector3f(1);

    @Getter
    private final Quaternionf rotation = new Quaternionf();

    public void setScale(Vector3fc scale) {
        this.scale.set(scale);
    }

    public void setRotation(Quaternionfc rotation) {
        this.rotation.set(rotation);
    }

    public EmptyObjectEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {

    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {

    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {

    }

    @Override
    protected AABB makeBoundingBox(Vec3 position) {
        return new SimpleBox(position)
                .expand(0.4)
                .toBox();
    }
}
