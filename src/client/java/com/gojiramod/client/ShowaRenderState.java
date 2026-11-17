package com.gojiramod.client;

/** Showa Godzilla's render state: everything Gojira's has, plus jet flight, the drop kick and the victory dance. */
public class ShowaRenderState extends GojiraRenderState {
	/** 0..1 jet-flight pose (body flat, tail first, head up so the beam blasts out backward). */
	public float flyAmount;
	/** 0..1 through the drop kick / victory dance (0 = not doing it). */
	public float kickProgress, danceProgress;
}
