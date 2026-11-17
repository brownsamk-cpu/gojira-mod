package com.gojiramod.client;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class GojiraRenderState extends LivingEntityRenderState {
	/** 0..1 through a stomp (0 = not stomping). */
	public float stompProgress;
	/** 0..1 how brightly the dorsal spikes glow white (beam wind-up / firing). */
	public float spikeGlow;
	/** 0..1 how far the jaw is open (roar / beam). */
	public float jawOpen;
	/** 0..1 swimming (body flat, tail sweeping, legs kicking, arms tucked). */
	public float swimAmount;
	/** 0..1 through a claw swipe / tail whip (0 = not attacking). */
	public float swipeProgress, whipProgress;
	/** 0..1 through the death animation, and the death time in ticks (vanilla's own deathTime is zeroed so it doesn't flip it). */
	public float deathProgress, deathTicks;
}
