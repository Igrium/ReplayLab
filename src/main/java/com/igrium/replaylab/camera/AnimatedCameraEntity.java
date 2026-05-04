package com.igrium.replaylab.camera;

import com.igrium.replaylab.math.MathUtils;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Quaternionfc;
import org.joml.Vector3dc;
import org.joml.Vector3f;

/**
 * A camera entity designed to be used for fully-animated cameras (not entity spectating)
 * In ReplayLab, RM's CameraEntity has been relegated to the work camera only/
 */
public class AnimatedCameraEntity extends Entity implements RollProvider, FovProvider {

    @Getter @Setter
    private float fov;

    @Getter @Setter
    private float roll;

    @Getter @Setter
    private boolean selected;

    @Getter @Setter
    private boolean active;

    public AnimatedCameraEntity(EntityType<?> type, World world) {
        super(type, world);
        if (!world.isClient) {
            throw new IllegalStateException("Animated camera should never be spawned on the server!");
        }
    }

    /**
     * Set the camera position without interpolation.
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     */
    public void setCameraPosition(double x, double y, double z) {
        this.lastRenderX = this.prevX = x;
        this.lastRenderY = this.prevY = y;
        this.lastRenderZ = this.prevZ = z;
        this.refreshPositionAndAngles(x, y, z, getYaw(), getPitch());
        calculateDimensions();
    }

    public void setCameraPosition(Vector3dc vec) {
        setCameraPosition(vec.x(), vec.y(), vec.z());
    }

    /**
     * Set the camera rotation without interpolation
     *
     * @param pitch Pitch in degrees
     * @param yaw   Yaw in degrees
     * @param roll  Roll in degrees
     */
    public void setCameraRotation(float pitch, float yaw, float roll) {
        this.prevPitch = pitch;
        this.prevYaw = yaw;
        setPitch(pitch);
        setYaw(yaw);
        setRoll(roll);
    }

    public void setCameraRotation(Quaternionfc rot) {
        Vector3f euler = MathUtils.entityRot(rot);
        setCameraRotation(euler.x, euler.y, euler.z);
//        Vector3f euler = rot.getEulerAnglesYXZ(new Vector3f());
//        setCameraRotation(Math.toDegrees(euler.y), Math.toDegrees(euler.x), Math.toDegrees(euler.z));
    }

    @Override
    public ClientWorld getWorld() {
        return (ClientWorld) super.getWorld();
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket(EntityTrackerEntry entityTrackerEntry) {
        throw new IllegalStateException("This entity is client-side only.");
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {

    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {

    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {

    }

    @Override
    public boolean isOnFire() {
        return false;
    }

    @Override
    protected void spawnSprintingParticles() {
        // We do not produce any particles, we are a camera
    }

    @Override
    public boolean shouldSave() {
        return false;
    }

    @Override
    public boolean canHit() {
        return true; // Allows player to interact
    }

    /**
     * Because Box is missing crucial functions.
     */
    private static class SimpleBox {
        double minX, minY, minZ;
        double maxX, maxY, maxZ;

        SimpleBox(Vec3d origin) {
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

        SimpleBox shift(Vec3d vec) {
            minX += vec.x;
            minY += vec.y;
            minZ += vec.z;

            maxX += vec.x;
            maxY += vec.y;
            maxZ += vec.z;
            return this;
        }

        Box toBox() {
            return new Box(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    @Override
    protected Box calculateDefaultBoundingBox(Vec3d pos) {
       return new SimpleBox(pos)
               .expand(0.5)
               .shift(getRotationVector().multiply(.5))
               .toBox();
    }
}
