package com.gojiramod.client;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class GojiraRenderState extends LivingEntityRenderState {
	/** 0..1 through a stomp (0 = not stomping). */
	public float stompProgress;
	/** 0..1 how brightly the dorsal spikes glow white (beam wind-up / firing). */
	public float spikeGlow;
	/** 0..1 how far the jaw is open (roar / beam). */
	public float jawOpen;
}
