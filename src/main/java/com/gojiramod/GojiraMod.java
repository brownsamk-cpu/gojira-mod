package com.gojiramod;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

/** Gojira Mod (June and Leon's design): Gojira, a giant, always-angry, unbeatable ocean kaiju, and (0.2.0) Showa Godzilla, June's hero kaiju. */
public class GojiraMod implements ModInitializer {
	public static final String MOD_ID = "gojiramod";

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModSounds.init();
		ModEntities.init();
		ModItems.init();
	}
}
