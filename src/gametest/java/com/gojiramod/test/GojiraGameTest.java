package com.gojiramod.test;

import com.gojiramod.Gojira;
import com.gojiramod.ModEntities;
import com.gojiramod.ModItems;
import com.gojiramod.ModSounds;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Gojira Mod 0.1.0: Gojira (June and Leon's design). Attack tests run on stone pads high in the sky, each at its own height, so they can't reach other tests. */
public class GojiraGameTest {
	private static Gojira calmGojira(GameTestHelper helper, BlockPos rel) {
		Gojira g = helper.spawn(ModEntities.GOJIRA, rel);
		g.calmForTest();
		return g;
	}

	/** A stone pad {@code up} blocks above this test; returns the block above its center. */
	private static BlockPos skyPad(GameTestHelper helper, int up, int radius) {
		BlockPos base = helper.absolutePos(new BlockPos(1, 1, 1)).above(up);
		for (BlockPos p : BlockPos.betweenClosed(base.offset(-radius, 0, -radius), base.offset(radius, 0, radius))) {
			helper.getLevel().setBlock(p, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		return base.above();
	}

	/** A stone platform under {@code top} (for Gojira to stand on). */
	private static void platform(GameTestHelper helper, BlockPos top, int radius) {
		for (BlockPos p : BlockPos.betweenClosed(top.below().offset(-radius, 0, -radius), top.below().offset(radius, 0, radius))) {
			helper.getLevel().setBlock(p, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	/** Force-loads the chunk at {@code pos} so entities there keep ticking (the test runner un-forces all chunks after the batch). */
	private static void forceTicking(GameTestHelper helper, BlockPos pos) {
		helper.getLevel().setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
	}

	private static boolean padIntact(GameTestHelper helper, BlockPos top, int radius) {
		BlockPos base = top.below();
		for (BlockPos p : BlockPos.betweenClosed(base.offset(-radius, 0, -radius), base.offset(radius, 0, radius))) {
			if (!helper.getLevel().getBlockState(p).is(Blocks.STONE)) {
				return false;
			}
		}
		return true;
	}

	private static void clearPad(GameTestHelper helper, BlockPos top, int radius) {
		BlockPos base = top.below();
		helper.getLevel().getEntitiesOfClass(Entity.class, AABB.encapsulatingFullBlocks(base.offset(-radius - 4, -4, -radius - 4), base.offset(radius + 4, 60, radius + 4)),
				e -> !(e instanceof ServerPlayer)).forEach(Entity::discard);
		for (BlockPos p : BlockPos.betweenClosed(base.offset(-radius, 0, -radius), base.offset(radius, 0, radius))) {
			helper.getLevel().setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	private static void moveTo(Entity e, BlockPos pos, float yaw) {
		e.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0);
		if (e instanceof Gojira g) {
			g.yBodyRot = yaw;
			g.yHeadRot = yaw;
		}
	}

	/** Registered as gojiramod:gojira with its spawn egg, roar sound, lang key; a monster that never despawns, breathes underwater and is ~50 blocks tall to look at. */
	@GameTest
	public void gojiraRegistered(GameTestHelper helper) {
		Gojira g = calmGojira(helper, new BlockPos(1, 2, 1));
		helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.getKey(ModEntities.GOJIRA).toString().equals("gojiramod:gojira"), "entity id");
		helper.assertTrue(g.getType().getDescriptionId().equals("entity.gojiramod.gojira"), "lang key");
		helper.assertTrue(BuiltInRegistries.ITEM.getKey(ModItems.GOJIRA_SPAWN_EGG).toString().equals("gojiramod:gojira_spawn_egg"), "egg id");
		helper.assertTrue(ModItems.GOJIRA_SPAWN_EGG instanceof SpawnEggItem, "spawn egg");
		helper.assertTrue(BuiltInRegistries.SOUND_EVENT.getKey(ModSounds.GOJIRA_ROAR).toString().equals("gojiramod:entity.gojira.roar"), "roar sound");
		helper.assertTrue(g.getMaxHealth() == Gojira.MAX_HEALTH, "max health");
		helper.assertTrue(g.isPersistenceRequired() && g.requiresCustomPersistence() && !g.removeWhenFarAway(1.0E8), "never despawns");
		helper.assertTrue(g.canBreatheUnderwater() && g.fireImmune(), "breathes underwater, fireproof");
		helper.assertTrue(Math.abs(g.getBbHeight() - Gojira.HITBOX_HEIGHT) < 0.01F && Gojira.VISUAL_HEIGHT == 50.0F, "hitbox and 50-block visual size");
		helper.assertTrue(Math.abs(Gojira.VISUAL_SCALE * 58.09F / 16.0F - 50.0F) < 0.1F, "model scale makes it 50 blocks tall");
		g.discard();
		helper.succeed();
	}

	/** Huge hits (even 100000 damage) never kill it: health stays at least 1 and it's still alive. */
	@GameTest(maxTicks = 40)
	public void gojiraCannotDie(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Gojira g = calmGojira(helper, new BlockPos(1, 2, 1));
		g.hurtServer(level, level.damageSources().generic(), 100000.0F);
		helper.assertTrue(g.isAlive() && !g.isDeadOrDying() && g.getHealth() >= Gojira.MIN_HEALTH, "survives a giant hit");
		g.setInvulnerableTime(0);
		g.hurtServer(level, level.damageSources().explosion(null, null), 5000.0F);
		g.setInvulnerableTime(0);
		g.hurtServer(level, level.damageSources().lava(), 5000.0F);
		g.setHealth(-50.0F);
		helper.assertTrue(g.isAlive() && g.getHealth() >= Gojira.MIN_HEALTH, "health is clamped to at least 1");
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(g.isAlive() && !g.isRemoved(), "still alive a second later");
			g.discard();
			helper.succeed();
		});
	}

	/** It regenerates faster than a strong player could hurt it (40 health a second). */
	@GameTest(maxTicks = 60)
	public void gojiraRegenerates(GameTestHelper helper) {
		Gojira g = calmGojira(helper, new BlockPos(1, 2, 1));
		g.setHealth(100.0F);
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(g.getHealth() >= 100.0F + Gojira.REGEN_PER_TICK * 15, "healed in one second, health " + g.getHealth());
			// a hit every 10 ticks (the fastest damage can land) for 12 health each: still heals more than it loses
			ServerLevel level = helper.getLevel();
			float before = g.getHealth();
			for (int i = 0; i < 3; i++) {
				final int k = i;
				helper.runAfterDelay(1 + 10 * k, () -> {
					g.setInvulnerableTime(0);
					g.hurtServer(level, level.damageSources().generic(), 12.0F);
				});
			}
			helper.runAfterDelay(31, () -> {
				helper.assertTrue(g.getHealth() > before, "regen outpaces damage: " + before + " -> " + g.getHealth());
				g.discard();
				helper.succeed();
			});
		});
	}

	/** The grown-up off switch: /kill (which bypasses invulnerability) still removes it. */
	@GameTest(maxTicks = 60)
	public void killCommandStillWorks(GameTestHelper helper) {
		Gojira g = calmGojira(helper, new BlockPos(1, 2, 1));
		g.kill(helper.getLevel());
		helper.assertTrue(g.isDeadOrDying(), "/kill works");
		helper.succeedWhen(() -> helper.assertTrue(g.isRemoved(), "gone after the death animation"));
	}

	/** Stomp: hurts and knocks back something near its feet, makes no holes in the ground. */
	@GameTest(maxTicks = 60)
	public void stompDamagesNearby(GameTestHelper helper) {
		BlockPos pad = skyPad(helper, 140, 14);
		Gojira g = calmGojira(helper, new BlockPos(1, 2, 1));
		Pig near = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(1, 2, 2));
		Pig far = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(2, 2, 1));
		moveTo(g, pad, 0);
		moveTo(near, pad.south(7), 0);
		moveTo(far, pad.east(14).south(14), 0);
		helper.runAfterDelay(2, () -> {
			Vec3 start = near.position();
			g.startStomp();
			helper.assertTrue(g.getStomps() == 0, "the foot is still in the air during the wind-up");
			helper.runAfterDelay(Gojira.STOMP_WINDUP + 3, () -> {
				helper.assertTrue(g.getStomps() == 1, "stomped");
				helper.assertTrue(near.getHealth() < near.getMaxHealth(), "the stomp hit the pig near its feet");
				helper.assertTrue(near.position().subtract(start).horizontalDistance() > 0.5 || near.getDeltaMovement().y > 0.05 || !near.onGround(),
						"knocked back");
				helper.assertTrue(far.getHealth() == far.getMaxHealth(), "a pig 20 blocks away is safe");
				helper.assertTrue(padIntact(helper, pad, 14), "no blocks broken");
				clearPad(helper, pad, 14);
				helper.succeed();
			});
		});
	}

	/** White beam: winds up (no damage yet), then fires along a line from its mouth and hurts the target; no blocks broken. */
	@GameTest(maxTicks = 160)
	public void beamDamagesAlongLine(GameTestHelper helper) {
		BlockPos pad = skyPad(helper, 80, 3);
		Gojira g = calmGojira(helper, new BlockPos(1, 2, 1));
		Husk z = helper.spawn(EntityTypes.HUSK, new BlockPos(1, 2, 3)); // husks don't burn in the sun
		z.setNoAi(true);
		z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0); // stays on its little platform
		platform(helper, pad.south(16), 4);
		forceTicking(helper, pad.south(16)); // the husk must keep ticking so its hurt cooldown counts down between pulses
		// Gojira faces south (+z) on the pad; its mouth is ~21 blocks ahead and ~42 up; the husk stands 16 blocks south
		moveTo(g, pad, 0);
		moveTo(z, pad.south(16), 0);
		int[] huskTicks = new int[1];
		helper.runAfterDelay(2, () -> huskTicks[0] = z.tickCount);
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(z.tickCount > huskTicks[0], "the husk's chunk is ticking");
			helper.assertTrue(g.canBeamReach(z), "clear line from the mouth to the husk");
			g.startBeam(z);
			helper.assertTrue(g.getRoars() == 1, "roars before the beam");
			helper.runAfterDelay(Gojira.BEAM_WINDUP - 4, () -> helper.assertTrue(g.getBeamPulses() == 0 && z.getHealth() == z.getMaxHealth(), "no damage during the wind-up"));
			helper.runAfterDelay(Gojira.BEAM_WINDUP + Gojira.BEAM_FIRE + 4, () -> {
				helper.assertTrue(g.getBeamPulses() == (Gojira.BEAM_FIRE + Gojira.BEAM_PULSE - 1) / Gojira.BEAM_PULSE, "pulses: " + g.getBeamPulses());
				helper.assertTrue(g.getBeamHits() >= 2, "beam hit the husk, hits " + g.getBeamHits());
				helper.assertTrue(z.getHealth() < z.getMaxHealth() || z.isDeadOrDying(), "husk hurt");
				helper.assertTrue(g.getBeamTicks() == 0, "beam finished");
				helper.assertTrue(padIntact(helper, pad, 3) && padIntact(helper, pad.south(16), 4), "no blocks broken");
				clearPad(helper, pad, 3);
				clearPad(helper, pad.south(16), 4);
				helper.succeed();
			});
		});
	}

	/** A wall in the way blocks the beam (it can't shoot through blocks), and it doesn't break the wall. */
	@GameTest(maxTicks = 20)
	public void beamBlockedByWall(GameTestHelper helper) {
		BlockPos pad = skyPad(helper, 200, 3);
		Gojira g = calmGojira(helper, new BlockPos(1, 2, 1));
		Husk z = helper.spawn(EntityTypes.HUSK, new BlockPos(1, 2, 3));
		z.setNoAi(true);
		platform(helper, pad.south(16), 4);
		moveTo(g, pad, 0);
		moveTo(z, pad.south(16), 0);
		// a stone roof over the husk
		for (BlockPos p : BlockPos.betweenClosed(pad.south(16).offset(-3, 3, -3), pad.south(16).offset(3, 3, 3))) {
			helper.getLevel().setBlock(p, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(!g.canBeamReach(z), "roof blocks the beam");
			for (BlockPos p : BlockPos.betweenClosed(pad.south(16).offset(-3, 3, -3), pad.south(16).offset(3, 3, 3))) {
				helper.getLevel().setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
			clearPad(helper, pad, 3);
			clearPad(helper, pad.south(16), 4);
			helper.succeed();
		});
	}

	/** The "Gojira" boss bar shows to nearby players, follows its health, and goes away when you're far or it's gone. */
	@GameTest(maxTicks = 40)
	@SuppressWarnings("removal")
	public void bossBarShowsNearby(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Gojira g = calmGojira(helper, new BlockPos(1, 2, 1));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.snapTo(g.getX() + 10, g.getY(), g.getZ(), 0, 0);
		g.updateBossBarPlayers(level);
		helper.assertTrue(g.getBossEvent().getPlayers().contains(player), "boss bar shows when near");
		helper.assertTrue(g.getBossEvent().getName().getString().equals("Gojira"), "named Gojira, got " + g.getBossEvent().getName().getString());
		g.setHealth(500.0F);
		helper.runAfterDelay(1, () -> {
			helper.assertTrue(g.getBossEvent().getProgress() > 0.49F && g.getBossEvent().getProgress() < 0.6F, "bar follows health: " + g.getBossEvent().getProgress());
			player.snapTo(g.getX() + Gojira.BOSS_BAR_RANGE + 20, g.getY(), g.getZ(), 0, 0);
			g.updateBossBarPlayers(level);
			helper.assertTrue(!g.getBossEvent().getPlayers().contains(player), "boss bar hides when far");
			player.snapTo(g.getX() + 10, g.getY(), g.getZ(), 0, 0);
			g.updateBossBarPlayers(level);
			g.discard();
			helper.assertTrue(g.getBossEvent().getPlayers().isEmpty(), "no bar after it's gone");
			player.discard();
			helper.succeed();
		});
	}

	/** Like GameTestHelper.makeMockServerPlayerInLevel, but in survival (its mock player is always creative, which mobs ignore). */
	private static ServerPlayer survivalPlayer(GameTestHelper helper) {
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "test-survivor"), false);
		ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public net.minecraft.world.level.GameType gameMode() {
				return net.minecraft.world.level.GameType.SURVIVAL;
			}
		};
		var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
		net.minecraft.world.level.GameType.SURVIVAL.updatePlayerAbilities(player.getAbilities());
		return player;
	}

	/** Always angry: it picks a nearby survival player as its target all by itself. */
	@GameTest(maxTicks = 100)
	@SuppressWarnings("removal")
	public void alwaysHostileToPlayers(GameTestHelper helper) {
		Gojira g = helper.spawn(ModEntities.GOJIRA, new BlockPos(1, 2, 1));
		ServerPlayer player = survivalPlayer(helper);
		player.snapTo(g.getX() + 3, g.getY(), g.getZ(), 0, 0);
		helper.succeedWhen(() -> {
			helper.assertTrue(g.getTarget() == player, "targets the player");
			g.discard();
			helper.getLevel().getServer().getPlayerList().remove(player);
		});
	}

	/** Lives in the ocean: underwater it doesn't drown or lose air. */
	@GameTest(maxTicks = 80)
	public void breathesUnderwater(GameTestHelper helper) {
		BlockPos base = helper.absolutePos(new BlockPos(1, 1, 1)).above(240);
		ServerLevel level = helper.getLevel();
		for (BlockPos p : BlockPos.betweenClosed(base.offset(-4, 0, -4), base.offset(4, 18, 4))) {
			level.setBlock(p, Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		Gojira g = calmGojira(helper, new BlockPos(1, 2, 1));
		moveTo(g, base, 0);
		int air = g.getAirSupply();
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(g.isInWater(), "in the water");
			helper.assertTrue(g.getAirSupply() >= air && g.getHealth() == g.getMaxHealth(), "no drowning");
			g.discard();
			for (BlockPos p : BlockPos.betweenClosed(base.offset(-4, 0, -4), base.offset(4, 18, 4))) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
			helper.succeed();
		});
	}
}
