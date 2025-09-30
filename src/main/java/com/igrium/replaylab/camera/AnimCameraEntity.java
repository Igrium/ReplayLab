package com.igrium.replaylab.camera;

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
import net.minecraft.world.World;

/**
 * A camera entity designed to be used for fully-animated cameras (not entity spectating)
 * In ReplayLab, RM's CameraEntity has been relegated to the work camera only/
 */
public class AnimCameraEntity extends Entity implements RollProvider, FovProvider {

    private double fov;
    private float roll;

    public AnimCameraEntity(EntityType<?> type, World world) {
        super(type, world);
        if (!world.isClient) {
            throw new IllegalStateException("Animated camera should never be spawned on the server!");
        }
    }


    @Override
    public double getFov() {
        return fov;
    }

    public void setFov(double fov) {
        this.fov = fov;
    }

    @Override
    public float getRoll() {
        return roll;
    }

    public void setRoll(float roll) {
        this.roll = roll;
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
        this.setPosition(x, y, z);
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

    @Override
    public ClientWorld getWorld() {
        return (ClientWorld) super.getWorld();
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket(EntityTrackerEntry entityTrackerEntry) {
        throw new IllegalStateException("This eneity is client-side only.");
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
}
