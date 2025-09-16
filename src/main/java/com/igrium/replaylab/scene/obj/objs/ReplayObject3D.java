package com.igrium.replaylab.scene.obj.objs;

import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.key.KeyChannel;
import com.igrium.replaylab.scene.obj.ReplayObjectType;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * An animation object that consists of a 3d transform
 */
@Accessors(fluent = true)
public abstract class ReplayObject3D extends ReplayObject {

    @Getter
    private final boolean hasLocation;

    @Getter
    private final boolean hasRotation;

    @Getter
    private final boolean hasScale;

    public ReplayObject3D(ReplayObjectType<?> type, ReplayScene scene, boolean hasLocation, boolean hasRotation, boolean hasScale) {
        super(type, scene);
        this.hasLocation = hasLocation;
        this.hasRotation = hasRotation;
        this.hasScale = hasScale;

        createChannels();
    }

    /**
     * Sample the location of this object.
     *
     * @param timestamp Timestamp to sample.
     * @param dest      Destination vector.
     * @return <code>dest</code>
     * @apiNote Result is undefined if <code>hasLocation() == false</code>
     */
    public Vector3d getLocation(int timestamp, Vector3d dest) {
        dest.x = getChannel(0).sample(timestamp);
        dest.y = getChannel(1).sample(timestamp);
        dest.z = getChannel(2).sample(timestamp);
        return dest;
    }

    /**
     * Sample the rotation of this object.
     *
     * @param timestamp Timestamp to sample.
     * @param dest      Destination vector.
     * @return <code>dest</code>
     * @apiNote Result is undefined if <code>hasRotation() == false</code>
     */
    public Vector3d getRotation(int timestamp, Vector3d dest) {
        int rotStart = rotationStartIndex();
        dest.x = getChannel(rotStart).sample(timestamp);
        dest.y = getChannel(rotStart + 1).sample(timestamp);
        dest.z = getChannel(rotStart + 2).sample(timestamp);
        return dest;
    }

    /**
     * Sample the scale of this object.
     *
     * @param timestamp Timestamp to sample.
     * @param dest      Destination vector.
     * @return <code>dest</code>
     * @apiNote Result is undefined if <code>hasScale() == false</code>
     */
    public Vector3d getScale(int timestamp, Vector3d dest) {
        int scaleStart = scaleStartIndex();
        dest.x = getChannel(scaleStart).sample(timestamp);
        dest.y = getChannel(scaleStart + 1).sample(timestamp);
        dest.z = getChannel(scaleStart + 2).sample(timestamp);
        return dest;
    }

    @Override
    public void apply(int timestamp) {
        Vector3d locVec = hasLocation() ? getLocation(timestamp, new Vector3d()) : null;
        Vector3d rotVec = hasRotation() ? getRotation(timestamp, new Vector3d()) : null;
        Vector3d scaleVec = hasScale() ? getScale(timestamp, new Vector3d()) : null;

        applyTransform(locVec, rotVec, scaleVec, timestamp);
    }

    /**
     * Apply the object's visual transform to the game.
     *
     * @param location  Global location of the object if present.
     * @param rotation  Global rotation of the object if present.
     * @param scale     Global scale of the object if present.
     * @param timestamp Current timestamp
     */
    protected abstract void applyTransform(@Nullable Vector3dc location,
                                           @Nullable Vector3dc rotation,
                                           @Nullable Vector3dc scale, int timestamp);

    /**
     * Get the index of the Rotation X channel.
     * @return Rotation X index.
     * @apiNote May be incorrect if <code>hasRotation() == false</code>
     */
    public int rotationStartIndex() {
        return hasLocation() ? 3 : 0;
    }

    /**
     * Get the index of the Scale X channel.
     * @return Scale X index.
     * @apiNote May be incorrect if <code>hasScale() == false</code>
     */
    public int scaleStartIndex() {
        if (hasRotation()) {
            return hasLocation() ? 6 : 3;
        } else {
            return hasLocation() ? 3 : 0;
        }
    }

    /**
     * Create the channel list based on enabled transform modes.
     */
    private void createChannels() {
        getChannels().clear();
        if (hasLocation()) {
            getChannels().add(new KeyChannel("Location X"));
            getChannels().add(new KeyChannel("Location Y"));
            getChannels().add(new KeyChannel("Location Z"));
        }
        if (hasRotation()) {
            getChannels().add(new KeyChannel("Rotation X"));
            getChannels().add(new KeyChannel("Rotation Y"));
            getChannels().add(new KeyChannel("Rotation Z"));
        }
        if (hasScale()) {
            getChannels().add(new KeyChannel("Scale X"));
            getChannels().add(new KeyChannel("Scale Y"));
            getChannels().add(new KeyChannel("Scale z"));
        }
    }
}
