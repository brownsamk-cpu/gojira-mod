package com.gojiramod;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

public final class ModItems {
	public static final ResourceKey<Item> GOJIRA_SPAWN_EGG_KEY = ResourceKey.create(Registries.ITEM, GojiraMod.id("gojira_spawn_egg"));
	public static final Item GOJIRA_SPAWN_EGG = Registry.register(BuiltInRegistries.ITEM, GOJIRA_SPAWN_EGG_KEY,
			new SpawnEggItem(new Item.Properties().spawnEgg(ModEntities.GOJIRA).setId(GOJIRA_SPAWN_EGG_KEY)));

	/** The Gojira Mod creative tab. */
	public static final ResourceKey<CreativeModeTab> TAB_KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, GojiraMod.id("gojira_mod"));
	public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB_KEY, FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.gojiramod.gojira_mod"))
			.icon(() -> new ItemStack(GOJIRA_SPAWN_EGG))
			.displayItems((params, output) -> output.accept(GOJIRA_SPAWN_EGG))
			.build());

	private ModItems() {
	}

	public static void init() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> output.insertAfter(Items.GUARDIAN_SPAWN_EGG, GOJIRA_SPAWN_EGG));
	}
}
