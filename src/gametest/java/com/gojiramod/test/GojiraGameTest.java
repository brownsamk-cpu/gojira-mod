package com.gojiramod.test;

import com.gojiramod.Gojira;
import com.gojiramod.GojiraSkeletonLayout;
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
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.decoration.ArmorStand;
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

	/** Keeps every chunk between two corners loaded and ticking (test spots land at random places, often across chunk edges). */
	private static void forceArea(GameTestHelper helper, BlockPos a, BlockPos b) {
		for (int cx = Math.min(a.getX(), b.getX()) >> 4; cx <= Math.max(a.getX(), b.getX()) >> 4; cx++) {
			for (int cz = Math.min(a.getZ(), b.getZ()) >> 4; cz <= Math.max(a.getZ(), b.getZ()) >> 4; cz++) {
				helper.getLevel().setChunkForced(cx, cz, true);
			}
		}
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
		helper.getLevel().getEntitiesOfClass(Entity.class, AABB.encapsulatingFullBlocks(base.offset(-radius - 4, -4, -radius - 4), base.offset(radius + 4, 20, radius + 4)), // only this pad's height band: other tests are 30 blocks up or down
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

	/** Registered as gojiramod:gojira with its spawn egg, roar sound, lang key; a monster that never despawns, breathes underwater and is 164/3 (~54.67) blocks tall to look at. */
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
		helper.assertTrue(Math.abs(g.getBbHeight() - Gojira.HITBOX_HEIGHT) < 0.01F && Math.abs(Gojira.VISUAL_HEIGHT - 164.0F / 3.0F) < 1.0E-4F, "hitbox and 164/3-block visual size");
		helper.assertTrue(Math.abs(Gojira.VISUAL_SCALE * com.gojiramod.GojiraDimensions.MODEL_HEIGHT_PX / 16.0F - 164.0F / 3.0F) < 0.01F, "model scale makes it 164/3 blocks tall");
		helper.assertTrue(Gojira.MOUTH_UP > 40.0 && Gojira.MOUTH_UP < Gojira.VISUAL_HEIGHT && Gojira.MOUTH_FORWARD > 15.0 && Gojira.MOUTH_FORWARD <= com.gojiramod.GojiraDimensions.FRONT_REACH
				&& Gojira.STOMP_RADIUS > com.gojiramod.GojiraDimensions.FEET_REACH && Gojira.STOMP_RADIUS >= 12.0 * 164.0 / 150.0 - 0.01,
				"mouth and stomp reach match the bigger size");
		helper.assertTrue(g.mouthPos().distanceTo(g.position().add(0, Gojira.MOUTH_UP, 0)) > 15.0, "mouth is in front of the body");
		helper.assertTrue(com.gojiramod.GojiraDimensions.CUBES >= 250 && com.gojiramod.GojiraDimensions.DORSAL_PLATES >= 30 && com.gojiramod.GojiraDimensions.PARTS >= 50,
				"detailed model: lots of cubes, parts and dorsal plates");
		g.discard();
		helper.succeed();
	}

	/** 0.1.1: the spawn egg uses its own detailed 64x64 texture through a plain item model (not the tinted vanilla spawn egg template). */
	@GameTest
	public void spawnEggUsesDetailedTexture(GameTestHelper helper) {
		try (var png = GojiraGameTest.class.getResourceAsStream("/assets/gojiramod/textures/item/gojira_spawn_egg.png");
				var model = GojiraGameTest.class.getResourceAsStream("/assets/gojiramod/models/item/gojira_spawn_egg.json");
				var item = GojiraGameTest.class.getResourceAsStream("/assets/gojiramod/items/gojira_spawn_egg.json")) {
			helper.assertTrue(png != null && model != null && item != null, "egg texture, model and item definition are in the jar");
			java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(png);
			helper.assertTrue(img.getWidth() == 64 && img.getHeight() == 64, "64x64 egg texture, got " + img.getWidth() + "x" + img.getHeight());
			java.util.Set<Integer> colors = new java.util.HashSet<>();
			for (int y = 0; y < 64; y++) {
				for (int x = 0; x < 64; x++) {
					colors.add(img.getRGB(x, y));
				}
			}
			helper.assertTrue(colors.size() > 300, "super detailed (many colors), got " + colors.size());
			String m = new String(model.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
			String i = new String(item.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
			helper.assertTrue(m.contains("\"gojiramod:item/gojira_spawn_egg\"") && !m.contains("template_spawn_egg"), "model uses our own texture");
			helper.assertTrue(i.contains("gojiramod:item/gojira_spawn_egg") && !i.contains("tints"), "item definition is not tinted");
		} catch (java.io.IOException e) {
			throw new RuntimeException(e);
		}
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
	@GameTest(maxTicks = 200)
	public void killCommandStillWorks(GameTestHelper helper) {
		Gojira g = calmGojira(helper, new BlockPos(1, 2, 1));
		helper.assertTrue(!g.isKaijuSwimming(), "not swimming on land");
		g.kill(helper.getLevel());
		helper.assertTrue(g.isDeadOrDying(), "/kill works");
		helper.runAfterDelay(30, () -> {
			// vanilla mobs vanish 20 ticks after dying; Gojira is still slowly toppling over
			helper.assertTrue(!g.isRemoved() && g.isDeadOrDying() && g.deathTime >= 25, "still playing the death animation");
			helper.assertTrue(Gojira.toppleDegrees(g.deathTime) > 10.0F && Gojira.toppleDegrees(g.deathTime) < 90.0F, "partway through toppling over");
			helper.assertTrue(g.getDeathDustBursts() > 0, "dust flies up from its feet");
			helper.assertTrue(g.getHealth() <= 0.0F, "doesn't regenerate while dying");
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(g.isRemoved(), "gone after the death animation");
			helper.assertTrue(g.deathTime >= Gojira.DEATH_TICKS && Gojira.toppleDegrees(g.deathTime) == 90.0F, "it toppled all the way over first");
			helper.assertTrue(g.getDeathDustBursts() >= Gojira.TOPPLE_TICKS / 4, "a big crash of dust when it hits the ground");
			helper.assertTrue(!g.diedUnderwater() && g.getSkeletonBlocks() == -1 && countBones(helper, g.blockPosition(), 70, -2, 4) == 0,
					"died on land: no skeleton");
		});
	}

	/**
	 * 0.1.6 (June's bug: "/kill @e kills him but he regens"): the real /kill command finishes him for good. Once he's been killed,
	 * nothing can bring his health back: not regen, not heal, not setHealth, and not saving + reloading the world mid-animation
	 * (which is what happens when /kill @e also kills you and you respawn far away, or you leave the world while he's toppling).
	 */
	@GameTest(maxTicks = 220)
	public void killCommandIsForever(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Gojira g = calmGojira(helper, new BlockPos(1, 2, 1));
		g.hurtServer(level, level.damageSources().generic(), 30.0F); // a recent sword hit (hurt cooldown running)
		level.getServer().getCommands().performPrefixedCommand(
				level.getServer().createCommandSourceStack().withSuppressedOutput(),
				"kill " + g.getStringUUID());
		helper.assertTrue(g.isDeadOrDying() && g.getHealth() <= 0.0F && g.isKilled(), "/kill command hits him");
		g.heal(500.0F);
		g.setHealth(Gojira.MAX_HEALTH);
		helper.assertTrue(g.getHealth() <= 0.0F && g.isDeadOrDying(), "nothing heals him once he's been killed");
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(!g.isRemoved() && g.getHealth() <= 0.0F, "no regen during the death animation");
			// save him mid-animation and load him back (like his chunk unloading and loading again)
			net.minecraft.world.level.storage.TagValueOutput out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
					net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
			g.saveWithoutId(out);
			net.minecraft.nbt.CompoundTag tag = out.buildResult();
			int deathTimeBefore = g.deathTime;
			g.discard();
			Gojira again = ModEntities.GOJIRA.create(level, net.minecraft.world.entity.EntitySpawnReason.LOAD);
			again.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), tag));
			again.calmForTest();
			level.addFreshEntity(again);
			helper.assertTrue(again.isKilled() && again.getHealth() <= 0.0F && again.isDeadOrDying() && !again.isAlive(),
					"still dead after a reload (health " + again.getHealth() + ")");
			helper.assertTrue(again.deathTime == deathTimeBefore, "death animation picks up where it left off");
			helper.runAfterDelay(10, () -> helper.assertTrue(again.getHealth() <= 0.0F, "no regen after a reload either"));
			helper.succeedWhen(() -> helper.assertTrue(again.isRemoved(), "he finishes dying and is gone"));
		});
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
				helper.assertTrue(g.getStomps() == 1, "stomped (stomps=" + g.getStomps() + ", removed=" + g.isRemoved() + ", y=" + g.getY() + ", pad=" + pad.getY() + ")");
				helper.assertTrue(near.getHealth() < near.getMaxHealth(), "the stomp hit the pig near its feet");
				helper.assertTrue(near.position().subtract(start).horizontalDistance() > 0.5 || near.getDeltaMovement().y > 0.05 || !near.onGround(),
						"knocked back");
				helper.assertTrue(far.getHealth() == far.getMaxHealth(), "a pig ~20 blocks away is safe");
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

	/** Claw swipe hits what's in front of it (not behind), and the tail whip hits what's behind it. */
	@GameTest(maxTicks = 100)
	public void meleeAttacksHurt(GameTestHelper helper) {
		BlockPos pad = skyPad(helper, 50, 20);
		Gojira g = calmGojira(helper, new BlockPos(1, 2, 1));
		Pig front = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(1, 2, 2));
		Pig back = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(2, 2, 1));
		moveTo(g, pad, 0); // faces south (+z)
		moveTo(front, pad.south(10), 0);
		moveTo(back, pad.north(14), 0);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(g.isInFront(front) && !g.isInFront(back), "one pig in front, one behind");
			g.startSwipe();
			helper.assertTrue(g.getSwipes() == 0 && front.getHealth() == front.getMaxHealth(), "the arm is still winding up");
			helper.runAfterDelay(Gojira.SWIPE_WINDUP + 2, () -> {
				helper.assertTrue(g.getSwipes() == 1 && front.getHealth() < front.getMaxHealth(), "the claw swipe hit the pig in front");
				helper.assertTrue(front.getHealth() > 0.0F, "moderate damage: a pig survives one swipe");
				helper.assertTrue(back.getHealth() == back.getMaxHealth(), "the swipe doesn't hit behind it");
				g.startWhip();
				helper.runAfterDelay(Gojira.WHIP_WINDUP + 2, () -> {
					helper.assertTrue(g.getWhips() == 1 && back.getHealth() < back.getMaxHealth(), "the tail whip hit the pig behind");
					helper.assertTrue(g.getMeleeHits() == 2, "two melee hits");
					helper.assertTrue(padIntact(helper, pad, 20), "no blocks broken");
					clearPad(helper, pad, 20);
					helper.succeed();
				});
			});
		});
	}

	/** On its own it picks the claw swipe for a target just out of stomp reach in front of it. */
	@GameTest(maxTicks = 100)
	public void swipesWhenClose(GameTestHelper helper) {
		BlockPos pad = skyPad(helper, 110, 20);
		Gojira g = calmGojira(helper, new BlockPos(1, 2, 1));
		Pig pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(1, 2, 2));
		moveTo(g, pad, 0);
		moveTo(pig, pad.south(16), 0);
		helper.runAfterDelay(2, () -> g.setTarget(pig));
		helper.succeedWhen(() -> {
			helper.assertTrue(g.getSwipes() >= 1 && pig.getHealth() < pig.getMaxHealth(), "swiped the pig");
			helper.assertTrue(g.getBeamPulses() == 0 && g.getStomps() == 0, "used the swipe (not the beam or a stomp)");
			clearPad(helper, pad, 20);
		});
	}

	/** Angry at every living mob, not just players: goes for the nearest cow, ignoring armor stands and other Gojiras. */
	@GameTest(maxTicks = 100)
	public void targetsNearbyMobs(GameTestHelper helper) {
		BlockPos pad = skyPad(helper, 170, 12);
		Gojira g = helper.spawn(ModEntities.GOJIRA, new BlockPos(1, 2, 1));
		Gojira other = calmGojira(helper, new BlockPos(2, 2, 1));
		ArmorStand stand = helper.spawn(EntityTypes.ARMOR_STAND, new BlockPos(1, 2, 2));
		Cow cow = helper.spawnWithNoFreeWill(EntityTypes.COW, new BlockPos(1, 2, 3));
		moveTo(g, pad, 0);
		moveTo(other, pad.west(9), 0);
		moveTo(stand, pad.east(4), 0);
		moveTo(cow, pad.north(7), 0);
		helper.assertTrue(Gojira.isPrey(cow) && !Gojira.isPrey(stand) && !Gojira.isPrey(other), "cows are prey; armor stands and Gojiras are not");
		helper.succeedWhen(() -> {
			helper.assertTrue(g.getTarget() == cow, "targets the nearby cow");
			clearPad(helper, pad, 12);
		});
	}

	/** 0.1.5: killed underwater, it topples, and when the death animation ends it leaves a big bone-block skeleton on the sea floor. */
	// its own batch: the 70-block skeleton must not land on another test's blocks
	@GameTest(environment = "gojiramod:skeleton", maxTicks = 220)
	public void underwaterDeathLeavesSkeleton(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos feet = helper.absolutePos(new BlockPos(1, 1, 1)).above(285);
		// a stone sea floor big enough for the whole skeleton (it falls east and faces south), a deep pool around Gojira,
		// and one stone block it must NOT replace, plus some seagrass it may replace
		BlockPos lo = feet.offset(-3, -1, GojiraSkeletonLayout.MIN_B - 3), hi = feet.offset(GojiraSkeletonLayout.MAX_A + 3, -1, GojiraSkeletonLayout.MAX_B + 3);
		forceArea(helper, lo, hi); // the skeleton skips unloaded chunks, so keep the whole sea floor loaded
		for (BlockPos p : BlockPos.betweenClosed(lo, hi)) {
			level.setBlock(p, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		for (BlockPos p : BlockPos.betweenClosed(feet.offset(-4, 0, -4), feet.offset(4, 9, 4))) {
			level.setBlock(p, Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		BlockPos rock = feet.offset(1, 0, 0); // under the bottom of its foot (a=1, b=0)
		level.setBlock(rock, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
		BlockPos grass = feet.offset(2, 0, 0); // at the bottom of its shin (a=2, b=0), in the pool
		level.setBlock(grass, Blocks.SEAGRASS.defaultBlockState(), Block.UPDATE_CLIENTS);
		Gojira g = calmGojira(helper, new BlockPos(1, 2, 1));
		moveTo(g, feet, 0); // faces south
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(g.isInWater(), "deep in the water");
			g.kill(level);
			helper.assertTrue(g.diedUnderwater(), "it knows it died underwater");
			helper.runAfterDelay(Gojira.DEATH_TICKS - 10, () -> helper.assertTrue(!g.isRemoved() && countBones(helper, feet, 70, -1, 4) == 0,
					"no bones until the death animation finishes"));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(g.isRemoved(), "the death animation finished");
			int bones = countBones(helper, feet, 70, -1, 4);
			helper.assertTrue(g.getSkeletonBlocks() >= GojiraSkeletonLayout.COUNT * 9 / 10 && bones == g.getSkeletonBlocks(),
					"a skeleton of bone blocks on the sea floor (placed " + g.getSkeletonBlocks() + ", found " + bones + ")");
			helper.assertTrue(level.getBlockState(rock).is(Blocks.COBBLESTONE), "it never replaces solid blocks");
			helper.assertTrue(level.getBlockState(grass).is(Blocks.BONE_BLOCK), "seagrass may be replaced");
			for (BlockPos p : BlockPos.betweenClosed(lo, hi)) {
				helper.assertTrue(level.getBlockState(p).is(Blocks.STONE), "the sea floor is untouched");
			}
			helper.assertTrue(countBones(helper, feet, 70, 3, 3) == 0, "everything lies within 3 blocks of the floor");
			// skull at the far end (a=42, b=15 is solid skull, layer 0) and the tail tip at the other
			helper.assertTrue(level.getBlockState(feet.offset(41, 0, 16)).is(Blocks.BONE_BLOCK), "the skull lies where its head fell");
			for (BlockPos p : BlockPos.betweenClosed(lo.above(), hi.above(12))) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
			for (BlockPos p : BlockPos.betweenClosed(lo, hi)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		});
	}

	/** Counts bone blocks within {@code radius} blocks sideways of {@code center}, from {@code dyLo} to {@code dyHi} blocks up. */
	private static int countBones(GameTestHelper helper, BlockPos center, int radius, int dyLo, int dyHi) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(center.offset(-radius, dyLo, -radius), center.offset(radius, dyHi, radius))) {
			if (helper.getLevel().getBlockState(p).is(Blocks.BONE_BLOCK)) {
				n++;
			}
		}
		return n;
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
			helper.assertTrue(g.isKaijuSwimming(), "deep water: it swims (the swim animation flag is on)");
			helper.assertTrue(g.getAirSupply() >= air && g.getHealth() == g.getMaxHealth(), "no drowning");
			g.discard();
			for (BlockPos p : BlockPos.betweenClosed(base.offset(-4, 0, -4), base.offset(4, 18, 4))) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
			helper.succeed();
		});
	}
}
