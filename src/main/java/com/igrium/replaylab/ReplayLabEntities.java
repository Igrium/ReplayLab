package com.igrium.replaylab;

import com.igrium.replaylab.camera.AnimatedCameraEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;

public class ReplayLabEntities {
    public static final EntityType<AnimatedCameraEntity> CAMERA = registerEntity(
            "replaylab:camera", EntityType.Builder.of(AnimatedCameraEntity::new, MobCategory.MISC)
                    .sized(.75f, .75f)
                    .eyeHeight(0)
                    .noSummon());

    private static <T extends Entity> EntityType<T> registerEntity(String id, EntityType.Builder<T> type) {
        ResourceKey<EntityType<?>> registryKey = ResourceKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse(id));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, registryKey, type.build(registryKey));
    }

    public static void register() {
        // Empty method for classloader
    }
}
