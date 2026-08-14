package com.igrium.replaylab.math;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Because Box is missing crucial functions.
 */
public class SimpleBox {
    double minX, minY, minZ;
    double maxX, maxY, maxZ;

    public SimpleBox(Vec3 origin) {
        minX = origin.x;
        minY = origin.y;
        minZ = origin.z;

        maxX = origin.x;
        maxY = origin.y;
        maxZ = origin.z;
    }

    public SimpleBox expand(double amount) {
        minX -= amount;
        minY -= amount;
        minZ -= amount;

        maxX += amount;
        maxY += amount;
        maxZ += amount;
        return this;
    }

    public SimpleBox shift(Vec3 vec) {
        minX += vec.x;
        minY += vec.y;
        minZ += vec.z;

        maxX += vec.x;
        maxY += vec.y;
        maxZ += vec.z;
        return this;
    }

    public AABB toBox() {
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
}