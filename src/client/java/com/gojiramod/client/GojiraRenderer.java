package com.gojiramod.client;

import com.gojiramod.Gojira;
import com.gojiramod.GojiraDimensions;
import com.gojiramod.GojiraMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.LivingEntityEmissiveLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

/**
 * Draws Gojira's model scaled up to {@link Gojira#VISUAL_HEIGHT} blocks (its real hitbox is much smaller), plus a white
 * glowing layer on the spikes and mouth that fades in while the beam charges and fires (and fades out as it dies). When it
 * dies it slowly topples onto its side instead of the usual quick flip.
 */
public class GojiraRenderer extends MobRenderer<Gojira, GojiraRenderState, GojiraModel> {
	private static final Identifier TEXTURE = GojiraMod.id("textures/entity/gojira.png");
	private static final Identifier GLOW = GojiraMod.id("textures/entity/gojira_glow.png");
	private static final float SCALE = Gojira.VISUAL_HEIGHT / (GojiraDimensions.MODEL_HEIGHT_PX / 16.0F);
	/** How far the scaled model reaches from its center (the tail tip is the farthest, ~60 blocks), plus a little margin. */
	private static final double VISUAL_REACH = Math.max(GojiraDimensions.TAIL_REACH, Math.max(GojiraDimensions.FRONT_REACH, GojiraDimensions.HALF_WIDTH)) + 2.0;

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
		state.swimAmount = entity.getSwimAmount(partialTicks);
		state.swipeProgress = entity.getSwipeProgress(partialTicks);
		state.whipProgress = entity.getWhipProgress(partialTicks);
		state.deathProgress = entity.getDeathProgress(partialTicks);
		state.deathTicks = state.deathTime;
		if (state.deathProgress > 0.0F) {
			// a last roar with the jaw open, and the plates flare white and then fade out
			float p = state.deathProgress;
			state.spikeGlow = Math.max(state.spikeGlow, p < 0.1F ? p / 0.1F : Math.max(0.0F, 1.0F - (p - 0.1F) / 0.5F));
			state.jawOpen = Math.max(state.jawOpen, p < 0.35F ? Math.min(1.0F, p / 0.1F) : Math.max(0.0F, 1.0F - (p - 0.35F) / 0.15F));
			state.deathTime = 0.0F; // no vanilla death flip
			state.hasRedOverlay = false; // and no red flash: kid-friendly
		}
	}

	@Override
	protected void setupRotations(final GojiraRenderState state, final PoseStack poseStack, final float bodyRot, final float entityScale) {
		super.setupRotations(state, poseStack, bodyRot, entityScale);
		if (state.deathTicks > 0.0F) {
			float deg = Gojira.toppleDegrees(state.deathTicks);
			// lift it as it tips so it ends up lying on its side instead of half in the ground
			poseStack.translate(0.0F, (float) (GojiraDimensions.BODY_HALF_WIDTH * 0.8 * Mth.sin(deg * Mth.DEG_TO_RAD)) / entityScale, 0.0F);
			poseStack.rotateDegrees(Axis.ZP, deg);
		}
	}
}
