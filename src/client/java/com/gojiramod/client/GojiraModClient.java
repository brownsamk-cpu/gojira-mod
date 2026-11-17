package com.gojiramod.client;

import com.gojiramod.Gojira;
import com.gojiramod.ModEntities;
import com.gojiramod.ShowaGodzilla;
import net.minecraft.world.entity.Entity;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public class GojiraModClient implements ClientModInitializer {
	/** Remaining ticks and strength of the little camera shake after a nearby stomp. */
	private static int shakeTicks;
	private static float shakeStrength, appliedX, appliedY;

	/** A little camera shake when a kaiju's stomp lands near you. */
	private static void stompShake(final Entity kaiju) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		double d = player.distanceTo(kaiju);
		if (d < 48.0) {
			shakeTicks = 12;
			shakeStrength = (float) (1.6 * (1.0 - d / 48.0)) + 0.3F;
		}
	}

	@Override
	public void onInitializeClient() {
		ModelLayerRegistry.registerModelLayer(GojiraModel.LAYER, GojiraModel::createBodyLayer);
		EntityRendererRegistry.register(ModEntities.GOJIRA, GojiraRenderer::new);
		ModelLayerRegistry.registerModelLayer(ShowaGodzillaModel.LAYER, ShowaGodzillaModel::createBodyLayer);
		EntityRendererRegistry.register(ModEntities.SHOWA_GODZILLA, ShowaGodzillaRenderer::new);
		Gojira.clientStompLanded = GojiraModClient::stompShake;
		ShowaGodzilla.clientStompLanded = GojiraModClient::stompShake;
		// gentle shake: nudges the view a little each tick and always takes the previous nudge back, so it ends where it started
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.player == null || mc.isPaused()) {
				return;
			}
			if (appliedX != 0.0F || appliedY != 0.0F) {
				mc.player.setXRot(mc.player.getXRot() - appliedX);
				mc.player.setYRot(mc.player.getYRot() - appliedY);
				appliedX = appliedY = 0.0F;
			}
			if (shakeTicks > 0) {
				float s = shakeStrength * shakeTicks / 12.0F * (shakeTicks % 2 == 0 ? 1.0F : -1.0F);
				appliedX = s;
				appliedY = s * 0.5F;
				mc.player.setXRot(mc.player.getXRot() + appliedX);
				mc.player.setYRot(mc.player.getYRot() + appliedY);
				shakeTicks--;
			}
		});
	}
}
