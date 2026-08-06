package com.igrium.replaylab.camera;

import com.igrium.replaylab.math.MathUtils;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import org.joml.Math;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3dc;
import org.joml.Vector3f;

/**
 * A camera entity designed to be used for fully-animated cameras (not entity spectating)
 * In ReplayLab, RM's CameraEntity has been relegated to the work camera only/
 */
public class AnimatedCameraEntity extends Entity implements FovProvider, RotationProvider, RollProvider {

    @Getter @Setter
    private float fov = 60;


    @Getter @Setter
    private boolean selected;

    @Getter @Setter
    private boolean active;

    @Getter @Setter
    private boolean sceneCamera;

    // Only used while piloting; NOT for rendering.
    @Getter
    private float aspectRatio = 1;

    @Getter
    private float roll;

    @Override
    public void setRoll(float roll) {
        this.roll = roll;
    }

    public void setAspectRatio(float aspectRatio) {
        this.aspectRatio = Math.max(aspectRatio, .01f);
    }

    private final Quaternionf rotationQuat = new Quaternionf();


    public AnimatedCameraEntity(EntityType<?> type, Level world) {
        super(type, world);
        if (!world.isClientSide) {
            throw new IllegalStateException("Animated camera should never be spawned on the server!");
        }
    }

    @Override
    public Quaternionf getRotationQuat(Quaternionf dest) {
        this.rotationQuat.get(dest).rotateY(Math.PI_f);
        return dest;
    }

    public Quaternionfc getRotationQuat() {
        return this.rotationQuat;
    }

    /**
     * Set the camera position without interpolation.
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     */
    public void setCameraPosition(double x, double y, double z) {
        this.xOld = this.xo = x;
        this.yOld = this.yo = y;
        this.zOld = this.zo = z;
        this.moveTo(x, y, z, getYRot(), getXRot());
        refreshDimensions();
    }

    public void setCameraPosition(Vector3dc vec) {
        setCameraPosition(vec.x(), vec.y(), vec.z());
    }

    private void setCameraRotation(float pitch, float yaw, float roll) {
        this.xRotO = pitch;
        this.yRotO = yaw;
        setXRot(pitch);
        setYRot(yaw);
        this.roll = roll;
    }

    public void setCameraRotation(Quaternionfc rot) {
        Vector3f euler = MathUtils.toEntityRot(rot);

        setCameraRotation(euler.x, euler.y, euler.z);
        rotationQuat.set(rot).normalize();
    }

    @Override
    public ClientLevel level() {
        return (ClientLevel) super.level();
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entityTrackerEntry) {
        throw new IllegalStateException("This entity is client-side only.");
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {

    }

    @Override
    public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {

    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {

    }

    @Override
    public boolean isOnFire() {
        return false;
    }

    @Override
    protected void spawnSprintParticle() {
        // We do not produce any particles, we are a camera
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return true; // Allows player to interact
    }

    /**
     * Because Box is missing crucial functions.
     */
    private static class SimpleBox {
        double minX, minY, minZ;
        double maxX, maxY, maxZ;

        SimpleBox(Vec3 origin) {
            minX = origin.x;
            minY = origin.y;
            minZ = origin.z;

            maxX = origin.x;
            maxY = origin.y;
            maxZ = origin.z;
        }

        SimpleBox expand(double amount) {
            minX -= amount;
            minY -= amount;
            minZ -= amount;

            maxX += amount;
            maxY += amount;
            maxZ += amount;
            return this;
        }

        SimpleBox shift(Vec3 vec) {
            minX += vec.x;
            minY += vec.y;
            minZ += vec.z;

            maxX += vec.x;
            maxY += vec.y;
            maxZ += vec.z;
            return this;
        }

        AABB toBox() {
            return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    @Override
    protected AABB makeBoundingBox(Vec3 pos) {
       return new SimpleBox(pos)
               .expand(0.5)
               .shift(getLookAngle().scale(.5))
               .toBox();
    }
}
