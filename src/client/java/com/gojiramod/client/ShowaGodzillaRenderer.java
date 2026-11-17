package com.gojiramod.client;

import com.gojiramod.Gojira;
import com.gojiramod.GojiraMod;
import com.gojiramod.ShowaDimensions;
import com.gojiramod.ShowaGodzilla;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.LivingEntityEmissiveLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * Draws Showa Godzilla scaled up to 54.67 blocks, with a blue-white glow on his plates and mouth while the beam charges or
 * fires and all through jet flight. The drop kick tips him onto his back in mid-air, and /kill makes him fall over sideways.
 */
public class ShowaGodzillaRenderer extends MobRenderer<ShowaGodzilla, ShowaRenderState, ShowaGodzillaModel> {
	private static final Identifier TEXTURE = GojiraMod.id("textures/entity/showa_godzilla.png");
	private static final Identifier GLOW = GojiraMod.id("textures/entity/showa_godzilla_glow.png");
	private static final float SCALE = ShowaGodzilla.VISUAL_HEIGHT / (ShowaDimensions.MODEL_HEIGHT_PX / 16.0F);
	private static final double VISUAL_REACH = Math.max(ShowaDimensions.TAIL_REACH, Math.max(ShowaDimensions.FRONT_REACH, ShowaDimensions.HALF_WIDTH)) + 2.0;

	public ShowaGodzillaRenderer(final EntityRendererProvider.Context context) {
		super(context, new ShowaGodzillaModel(context.bakeLayer(ShowaGodzillaModel.LAYER)), 8.0F);
		this.addLayer(new LivingEntityEmissiveLayer<>(this, s -> GLOW, (s, age) -> s.spikeGlow, this.getModel(), RenderTypes::entityTranslucentEmissive, false));
	}

	@Override
	protected void scale(final ShowaRenderState state, final PoseStack poseStack) {
		poseStack.scale(SCALE, SCALE, SCALE);
	}

	@Override
	protected AABB getBoundingBoxForCulling(final ShowaGodzilla entity, final float partialTicks) {
		AABB box = entity.getInterpolatedBoundingBox(partialTicks);
		return new AABB(box.getCenter().x - VISUAL_REACH, box.minY - 20.0, box.getCenter().z - VISUAL_REACH, box.getCenter().x + VISUAL_REACH,
				box.minY + ShowaGodzilla.VISUAL_HEIGHT + 2.0, box.getCenter().z + VISUAL_REACH);
	}

	@Override
	public Identifier getTextureLocation(final ShowaRenderState state) {
		return TEXTURE;
	}

	@Override
	public ShowaRenderState createRenderState() {
		return new ShowaRenderState();
	}

	@Override
	public void extractRenderState(final ShowaGodzilla entity, final ShowaRenderState state, final float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.stompProgress = entity.getStompProgress(partialTicks);
		state.spikeGlow = entity.getSpikeGlow(partialTicks);
		state.jawOpen = entity.getJawOpen(partialTicks);
		state.swimAmount = entity.getSwimAmount(partialTicks);
		state.flyAmount = entity.getFlyAmount(partialTicks);
		state.swipeProgress = entity.getSwipeProgress(partialTicks);
		state.whipProgress = entity.getWhipProgress(partialTicks);
		state.kickProgress = entity.getKickProgress(partialTicks);
		state.danceProgress = entity.getDanceProgress(partialTicks);
		state.deathProgress = entity.getDeathProgress(partialTicks);
		state.deathTicks = state.deathTime;
		if (state.deathProgress > 0.0F) {
			state.spikeGlow = 0.0F;
			state.jawOpen = Math.max(0.0F, 1.0F - state.deathProgress / 0.3F) * 0.6F; // one last little roar
			state.flyAmount = 0.0F;
			state.deathTime = 0.0F; // no vanilla death flip
			state.hasRedOverlay = false; // and no red flash: kid-friendly
		}
	}

	@Override
	protected void setupRotations(final ShowaRenderState state, final PoseStack poseStack, final float bodyRot, final float entityScale) {
		super.setupRotations(state, poseStack, bodyRot, entityScale);
		if (state.kickProgress > 0.0F) {
			float deg = ShowaGodzilla.kickTiltDegrees(state.kickProgress);
			// lift as he tips back so his body is up in the air, not in the ground
			poseStack.translate(0.0F, (float) (ShowaDimensions.BODY_HALF_WIDTH * 0.9 * Math.max(0.0F, Mth.sin(deg * Mth.DEG_TO_RAD))) / entityScale, 0.0F);
			poseStack.rotateDegrees(Axis.XP, deg);
		}
		if (state.deathTicks > 0.0F) {
			float deg = Gojira.toppleDegrees(state.deathTicks);
			poseStack.translate(0.0F, (float) (ShowaDimensions.BODY_HALF_WIDTH * 0.8 * Mth.sin(deg * Mth.DEG_TO_RAD)) / entityScale, 0.0F);
			poseStack.rotateDegrees(Axis.ZP, deg);
		}
	}
}
