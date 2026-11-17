package com.gojiramod.test;

import com.gojiramod.Gojira;
import com.gojiramod.GojiraDimensions;
import com.gojiramod.ModEntities;
import com.gojiramod.ModItems;
import com.gojiramod.ShowaDimensions;
import com.gojiramod.ShowaGodzilla;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 0.2.0: Showa Godzilla (June's hero). These run in their own batch (test environment gojiramod:showa), after the Gojira
 * tests, so no always-angry Gojira is around. Tests that attack or fly use their own stone pads high in the sky, about
 * 50 blocks apart, so their attacks can't reach each other.
 */
public class ShowaGodzillaGameTest {
	private static final String ENV = "gojiramod:showa";

	private static ShowaGodzilla calmShowa(GameTestHelper helper, BlockPos rel) {
		ShowaGodzilla g = helper.spawn(ModEntities.SHOWA_GODZILLA, rel);
		g.calmForTest();
		return g;
	}

	private static void fill(GameTestHelper helper, BlockPos a, BlockPos b, Block block) {
		for (BlockPos p : BlockPos.betweenClosed(a, b)) {
			helper.getLevel().setBlock(p, block.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	/** A stone pad {@code up} blocks above this test; returns the block above its center. */
	private static BlockPos skyPad(GameTestHelper helper, int up, int radius) {
		BlockPos base = helper.absolutePos(new BlockPos(1, 1, 1)).above(up);
		fill(helper, base.offset(-radius, 0, -radius), base.offset(radius, 0, radius), Blocks.STONE);
		return base.above();
	}

	/** A stone platform under {@code top}. */
	private static void platform(GameTestHelper helper, BlockPos top, int radius) {
		fill(helper, top.below().offset(-radius, 0, -radius), top.below().offset(radius, 0, radius), Blocks.STONE);
	}

	private static void clearPad(GameTestHelper helper, BlockPos top, int radius) {
		BlockPos base = top.below();
		helper.getLevel().getEntitiesOfClass(Entity.class, AABB.encapsulatingFullBlocks(base.offset(-radius - 4, -4, -radius - 4), base.offset(radius + 4, 24, radius + 4)),
				e -> !(e instanceof ServerPlayer)).forEach(Entity::discard);
		fill(helper, base.offset(-radius, 0, -radius), base.offset(radius, 0, radius), Blocks.AIR);
	}

	private static void forceTicking(GameTestHelper helper, BlockPos pos) {
		helper.getLevel().setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
	}

	private static void moveTo(Entity e, BlockPos pos, float yaw) {
		e.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0);
		if (e instanceof LivingEntity l) {
			l.yBodyRot = yaw;
			l.yHeadRot = yaw;
		}
	}

	private static Husk stillHusk(GameTestHelper helper) {
		Husk z = helper.spawn(EntityTypes.HUSK, new BlockPos(1, 2, 3)); // husks don't burn in the sun
		z.setNoAi(true);
		z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		return z;
	}

	private static ServerPlayer survivalPlayer(GameTestHelper helper) {
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "test-friend"), false);
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

	private static int countBones(GameTestHelper helper, BlockPos center, int radius, int dyLo, int dyHi) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(center.offset(-radius, dyLo, -radius), center.offset(radius, dyHi, radius))) {
			if (helper.getLevel().getBlockState(p).is(Blocks.BONE_BLOCK)) {
				n++;
			}
		}
		return n;
	}

	/** Registered with his detailed spawn egg; a hero (not a monster), same 164/3-block size as Gojira, his own model. */
	@GameTest(environment = ENV)
	public void showaRegistered(GameTestHelper helper) {
		ShowaGodzilla g = calmShowa(helper, new BlockPos(1, 2, 1));
		helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.getKey(ModEntities.SHOWA_GODZILLA).toString().equals("gojiramod:showa_godzilla"), "entity id");
		helper.assertTrue(g.getType().getDescriptionId().equals("entity.gojiramod.showa_godzilla"), "lang key");
		helper.assertTrue(BuiltInRegistries.ITEM.getKey(ModItems.SHOWA_GODZILLA_SPAWN_EGG).toString().equals("gojiramod:showa_godzilla_spawn_egg")
				&& ModItems.SHOWA_GODZILLA_SPAWN_EGG instanceof SpawnEggItem, "spawn egg");
		helper.assertTrue(!Monster.class.isInstance(g) && !Enemy.class.isInstance(g) && ModEntities.SHOWA_GODZILLA.getCategory() == MobCategory.MISC,
				"a hero, not a monster (iron golems leave him alone)");
		helper.assertTrue(g.getMaxHealth() == Gojira.MAX_HEALTH && g.isPersistenceRequired() && !g.removeWhenFarAway(1.0E8), "1000 health, never despawns");
		helper.assertTrue(g.canBreatheUnderwater() && g.fireImmune(), "breathes underwater, fireproof");
		helper.assertTrue(Math.abs(ShowaGodzilla.VISUAL_HEIGHT - Gojira.VISUAL_HEIGHT) < 1.0E-4F && Math.abs(ShowaGodzilla.VISUAL_HEIGHT - 164.0F / 3.0F) < 1.0E-4F,
				"the same 164/3-block height as Gojira");
		helper.assertTrue(Math.abs(ShowaDimensions.VISUAL_SCALE * ShowaDimensions.MODEL_HEIGHT_PX / 16.0F - 164.0F / 3.0F) < 0.01F, "model scale makes him 164/3 blocks tall");
		helper.assertTrue(ShowaDimensions.CUBES >= 150 && ShowaDimensions.DORSAL_PLATES >= 20 && ShowaDimensions.PARTS >= 40, "detailed model");
		helper.assertTrue(ShowaDimensions.CUBES != GojiraDimensions.CUBES && ShowaDimensions.PARTS != GojiraDimensions.PARTS, "his own model, not Gojira's");
		helper.assertTrue(ShowaGodzilla.MOUTH_UP > 35.0 && ShowaGodzilla.MOUTH_UP < ShowaGodzilla.VISUAL_HEIGHT && ShowaGodzilla.STOMP_RADIUS > ShowaDimensions.FEET_REACH,
				"mouth and stomp reach match his size");
		try (var png = ShowaGodzillaGameTest.class.getResourceAsStream("/assets/gojiramod/textures/item/showa_godzilla_spawn_egg.png");
				var model = ShowaGodzillaGameTest.class.getResourceAsStream("/assets/gojiramod/models/item/showa_godzilla_spawn_egg.json");
				var item = ShowaGodzillaGameTest.class.getResourceAsStream("/assets/gojiramod/items/showa_godzilla_spawn_egg.json");
				var skin = ShowaGodzillaGameTest.class.getResourceAsStream("/assets/gojiramod/textures/entity/showa_godzilla.png")) {
			helper.assertTrue(png != null && model != null && item != null && skin != null, "egg texture, model, item definition and skin are in the jar");
			java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(png);
			helper.assertTrue(img.getWidth() == 64 && img.getHeight() == 64, "64x64 egg texture");
			java.util.Set<Integer> colors = new java.util.HashSet<>();
			for (int y = 0; y < 64; y++) {
				for (int x = 0; x < 64; x++) {
					colors.add(img.getRGB(x, y));
				}
			}
			helper.assertTrue(colors.size() > 300, "super detailed egg (many colors), got " + colors.size());
			String m = new String(model.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
			String i = new String(item.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
			helper.assertTrue(m.contains("\"gojiramod:item/showa_godzilla_spawn_egg\"") && !i.contains("tints"), "his own untinted egg texture");
		} catch (java.io.IOException e) {
			throw new RuntimeException(e);
		}
		g.discard();
		helper.succeed();
	}

	/** Can't be beaten by normal damage (health never below 1) and heals 40 health a second, just like Gojira. */
	@GameTest(environment = ENV, maxTicks = 60)
	public void showaCannotDieAndRegens(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ShowaGodzilla g = calmShowa(helper, new BlockPos(1, 2, 1));
		g.hurtServer(level, level.damageSources().generic(), 100000.0F);
		g.setInvulnerableTime(0);
		g.hurtServer(level, level.damageSources().lava(), 5000.0F);
		g.setHealth(-50.0F);
		helper.assertTrue(g.isAlive() && !g.isDeadOrDying() && g.getHealth() >= ShowaGodzilla.MIN_HEALTH, "survives giant hits");
		g.setHealth(100.0F);
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(g.isAlive() && g.getHealth() >= 100.0F + ShowaGodzilla.REGEN_PER_TICK * 15, "healed in one second: " + g.getHealth());
			g.discard();
			helper.succeed();
		});
	}

	/** HERO: he goes for the hostile husk, and never for the player, villager, iron golem or cow standing even closer. */
	@GameTest(environment = ENV, maxTicks = 100)
	@SuppressWarnings("removal")
	public void heroTargetsMonstersNotPeople(GameTestHelper helper) {
		BlockPos pad = skyPad(helper, 20, 12);
		ShowaGodzilla g = helper.spawn(ModEntities.SHOWA_GODZILLA, new BlockPos(1, 2, 1));
		ServerPlayer player = survivalPlayer(helper);
		Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(1, 2, 2));
		IronGolem golem = helper.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, new BlockPos(2, 2, 1));
		Cow cow = helper.spawnWithNoFreeWill(EntityTypes.COW, new BlockPos(2, 2, 2));
		Husk husk = stillHusk(helper);
		moveTo(g, pad, 0);
		player.snapTo(pad.getX() + 4.5, pad.getY(), pad.getZ() + 0.5, 0, 0);
		moveTo(villager, pad.west(5), 0);
		moveTo(golem, pad.north(6), 0);
		moveTo(cow, pad.east(5).north(3), 0);
		moveTo(husk, pad.south(10), 0);
		helper.assertTrue(ShowaGodzilla.isHeroTarget(husk), "hostile mobs are his targets");
		helper.assertTrue(!ShowaGodzilla.isHeroTarget(player) && !ShowaGodzilla.isHeroTarget(villager) && !ShowaGodzilla.isHeroTarget(golem)
				&& !ShowaGodzilla.isHeroTarget(cow) && !ShowaGodzilla.isHeroTarget(g), "never players, villagers, iron golems, animals or himself");
		Gojira gojira = helper.spawnWithNoFreeWill(ModEntities.GOJIRA, new BlockPos(1, 2, 1));
		helper.assertTrue(ShowaGodzilla.isHeroTarget(gojira), "Gojira is a monster he fights");
		gojira.discard();
		// even if they hit him by accident, he won't turn on them
		g.setTarget(player);
		g.setTarget(villager);
		helper.assertTrue(g.getTarget() == null, "can't be made to target a player or a villager");
		helper.onEachTick(() -> {
			LivingEntity t = g.getTarget();
			helper.assertTrue(t == null || t == husk, "only ever targets the husk, got " + t);
			helper.assertTrue(player.getHealth() == player.getMaxHealth() && villager.getHealth() == villager.getMaxHealth()
					&& golem.getHealth() == golem.getMaxHealth() && cow.getHealth() == cow.getMaxHealth(), "nobody friendly gets hurt");
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(g.getTarget() == husk, "targets the husk");
			clearPad(helper, pad, 12);
			helper.getLevel().getServer().getPlayerList().remove(player);
		});
	}

	/** His stomp, claw swipe and beam only hurt hostile mobs: a villager, an iron golem and a pig right next to him are safe. */
	@GameTest(environment = ENV, maxTicks = 100)
	public void heroAttacksSpareFriends(GameTestHelper helper) {
		BlockPos pad = skyPad(helper, 70, 20);
		ShowaGodzilla g = calmShowa(helper, new BlockPos(1, 2, 1));
		Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(1, 2, 2));
		IronGolem golem = helper.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, new BlockPos(2, 2, 1));
		Pig pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(2, 2, 2));
		Husk near = stillHusk(helper), front = stillHusk(helper);
		moveTo(g, pad, 0); // faces south (+z)
		moveTo(villager, pad.south(6), 0);
		moveTo(golem, pad.east(7), 0);
		moveTo(pig, pad.west(7), 0);
		moveTo(near, pad.north(7), 0);
		moveTo(front, pad.south(17), 0);
		helper.runAfterDelay(2, () -> {
			g.startStomp();
			helper.runAfterDelay(ShowaGodzilla.STOMP_WINDUP + 3, () -> {
				helper.assertTrue(g.getStomps() == 1 && near.getHealth() < near.getMaxHealth(), "the stomp hit the husk by his feet");
				helper.assertTrue(villager.getHealth() == villager.getMaxHealth() && golem.getHealth() == golem.getMaxHealth() && pig.getHealth() == pig.getMaxHealth(),
						"the stomp spared the villager, golem and pig");
				g.startSwipe();
				helper.runAfterDelay(ShowaGodzilla.SWIPE_WINDUP + 2, () -> {
					helper.assertTrue(g.getSwipes() == 1 && front.getHealth() < front.getMaxHealth(), "the swipe hit the husk in front");
					helper.assertTrue(villager.getHealth() == villager.getMaxHealth(), "the swipe spared the villager in front of him");
					clearPad(helper, pad, 20);
					helper.succeed();
				});
			});
		});
	}

	/** JET FLIGHT: a target far away across the sky: he takes off, flies there tail first with the beam trail, and lands. */
	@GameTest(environment = ENV, maxTicks = 400)
	public void fliesToFarTarget(GameTestHelper helper) {
		BlockPos pad = skyPad(helper, 120, 4);
		BlockPos far = pad.south(56);
		platform(helper, far, 22);
		for (int d = -16; d <= 80; d += 16) {
			forceTicking(helper, pad.south(d));
			forceTicking(helper, pad.south(d).east(22));
			forceTicking(helper, pad.south(d).west(22));
		}
		ShowaGodzilla g = calmShowa(helper, new BlockPos(1, 2, 1));
		Husk husk = stillHusk(helper);
		moveTo(g, pad, 0);
		moveTo(husk, far, 0);
		boolean[] sawFlying = new boolean[2];
		helper.onEachTick(() -> {
			if (g.isJetFlying()) {
				sawFlying[0] = true;
				Vec3 toTarget = new Vec3(husk.getX() - g.getX(), 0, husk.getZ() - g.getZ()).normalize();
				Vec3 facing = Vec3.directionFromRotation(0, g.yBodyRot);
				sawFlying[1] |= g.isNoGravity() && facing.dot(toTarget) < -0.8 && !g.onGround();
			}
		});
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(g.wantsToFly(husk), "far away (56 blocks) and across the sky: he wants to fly");
			g.setTarget(husk);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(g.getFlights() >= 1 && sawFlying[0], "took off");
			helper.assertTrue(sawFlying[1], "flew tail first (facing away from where he's going), no gravity");
			helper.assertTrue(g.getFlightTrailTicks() > 10, "blasted the beam backward the whole way");
			helper.assertTrue(g.getLandings() >= 1 && !g.isJetFlying() && !g.isNoGravity() && g.onGround(), "landed again");
			double hd = Math.hypot(husk.getX() - g.getX(), husk.getZ() - g.getZ());
			helper.assertTrue(hd < ShowaGodzilla.FLY_LAND_DISTANCE + 6.0 && g.getY() > far.getY() - 1.0, "landed near the target, on its platform (" + hd + ")");
			clearPad(helper, pad, 4);
			clearPad(helper, far, 22);
		});
	}

	/** He also flies across a gap (or up a cliff), but walks when there's a bridge and it's not far. */
	@GameTest(environment = ENV, maxTicks = 40)
	public void fliesAcrossGapsAndCliffs(GameTestHelper helper) {
		BlockPos pad = skyPad(helper, 45, 4);
		BlockPos other = pad.south(26);
		platform(helper, other, 4);
		ShowaGodzilla g = calmShowa(helper, new BlockPos(1, 2, 1));
		Husk husk = stillHusk(helper);
		moveTo(g, pad, 0);
		moveTo(husk, other, 0);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(g.gapBetween(husk) && g.wantsToFly(husk), "a big drop between them: he flies across the gap");
			fill(helper, pad.below().offset(-2, 0, 0), other.below().offset(2, 0, 0), Blocks.STONE); // a bridge
			helper.assertTrue(!g.gapBetween(husk) && !g.wantsToFly(husk), "with a bridge, 26 blocks away: he walks");
			moveTo(husk, other.above(20), 0);
			helper.assertTrue(g.wantsToFly(husk), "the target up a 20-block cliff: he flies up");
			moveTo(husk, pad.south(10), 0);
			helper.assertTrue(!g.wantsToFly(husk), "close by: no flying");
			fill(helper, pad.below().offset(-2, 0, 0), other.below().offset(2, 0, 0), Blocks.AIR);
			clearPad(helper, pad, 4);
			clearPad(helper, other, 4);
			helper.succeed();
		});
	}

	/** DROP KICK: crouch, jump, and both feet hit the husk in front of him (not the villager next to it). */
	@GameTest(environment = ENV, maxTicks = 100)
	public void dropKickHits(GameTestHelper helper) {
		BlockPos pad = skyPad(helper, 170, 30);
		ShowaGodzilla g = calmShowa(helper, new BlockPos(1, 2, 1));
		Husk husk = stillHusk(helper);
		Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(1, 2, 2));
		moveTo(g, pad, 0);
		moveTo(husk, pad.south(22), 0);
		moveTo(villager, pad.south(20).east(3), 0);
		helper.runAfterDelay(2, () -> {
			double startZ = g.getZ();
			g.startDropKick();
			helper.assertTrue(g.getKickTicks() > 0 && g.getKicks() == 0 && g.isBusyAttacking(), "crouching for the kick");
			helper.runAfterDelay(ShowaGodzilla.KICK_LAUNCH + 3, () -> helper.assertTrue(!g.onGround() || g.getDeltaMovement().y > 0.0, "jumped"));
			helper.runAfterDelay(ShowaGodzilla.KICK_HIT + 2, () -> {
				helper.assertTrue(g.getKicks() == 1 && g.getKickHits() >= 1 && husk.getHealth() < husk.getMaxHealth(), "both feet hit the husk");
				helper.assertTrue(husk.getHealth() <= husk.getMaxHealth() - ShowaGodzilla.KICK_DAMAGE + 0.5F || husk.isDeadOrDying(), "a big kick");
				helper.assertTrue(villager.getHealth() == villager.getMaxHealth(), "the villager is safe");
				helper.assertTrue(g.getZ() > startZ + 3.0, "he leapt forward");
			});
			helper.runAfterDelay(ShowaGodzilla.KICK_ANIM + 4, () -> {
				helper.assertTrue(g.getKickTicks() == 0, "kick finished, back on his feet");
				helper.assertTrue(ShowaGodzilla.kickTiltDegrees(0.5F) > 60.0F && ShowaGodzilla.kickTiltDegrees(1.0F) == 0.0F, "tips back for the kick, then stands up");
				clearPad(helper, pad, 30);
				helper.succeed();
			});
		});
	}

	/** On his own he picks the drop kick for a hostile mob in front of him that's just too far for a claw swipe. */
	@GameTest(environment = ENV, maxTicks = 120)
	public void picksDropKickAtMidRange(GameTestHelper helper) {
		BlockPos pad = skyPad(helper, 220, 30);
		ShowaGodzilla g = calmShowa(helper, new BlockPos(1, 2, 1));
		Husk husk = stillHusk(helper);
		moveTo(g, pad, 0);
		moveTo(husk, pad.south(26), 0);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(!g.wantsToFly(husk), "close enough to walk");
			g.setTarget(husk);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(g.getKicks() >= 1 && husk.getHealth() < husk.getMaxHealth(), "drop-kicked the husk");
			helper.assertTrue(g.getBeamPulses() == 0 && g.getStomps() == 0 && g.getSwipes() == 0, "the drop kick was his first move");
			clearPad(helper, pad, 30);
		});
	}

	/** VICTORY DANCE: after he defeats a mob he does his happy dance (and not before). */
	@GameTest(environment = ENV, maxTicks = 140)
	public void victoryDanceAfterDefeat(GameTestHelper helper) {
		BlockPos pad = skyPad(helper, 270, 20);
		ShowaGodzilla g = calmShowa(helper, new BlockPos(1, 2, 1));
		Husk husk = stillHusk(helper);
		moveTo(g, pad, 0);
		moveTo(husk, pad.south(12), 0);
		husk.setHealth(2.0F);
		boolean[] danced = new boolean[1];
		helper.onEachTick(() -> danced[0] |= g.getDanceTicks() > 0 && g.isBusyAttacking());
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(g.getDances() == 0, "no dance yet");
			g.startSwipe();
			helper.runAfterDelay(ShowaGodzilla.SWIPE_WINDUP + 2, () -> helper.assertTrue(husk.isDeadOrDying() && g.getDefeats() == 1, "the swipe defeated the husk"));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(g.getDances() == 1 && danced[0], "did the victory dance");
			helper.assertTrue(g.getDanceTicks() == 0, "dance finished (3 seconds)");
			clearPad(helper, pad, 20);
		});
	}

	/** /kill is forever (the 0.1.6 fix): no heal, no regen, still dead after a reload; he falls over and is gone. No bones on land. */
	@GameTest(environment = ENV, maxTicks = 220)
	public void killCommandIsForever(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ShowaGodzilla g = calmShowa(helper, new BlockPos(1, 2, 1));
		g.hurtServer(level, level.damageSources().generic(), 30.0F);
		level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withSuppressedOutput(), "kill " + g.getStringUUID());
		helper.assertTrue(g.isDeadOrDying() && g.getHealth() <= 0.0F && g.isKilled(), "/kill command gets him");
		g.heal(500.0F);
		g.setHealth(ShowaGodzilla.MAX_HEALTH);
		helper.assertTrue(g.getHealth() <= 0.0F && g.isDeadOrDying(), "nothing heals him once he's been killed");
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(!g.isRemoved() && g.getHealth() <= 0.0F && g.getDeathDustBursts() > 0, "falling over, no regen");
			net.minecraft.world.level.storage.TagValueOutput out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
					net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
			g.saveWithoutId(out);
			net.minecraft.nbt.CompoundTag tag = out.buildResult();
			int deathTimeBefore = g.deathTime;
			BlockPos where = g.blockPosition();
			g.discard();
			ShowaGodzilla again = ModEntities.SHOWA_GODZILLA.create(level, net.minecraft.world.entity.EntitySpawnReason.LOAD);
			again.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), tag));
			again.calmForTest();
			level.addFreshEntity(again);
			helper.assertTrue(again.isKilled() && again.getHealth() <= 0.0F && !again.isAlive(), "still dead after a reload");
			helper.assertTrue(again.deathTime == deathTimeBefore, "falling over picks up where it left off");
			helper.runAfterDelay(10, () -> helper.assertTrue(again.getHealth() <= 0.0F, "no regen after a reload either"));
			helper.succeedWhen(() -> {
				helper.assertTrue(again.isRemoved(), "he finishes falling over and is gone");
				helper.assertTrue(countBones(helper, where, 70, -2, 4) == 0, "no bones");
			});
		});
	}

	/** In deep water he swims; killed there he just falls over and is gone: NO bones on the sea floor (unlike Gojira). */
	@GameTest(environment = ENV, maxTicks = 220)
	public void noBonesWhenKilledInWater(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos feet = helper.absolutePos(new BlockPos(1, 1, 1)).above(95);
		BlockPos lo = feet.offset(-12, -1, -12), hi = feet.offset(12, -1, 12);
		fill(helper, lo, hi, Blocks.STONE);
		fill(helper, feet.offset(-5, 0, -5), feet.offset(5, 13, 5), Blocks.WATER);
		ShowaGodzilla g = calmShowa(helper, new BlockPos(1, 2, 1));
		moveTo(g, feet, 0);
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(g.isInWater() && g.isKaijuSwimming(), "swimming in deep water");
			helper.assertTrue(g.getHealth() == g.getMaxHealth(), "no drowning");
			g.kill(level);
			helper.assertTrue(g.isDeadOrDying(), "/kill works underwater");
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(g.isRemoved() && g.deathTime >= ShowaGodzilla.DEATH_TICKS, "fell over, then gone");
			helper.assertTrue(countBones(helper, feet, 70, -1, 14) == 0, "no bones in the water");
			for (BlockPos p : BlockPos.betweenClosed(lo, hi)) {
				helper.assertTrue(level.getBlockState(p).is(Blocks.STONE), "the sea floor is untouched");
			}
			fill(helper, feet.offset(-5, 0, -5), feet.offset(5, 13, 5), Blocks.AIR);
			fill(helper, lo, hi, Blocks.AIR);
		});
	}
}
