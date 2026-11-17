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

	public static final ResourceKey<EntityType<?>> SHOWA_GODZILLA_KEY = ResourceKey.create(Registries.ENTITY_TYPE, GojiraMod.id("showa_godzilla"));

	/**
	 * 0.2.0: Showa Godzilla, June's hero. Same size as Gojira (54.67 blocks to look at, a 6 x 16 hitbox), but he's not a
	 * monster: MISC category like the iron golem, so iron golems leave him alone and Peaceful doesn't remove him.
	 * No natural spawning: only from his spawn egg.
	 */
	public static final EntityType<ShowaGodzilla> SHOWA_GODZILLA = Registry.register(BuiltInRegistries.ENTITY_TYPE, SHOWA_GODZILLA_KEY,
			EntityType.Builder.<ShowaGodzilla>of(ShowaGodzilla::new, MobCategory.MISC)
					.sized(ShowaGodzilla.HITBOX_WIDTH, ShowaGodzilla.HITBOX_HEIGHT)
					.eyeHeight(ShowaGodzilla.HITBOX_HEIGHT - 1.0F)
					.fireImmune()
					.clientTrackingRange(16)
					.build(SHOWA_GODZILLA_KEY));

	private ModEntities() {
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(GOJIRA, Gojira.createAttributes());
		FabricDefaultAttributeRegistry.register(SHOWA_GODZILLA, ShowaGodzilla.createAttributes());
	}
}
