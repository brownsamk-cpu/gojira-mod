package com.gojiramod.client;

import com.gojiramod.Gojira;
import com.gojiramod.GojiraMod;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.LivingEntityEmissiveLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

/**
 * Draws Gojira's model scaled up to {@link Gojira#VISUAL_HEIGHT} blocks (its real hitbox is much smaller), plus a white
 * glowing layer on the spikes and mouth that fades in while the beam charges and fires.
 */
public class GojiraRenderer extends MobRenderer<Gojira, GojiraRenderState, GojiraModel> {
	private static final Identifier TEXTURE = GojiraMod.id("textures/entity/gojira.png");
	private static final Identifier GLOW = GojiraMod.id("textures/entity/gojira_glow.png");
	private static final float SCALE = Gojira.VISUAL_HEIGHT / (GojiraModelLayer.MODEL_HEIGHT_PX / 16.0F);
	/** How far the scaled model reaches from its feet (tail tip ~49 blocks behind, snout ~21 in front, ~12 to each side). */
	private static final double VISUAL_REACH = 50.0;

	public GojiraRenderer(final EntityRendererProvider.Context context) {
		super(context, new GojiraModel(context.bakeLayer(GojiraModel.LAYER)), 8.0F);
		this.addLayer(new LivingEntityEmissiveLayer<>(this, s -> GLOW, (s, age) -> s.spikeGlow, this.getModel(), RenderTypes::entityTranslucentEmissive, false));
	}

	@Override
	protected void scale(final GojiraRenderState state, final PoseStack poseStack) {
		poseStack.scale(SCALE, SCALE, SCALE);
	}

	@Override
	protected AABB getBoundingBoxForCulling(final Gojira entity, final float partialTicks) {
		// cull by the size of the drawn model, not the small hitbox, so it doesn't vanish when its feet are off screen
		AABB box = entity.getInterpolatedBoundingBox(partialTicks);
		return new AABB(box.getCenter().x - VISUAL_REACH, box.minY, box.getCenter().z - VISUAL_REACH, box.getCenter().x + VISUAL_REACH,
				box.minY + Gojira.VISUAL_HEIGHT + 2.0, box.getCenter().z + VISUAL_REACH);
	}

	@Override
	public Identifier getTextureLocation(final GojiraRenderState state) {
		return TEXTURE;
	}

	@Override
	public GojiraRenderState createRenderState() {
		return new GojiraRenderState();
	}

	@Override
	public void extractRenderState(final Gojira entity, final GojiraRenderState state, final float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.stompProgress = entity.getStompProgress(partialTicks);
		state.spikeGlow = entity.getSpikeGlow(partialTicks);
		state.jawOpen = entity.getJawOpen(partialTicks);
	}
}
