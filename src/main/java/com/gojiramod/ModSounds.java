package com.gojiramod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

public final class ModSounds {
	/** Gojira's own "skreeonk" roar, synthesized from scratch by tools/gojira.py (no movie audio). */
	public static final SoundEvent GOJIRA_ROAR = register("entity.gojira.roar");

	private ModSounds() {
	}

	private static SoundEvent register(String name) {
		return Registry.register(BuiltInRegistries.SOUND_EVENT, GojiraMod.id(name), SoundEvent.createVariableRangeEvent(GojiraMod.id(name)));
	}

	public static void init() {
	}
}
