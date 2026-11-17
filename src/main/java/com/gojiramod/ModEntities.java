package com.gojiramod;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	public static final ResourceKey<EntityType<?>> GOJIRA_KEY = ResourceKey.create(Registries.ENTITY_TYPE, GojiraMod.id("gojira"));

	/**
	 * Gojira. It LOOKS 164/3 = 54.67 blocks tall (the model is scaled up in the renderer), but its real hitbox is
	 * {@link Gojira#HITBOX_WIDTH} x {@link Gojira#HITBOX_HEIGHT} so it can still move, swim and collide without lagging the game.
	 * No natural spawning: it only comes from its spawn egg (an unbeatable mob that never despawns would pile up otherwise).
	 */
	public static final EntityType<Gojira> GOJIRA = Registry.register(BuiltInRegistries.ENTITY_TYPE, GOJIRA_KEY,
			EntityType.Builder.<Gojira>of(Gojira::new, MobCategory.MONSTER)
					.sized(Gojira.HITBOX_WIDTH, Gojira.HITBOX_HEIGHT)
					.eyeHeight(Gojira.HITBOX_HEIGHT - 1.0F)
					.fireImmune()
					.clientTrackingRange(16)
					.build(GOJIRA_KEY));

	private ModEntities() {
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(GOJIRA, Gojira.createAttributes());
	}
}
