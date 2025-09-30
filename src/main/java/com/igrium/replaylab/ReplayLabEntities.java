package com.igrium.replaylab;

import com.igrium.replaylab.camera.AnimatedCameraEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public class ReplayLabEntities {
    public static final EntityType<AnimatedCameraEntity> CAMERA = registerEntity(
            "replaylab:camera", EntityType.Builder.create(AnimatedCameraEntity::new, SpawnGroup.MISC)
                    .dimensions(.75f, .75f)
                    .eyeHeight(0)
                    .disableSummon());

    private static <T extends Entity> EntityType<T> registerEntity(String id, EntityType.Builder<T> type) {
        RegistryKey<EntityType<?>> registryKey = RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(id));
        return Registry.register(Registries.ENTITY_TYPE, registryKey, type.build(registryKey));
    }

    public static void register() {
        // Empty method for classloader
    }
}
