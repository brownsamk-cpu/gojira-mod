package com.gojiramod;

import java.util.EnumSet;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Showa Godzilla (gojiramod:showa_godzilla), June's hero kaiju (0.2.0): a big, friendly, charcoal-grey kaiju inspired by
 * the 1960s/70s films, drawn as an original blocky design (round face, big eyes, white rounded back plates, big belly).
 *
 * <p>He's a HERO OF HUMANITY: he never targets or hurts players, villagers, iron golems or animals. He only goes after
 * hostile mobs ({@link Enemy}: zombies, skeletons, creepers, slimes, ... and Gojira), nearest first, up to 64 blocks away,
 * and every one of his attacks only damages hostile mobs, so his stomp can't hurt you even if you're standing on his toes.
 *
 * <p>Same size and powers as Gojira: 164/3 = 54.67 blocks tall, the stomp, claw swipe, tail whip, glowing white beam and
 * the same roar, he swims, and he can't be beaten (fast regen, health never below 1). New for him:
 * <ul>
 * <li>JET FLIGHT: when his target is far away (more than {@link #FLY_START_DISTANCE} blocks), across a gap, or up on a
 * high cliff, he blasts his beam out BACKWARD like a rocket and flies tail-first toward it (body flat, a trail of glowing
 * beam particles behind him), then lands again {@link #FLY_LAND_DISTANCE} blocks away.</li>
 * <li>DROP KICK: for a target in front of him a bit too far for a swipe, he crouches, jumps and kicks with both feet.</li>
 * <li>VICTORY DANCE: after he defeats a mob he does a happy little 3-second dance.</li>
 * </ul>
 *
 * <p>Death: only {@code /kill} (or the void). He just falls over and is gone after 5 seconds. No bones, ever (land or water).
 * Once killed he stays dead (the 0.1.6 "killed forever" fix: no regen, not even after a reload).
 */
public class ShowaGodzilla extends PathfinderMob {
	// ---- size (measured from the model by tools/showa.py) ----
	public static final float VISUAL_HEIGHT = ShowaDimensions.VISUAL_HEIGHT;
	public static final float SIZE_FACTOR = VISUAL_HEIGHT / 50.0F;
	public static final float HITBOX_WIDTH = Gojira.HITBOX_WIDTH, HITBOX_HEIGHT = Gojira.HITBOX_HEIGHT;
	public static final double MOUTH_UP = ShowaDimensions.MOUTH_UP, MOUTH_FORWARD = ShowaDimensions.MOUTH_FORWARD;

	// ---- can't be beaten (same as Gojira) ----
	public static final float MAX_HEALTH = Gojira.MAX_HEALTH, MIN_HEALTH = Gojira.MIN_HEALTH, REGEN_PER_TICK = Gojira.REGEN_PER_TICK;

	// ---- the same attacks as Gojira ----
	public static final double STOMP_RADIUS = Math.max(12.0 * SIZE_FACTOR, ShowaDimensions.FEET_REACH + 0.5);
	public static final float STOMP_DAMAGE = Gojira.STOMP_DAMAGE;
	public static final int STOMP_WINDUP = Gojira.STOMP_WINDUP, STOMP_COOLDOWN = Gojira.STOMP_COOLDOWN;
	public static final double BEAM_RANGE = Gojira.BEAM_RANGE, BEAM_MIN_DISTANCE = Gojira.BEAM_MIN_DISTANCE;
	public static final float BEAM_DAMAGE = Gojira.BEAM_DAMAGE;
	public static final int BEAM_WINDUP = Gojira.BEAM_WINDUP, BEAM_FIRE = Gojira.BEAM_FIRE, BEAM_PULSE = Gojira.BEAM_PULSE, BEAM_COOLDOWN = Gojira.BEAM_COOLDOWN;
	public static final double SWIPE_RANGE = Math.max(Gojira.SWIPE_RANGE, STOMP_RADIUS + 2.0), WHIP_RANGE = Gojira.WHIP_RANGE;
	public static final float SWIPE_DAMAGE = Gojira.SWIPE_DAMAGE, WHIP_DAMAGE = Gojira.WHIP_DAMAGE;
	public static final int SWIPE_WINDUP = Gojira.SWIPE_WINDUP, SWIPE_ANIM = Gojira.SWIPE_ANIM, WHIP_WINDUP = Gojira.WHIP_WINDUP, WHIP_ANIM = Gojira.WHIP_ANIM,
			MELEE_COOLDOWN = Gojira.MELEE_COOLDOWN;

	// ---- NEW: drop kick ----
	/** He drop-kicks targets in front of him between these distances (just past swipe reach). */
	public static final double KICK_MIN_DISTANCE = SWIPE_RANGE, KICK_MAX_DISTANCE = 36.0, KICK_REACH = 24.0;
	public static final float KICK_DAMAGE = 8.0F; // 4 hearts on Normal
	/** Crouch until {@link #KICK_LAUNCH}, jump, both feet hit at {@link #KICK_HIT}, then he lands on his back and gets up. */
	public static final int KICK_LAUNCH = 8, KICK_HIT = 16, KICK_ANIM = 44, KICK_COOLDOWN = 100;

	// ---- NEW: victory dance ----
	public static final int DANCE_TICKS = 60;

	// ---- NEW: jet flight ----
	public static final double FLY_START_DISTANCE = 40.0, FLY_LAND_DISTANCE = 16.0, FLY_SPEED = 1.1, FLY_CRUISE_HEIGHT = 10.0;
	/** A gap: no floor within this many blocks below his feet (and no water) somewhere between him and his target. */
	public static final int GAP_DEPTH = 6;
	public static final double CLIFF_HEIGHT = 12.0;
	public static final int FLY_MAX_TICKS = 600, FLY_COOLDOWN = 40;
	/** The jet-flight pose: how far the model tips forward (radians), and the root drop in model pixels. */
	public static final float FLY_PITCH = 1.45F, FLY_ROOT_DROP_PX = 4.0F;

	// ---- death: only /kill or the void (just falls over, no bones) ----
	public static final int TOPPLE_TICKS = Gojira.TOPPLE_TICKS, DEATH_TICKS = Gojira.DEATH_TICKS;

	// ---- swimming ----
	public static final double SWIM_DEPTH = Gojira.SWIM_DEPTH, SWIM_DEPTH_FLOATING = Gojira.SWIM_DEPTH_FLOATING;
	private static final EntityDataAccessor<Boolean> DATA_SWIMMING = SynchedEntityData.defineId(ShowaGodzilla.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_FLYING = SynchedEntityData.defineId(ShowaGodzilla.class, EntityDataSerializers.BOOLEAN);

	public static final int ROAR_MIN_INTERVAL = Gojira.ROAR_MIN_INTERVAL, ROAR_EXTRA_INTERVAL = Gojira.ROAR_EXTRA_INTERVAL;
	public static final float ROAR_VOLUME = Gojira.ROAR_VOLUME;
	public static final double ROAR_PLAYER_RANGE = 96.0;
	public static final byte EVENT_STOMP = Gojira.EVENT_STOMP, EVENT_STOMP_LAND = Gojira.EVENT_STOMP_LAND, EVENT_BEAM = Gojira.EVENT_BEAM,
			EVENT_ROAR = Gojira.EVENT_ROAR, EVENT_SWIPE = Gojira.EVENT_SWIPE, EVENT_WHIP = Gojira.EVENT_WHIP, EVENT_KICK = 92, EVENT_DANCE = 93;
	public static final int ROAR_ANIM_TICKS = Gojira.ROAR_ANIM_TICKS;

	/** Set by the client mod: called on the client when a stomp lands (small camera shake). */
	public static Consumer<ShowaGodzilla> clientStompLanded = g -> {
	};

	private boolean allowDeath, killed;
	private int stompTicks, stompCooldown, beamTicks, beamCooldown, roarCooldown = 60;
	private int swipeTicks, whipTicks, meleeCooldown;
	private int kickTicks, kickCooldown, danceTicks, flyCooldown, flightTicks;
	private boolean victoryPending;
	// counters for the tests
	private int stomps, beamPulses, beamHits, roars, swipes, whips, meleeHits, kicks, kickHits, dances, defeats, flights, landings, flightTrailTicks;
	private int deathDustBursts;
	private float swimAnim, swimAnimO, flyAnim, flyAnimO;
	private @Nullable LivingEntity beamTarget;
	private @Nullable Vec3 beamAim;
	private int clientStompTicks, clientBeamTicks, clientRoarTicks, clientSwipeTicks, clientWhipTicks, clientKickTicks, clientDanceTicks;

	public ShowaGodzilla(final EntityType<? extends ShowaGodzilla> type, final Level level) {
		super(type, level);
		this.xpReward = 0;
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, MAX_HEALTH)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.ARMOR, 10.0)
				.add(Attributes.ATTACK_DAMAGE, STOMP_DAMAGE)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.FOLLOW_RANGE, 64.0)
				.add(Attributes.STEP_HEIGHT, 2.5)
				.add(Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0);
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_SWIMMING, false);
		builder.define(DATA_FLYING, false);
	}

	public boolean isKaijuSwimming() {
		return this.entityData.get(DATA_SWIMMING);
	}

	/** True while he's jet-flying (synced to clients for the flight pose and glow). */
	public boolean isJetFlying() {
		return this.entityData.get(DATA_FLYING);
	}

	// ---- hero: who he fights ----

	/** Only hostile mobs (and Gojira). Never players, villagers, iron golems, animals, armor stands or other Showa Godzillas. */
	public static boolean isHeroTarget(final @Nullable LivingEntity e) {
		return e != null && e instanceof Enemy && !(e instanceof ShowaGodzilla) && !(e instanceof Player);
	}

	@Override
	public void setTarget(final @Nullable LivingEntity target) {
		// a hero never turns on the people he protects, even if a player (or a villager) hits him by accident
		if (target == null || isHeroTarget(target)) {
			super.setTarget(target);
		}
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(2, new ChaseGoal());
		this.goalSelector.addGoal(5, new WanderGoal());
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 48.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this)); // only ever sticks for hostile mobs (see setTarget)
		this.targetSelector.addGoal(2, new ProtectEveryoneGoal());
	}

	class ProtectEveryoneGoal extends NearestAttackableTargetGoal<LivingEntity> {
		ProtectEveryoneGoal() {
			super(ShowaGodzilla.this, LivingEntity.class, 10, false, false, (e, level) -> isHeroTarget(e));
			this.targetConditions.ignoreLineOfSight();
		}
	}

	// ---- can't be beaten (same rules as Gojira, including the 0.1.6 "killed forever" fix) ----

	@Override
	public void setHealth(final float health) {
		if (this.killed) {
			super.setHealth(0.0F);
			return;
		}
		super.setHealth(this.allowDeath || this.level().isClientSide() ? health : Math.max(health, MIN_HEALTH));
	}

	@Override
	public void heal(final float heal) {
		if (!this.killed) {
			super.heal(heal);
		}
	}

	public boolean isKilled() {
		return this.killed;
	}

	@Override
	public boolean hurtServer(final ServerLevel level, final DamageSource source, final float damage) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			this.allowDeath = true; // /kill, /damage ... out_of_world, the void
			try {
				return super.hurtServer(level, source, damage);
			} finally {
				this.allowDeath = false;
			}
		}
		return super.hurtServer(level, source, damage);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.level().isClientSide()) {
			if (this.clientStompTicks > 0) {
				this.clientStompTicks--;
			}
			if (this.clientBeamTicks > 0) {
				this.clientBeamTicks--;
			}
			if (this.clientRoarTicks > 0) {
				this.clientRoarTicks--;
			}
			if (this.clientSwipeTicks > 0) {
				this.clientSwipeTicks--;
			}
			if (this.clientWhipTicks > 0) {
				this.clientWhipTicks--;
			}
			if (this.clientKickTicks > 0) {
				this.clientKickTicks--;
			}
			if (this.clientDanceTicks > 0) {
				this.clientDanceTicks--;
			}
			this.swimAnimO = this.swimAnim;
			this.swimAnim = Mth.approach(this.swimAnim, this.isKaijuSwimming() && !this.isJetFlying() && this.isAlive() ? 1.0F : 0.0F, 0.05F);
			this.flyAnimO = this.flyAnim;
			this.flyAnim = Mth.approach(this.flyAnim, this.isJetFlying() && this.isAlive() ? 1.0F : 0.0F, 0.1F);
			return;
		}
		double depth = this.isInWater() ? this.getFluidHeight(FluidTags.WATER) : 0.0;
		this.entityData.set(DATA_SWIMMING, this.isAlive() && (depth >= SWIM_DEPTH || (depth >= SWIM_DEPTH_FLOATING && !this.onGround())));
		if (this.killed && this.getHealth() > 0.0F) {
			super.setHealth(0.0F);
		}
		if (!this.killed && this.isAlive() && this.getHealth() < this.getMaxHealth()) {
			this.heal(REGEN_PER_TICK);
		}
		LivingEntity target = this.getTarget();
		if (!this.isJetFlying() && this.isInWater() && target != null && target.getY() > this.getY() - 1.0 && this.getFluidHeight(FluidTags.WATER) > 2.0) {
			Vec3 dm = this.getDeltaMovement();
			this.setDeltaMovement(dm.x, Math.min(0.3, dm.y + 0.08), dm.z);
		}
	}

	// ---- shared hit helper: only hostile mobs get hurt ----

	private Vec3 forward() {
		float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
	}

	public boolean isInFront(final Entity e) {
		Vec3 to = new Vec3(e.getX() - this.getX(), 0, e.getZ() - this.getZ());
		return to.lengthSqr() < 1.0E-4 || to.normalize().dot(this.forward()) >= 0.0;
	}

	/** Hurts a hostile mob; remembers a win (for the victory dance) if that finished it off. */
	private boolean heroHit(final ServerLevel level, final LivingEntity e, final float damage) {
		if (!isHeroTarget(e) || !e.isAlive()) {
			return false;
		}
		boolean hit = e.hurtServer(level, this.damageSources().mobAttack(this), damage);
		if (hit && e.isDeadOrDying()) {
			this.defeats++;
			this.victoryPending = true;
		}
		return hit;
	}

	private static void push(final LivingEntity e, final Vec3 dir, final double side, final double up) {
		e.push(dir.x * side, up, dir.z * side);
		e.needsSync = true;
		if (e instanceof ServerPlayer sp) {
			sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
		}
	}

	private int meleeHitsArea(final ServerLevel level, final boolean front, final double range, final double up, final float damage, final double pushBy) {
		int n = 0;
		AABB area = this.getBoundingBox().inflate(range, 0, range).expandTowards(0, up, 0).expandTowards(0, -3.0, 0);
		Vec3 fwd = this.forward(), side = new Vec3(-fwd.z, 0, fwd.x);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area, ShowaGodzilla::isHeroTarget)) {
			double dx = e.getX() - this.getX(), dz = e.getZ() - this.getZ();
			if (dx * dx + dz * dz > range * range || this.isInFront(e) != front) {
				continue;
			}
			if (this.heroHit(level, e, damage)) {
				n++;
				Vec3 away = new Vec3(dx, 0, dz);
				away = away.lengthSqr() < 1.0E-4 ? fwd : away.normalize();
				Vec3 dir = front ? away : away.add(side.scale(new Vec3(dx, 0, dz).dot(side) >= 0 ? 1.0 : -1.0)).normalize();
				push(e, dir, pushBy, 0.45);
			}
		}
		this.meleeHits += n;
		return n;
	}

	// ---- stomp ----

	public void startStomp() {
		this.stompTicks = STOMP_WINDUP;
		this.stompCooldown = STOMP_COOLDOWN;
		this.level().broadcastEntityEvent(this, EVENT_STOMP);
		this.playSound(SoundEvents.RAVAGER_ROAR, 2.0F, 0.5F);
	}

	private BlockParticleOption groundDust(final ServerLevel level) {
		BlockState ground = level.getBlockState(BlockPos.containing(this.getX(), this.getY() - 0.5, this.getZ()));
		return new BlockParticleOption(ParticleTypes.BLOCK, ground.isAir() ? Blocks.DIRT.defaultBlockState() : ground);
	}

	private void stompLands(final ServerLevel level) {
		this.stomps++;
		level.broadcastEntityEvent(this, EVENT_STOMP_LAND);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 4.0F, 0.55F);
		BlockParticleOption dust = this.groundDust(level);
		for (int i = 0; i < 24; i++) {
			double a = i * Math.PI * 2 / 24, r = STOMP_RADIUS * 0.7;
			level.sendParticles(dust, true, false, this.getX() + Math.cos(a) * r, this.getY() + 0.3, this.getZ() + Math.sin(a) * r, 8, 1.5, 0.2, 1.5, 0.2);
		}
		level.sendParticles(ParticleTypes.CLOUD, true, false, this.getX(), this.getY() + 0.5, this.getZ(), 60, STOMP_RADIUS * 0.4, 0.3, STOMP_RADIUS * 0.4, 0.1);
		AABB area = new AABB(this.getX() - STOMP_RADIUS, this.getY() - 2.0, this.getZ() - STOMP_RADIUS, this.getX() + STOMP_RADIUS, this.getY() + 6.0,
				this.getZ() + STOMP_RADIUS);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area, ShowaGodzilla::isHeroTarget)) {
			double dx = e.getX() - this.getX(), dz = e.getZ() - this.getZ();
			if (dx * dx + dz * dz <= STOMP_RADIUS * STOMP_RADIUS && this.heroHit(level, e, STOMP_DAMAGE)) {
				Vec3 away = new Vec3(dx, 0, dz);
				push(e, away.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : away.normalize(), 1.4, 0.7);
			}
		}
	}

	// ---- claw swipe and tail whip ----

	public void startSwipe() {
		this.swipeTicks = SWIPE_WINDUP;
		this.meleeCooldown = MELEE_COOLDOWN + SWIPE_ANIM;
		this.level().broadcastEntityEvent(this, EVENT_SWIPE);
		this.playSound(SoundEvents.RAVAGER_ATTACK, 3.0F, 0.6F);
	}

	public void startWhip() {
		this.whipTicks = WHIP_WINDUP;
		this.meleeCooldown = MELEE_COOLDOWN + WHIP_ANIM;
		this.level().broadcastEntityEvent(this, EVENT_WHIP);
		this.playSound(SoundEvents.RAVAGER_ROAR, 2.0F, 0.7F);
	}

	private void swipeLands(final ServerLevel level) {
		this.swipes++;
		Vec3 f = this.forward().scale(SWIPE_RANGE * 0.6);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, true, false, this.getX() + f.x, this.getY() + 4.0, this.getZ() + f.z, 6, 4.0, 2.0, 4.0, 0.0);
		level.playSound(null, this.getX() + f.x, this.getY() + 4.0, this.getZ() + f.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.NEUTRAL, 3.0F, 0.6F);
		this.meleeHitsArea(level, true, SWIPE_RANGE, 24.0, SWIPE_DAMAGE, 1.1);
	}

	private void whipLands(final ServerLevel level) {
		this.whips++;
		Vec3 b = this.forward().scale(-WHIP_RANGE * 0.6);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, true, false, this.getX() + b.x, this.getY() + 2.0, this.getZ() + b.z, 8, 6.0, 1.0, 6.0, 0.0);
		level.sendParticles(ParticleTypes.CLOUD, true, false, this.getX() + b.x, this.getY() + 0.5, this.getZ() + b.z, 20, 6.0, 0.3, 6.0, 0.05);
		level.playSound(null, this.getX() + b.x, this.getY() + 2.0, this.getZ() + b.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.NEUTRAL, 3.0F, 0.5F);
		this.meleeHitsArea(level, false, WHIP_RANGE, 12.0, WHIP_DAMAGE, 1.6);
	}

	// ---- NEW: drop kick ----

	/** Crouches, then jumps at the target and kicks with both feet; he lands on his back and gets up again. */
	public void startDropKick() {
		this.kickTicks = KICK_ANIM;
		this.kickCooldown = KICK_COOLDOWN + KICK_ANIM;
		this.meleeCooldown = Math.max(this.meleeCooldown, KICK_ANIM);
		this.level().broadcastEntityEvent(this, EVENT_KICK);
		this.playSound(SoundEvents.RAVAGER_ROAR, 2.5F, 0.8F);
	}

	private void tickDropKick(final ServerLevel level) {
		int elapsed = KICK_ANIM - this.kickTicks;
		this.kickTicks--;
		Vec3 fwd = this.forward();
		if (elapsed == KICK_LAUNCH) {
			// the jump: forward and up
			this.setDeltaMovement(fwd.x * 1.5, 0.9, fwd.z * 1.5);
			this.needsSync = true;
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.NEUTRAL, 4.0F, 0.4F);
			level.sendParticles(this.groundDust(level), true, false, this.getX(), this.getY() + 0.3, this.getZ(), 40, 5.0, 0.3, 5.0, 0.15);
		}
		if (elapsed == KICK_HIT) {
			this.kicks++;
			Vec3 f = fwd.scale(KICK_REACH * 0.5);
			level.sendParticles(ParticleTypes.EXPLOSION, true, false, this.getX() + f.x, this.getY() + 6.0, this.getZ() + f.z, 4, 3.0, 2.0, 3.0, 0.0);
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, true, false, this.getX() + f.x, this.getY() + 6.0, this.getZ() + f.z, 6, 4.0, 2.0, 4.0, 0.0);
			level.playSound(null, this.getX() + f.x, this.getY() + 6.0, this.getZ() + f.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 3.0F, 0.9F);
			this.kickHits += this.meleeHitsArea(level, true, KICK_REACH, 24.0, KICK_DAMAGE, 2.0);
		}
		if (this.kickTicks == 0) {
			level.sendParticles(this.groundDust(level), true, false, this.getX(), this.getY() + 0.3, this.getZ(), 30, 6.0, 0.3, 6.0, 0.1);
		}
	}

	// ---- NEW: victory dance ----

	public void startVictoryDance() {
		this.victoryPending = false;
		this.danceTicks = DANCE_TICKS;
		this.dances++;
		this.level().broadcastEntityEvent(this, EVENT_DANCE);
		this.roar();
	}

	private void tickDance(final ServerLevel level) {
		this.danceTicks--;
		if (this.danceTicks % 10 == 0) {
			level.sendParticles(ParticleTypes.NOTE, true, false, this.getX(), this.getY() + VISUAL_HEIGHT * 0.9, this.getZ(), 4, 6.0, 3.0, 6.0, 1.0);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, true, false, this.getX(), this.getY() + 1.0, this.getZ(), 12, 8.0, 1.0, 8.0, 0.0);
		}
		if (this.danceTicks % 15 == 0) {
			// a happy little hop on each beat
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.RAVAGER_STEP, SoundSource.NEUTRAL, 3.0F, 0.7F);
		}
	}

	// ---- white beam (same as Gojira's, but it only hurts hostile mobs) ----

	public Vec3 mouthPos() {
		Vec3 f = this.forward();
		return new Vec3(this.getX() + f.x * MOUTH_FORWARD, this.getY() + MOUTH_UP, this.getZ() + f.z * MOUTH_FORWARD);
	}

	/** Where his mouth is in the jet-flight pose (body tipped flat, the beam blasting out the way he faces). */
	public Vec3 flightMouthPos() {
		double up = MOUTH_UP - ShowaDimensions.ROOT_PIVOT_UP, s = Mth.sin(FLY_PITCH), c = Mth.cos(FLY_PITCH);
		double fwd = MOUTH_FORWARD * c + up * s;
		double height = ShowaDimensions.ROOT_PIVOT_UP + up * c - MOUTH_FORWARD * s - FLY_ROOT_DROP_PX / 16.0 * ShowaDimensions.VISUAL_SCALE;
		Vec3 f = this.forward();
		return new Vec3(this.getX() + f.x * fwd, this.getY() + Math.max(1.0, height), this.getZ() + f.z * fwd);
	}

	private static Vec3 aimPoint(final LivingEntity target) {
		return target.position().add(0, target.getBbHeight() * 0.6, 0);
	}

	public boolean canBeamReach(final LivingEntity target) {
		Vec3 from = this.mouthPos(), to = aimPoint(target);
		if (from.distanceTo(to) > BEAM_RANGE) {
			return false;
		}
		BlockHitResult hit = this.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceTo(to) < 1.0;
	}

	public void startBeam(final LivingEntity target) {
		this.beamTicks = BEAM_WINDUP + BEAM_FIRE;
		this.beamCooldown = BEAM_COOLDOWN + BEAM_WINDUP + BEAM_FIRE;
		this.beamTarget = target;
		this.beamAim = aimPoint(target);
		this.level().broadcastEntityEvent(this, EVENT_BEAM);
		this.roar();
	}

	private void tickBeam(final ServerLevel level) {
		this.beamTicks--;
		LivingEntity target = this.beamTarget;
		if (target != null && target.isAlive()) {
			double dx = target.getX() - this.getX(), dz = target.getZ() - this.getZ();
			float yaw = Mth.approachDegrees(this.yBodyRot, (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F, 4.0F);
			this.setYRot(yaw);
			this.yBodyRot = yaw;
			this.yHeadRot = yaw;
			Vec3 want = aimPoint(target);
			this.beamAim = this.beamAim == null ? want : this.beamAim.lerp(want, 0.12);
		}
		Vec3 mouth = this.mouthPos();
		if (this.beamTicks >= BEAM_FIRE) {
			Vec3 b = this.forward().scale(-1);
			for (int i = 0; i < 3; i++) {
				double back = this.random.nextDouble() * 12.0 * SIZE_FACTOR, up = (12.0 + this.random.nextDouble() * 22.0) * SIZE_FACTOR;
				level.sendParticles(ParticleTypes.END_ROD, true, false, this.getX() + b.x * back, this.getY() + up, this.getZ() + b.z * back, 1, 1.0, 1.0, 1.0, 0.02);
			}
			level.sendParticles(ParticleTypes.END_ROD, true, false, mouth.x, mouth.y, mouth.z, 3, 0.8, 0.8, 0.8, 0.05);
			if (this.beamTicks == BEAM_FIRE) {
				level.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.NEUTRAL, 5.0F, 0.6F);
			}
		} else {
			int firing = BEAM_FIRE - this.beamTicks;
			boolean pulse = firing % BEAM_PULSE == 1;
			this.fireBeam(level, mouth, pulse, firing % 2 == 1);
			if (pulse) {
				level.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.BEACON_AMBIENT, SoundSource.NEUTRAL, 5.0F, 1.7F);
			}
		}
		if (this.beamTicks <= 0) {
			this.beamTarget = null;
			this.beamAim = null;
		}
	}

	private void fireBeam(final ServerLevel level, final Vec3 from, final boolean damage, final boolean particles) {
		Vec3 aim = this.beamAim != null ? this.beamAim : from.add(this.getLookAngle());
		Vec3 dir = aim.subtract(from);
		if (dir.lengthSqr() < 1.0E-6) {
			return;
		}
		dir = dir.normalize();
		Vec3 to = from.add(dir.scale(BEAM_RANGE));
		BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		Vec3 end = hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
		double len = end.distanceTo(from);
		if (particles) {
			for (double d = 0; d < len; d += 1.0) {
				Vec3 p = from.add(dir.scale(d));
				level.sendParticles(ParticleTypes.END_ROD, true, false, p.x, p.y, p.z, 2, 0.12, 0.12, 0.12, 0.0);
			}
			level.sendParticles(ParticleTypes.END_ROD, true, false, end.x, end.y, end.z, 10, 0.6, 0.6, 0.6, 0.12);
		}
		if (damage) {
			this.beamPulses++;
			AABB box = new AABB(from, end).inflate(1.5);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, ShowaGodzilla::isHeroTarget)) {
				if (e.getBoundingBox().inflate(0.5).clip(from, end).isPresent() && this.heroHit(level, e, BEAM_DAMAGE)) {
					this.beamHits++;
				}
			}
		}
	}

	// ---- NEW: jet flight ----

	private double horizontalDistanceTo(final double x, final double z) {
		double dx = x - this.getX(), dz = z - this.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** Is there a gap (no floor within {@link #GAP_DEPTH} blocks below his feet, and no water) between him and {@code t}? */
	public boolean gapBetween(final LivingEntity t) {
		double hd = this.horizontalDistanceTo(t.getX(), t.getZ());
		if (hd < 8.0) {
			return false;
		}
		Vec3 dir = new Vec3(t.getX() - this.getX(), 0, t.getZ() - this.getZ()).normalize();
		Level level = this.level();
		for (double d = 4.0; d < hd - 4.0; d += 2.0) {
			BlockPos col = BlockPos.containing(this.getX() + dir.x * d, this.getY() - 0.5, this.getZ() + dir.z * d);
			boolean floor = false;
			for (int dy = 0; dy < GAP_DEPTH && !floor; dy++) {
				BlockPos p = col.below(dy);
				BlockState s = level.getBlockState(p);
				floor = !s.getCollisionShape(level, p).isEmpty() || !s.getFluidState().isEmpty();
			}
			if (!floor) {
				return true;
			}
		}
		return false;
	}

	/** He flies when his target is far away, across a gap, or up on a high cliff. */
	public boolean wantsToFly(final LivingEntity t) {
		double hd = this.horizontalDistanceTo(t.getX(), t.getZ());
		if (hd <= FLY_LAND_DISTANCE) {
			return false;
		}
		return hd > FLY_START_DISTANCE || t.getY() - this.getY() > CLIFF_HEIGHT || this.gapBetween(t);
	}

	public void startFlying() {
		this.entityData.set(DATA_FLYING, true);
		this.setNoGravity(true);
		this.flights++;
		this.flightTicks = 0;
		Vec3 dm = this.getDeltaMovement();
		this.setDeltaMovement(dm.x, 0.8, dm.z);
		this.roar();
		this.playSound(SoundEvents.BEACON_POWER_SELECT, 5.0F, 0.6F);
	}

	public void stopFlying() {
		if (this.isJetFlying()) {
			this.landings++;
		}
		this.entityData.set(DATA_FLYING, false);
		this.setNoGravity(false);
		this.resetFallDistance();
		this.flyCooldown = FLY_COOLDOWN;
	}

	private void tickFlight(final ServerLevel level, final @Nullable LivingEntity t) {
		this.flightTicks++;
		if (t == null || !t.isAlive() || this.flightTicks > FLY_MAX_TICKS) {
			this.stopFlying(); // nothing to fly to: he drops back down (no fall damage)
			return;
		}
		Vec3 to = new Vec3(t.getX() - this.getX(), 0, t.getZ() - this.getZ());
		double hd = to.length();
		if (hd <= FLY_LAND_DISTANCE) {
			this.stopFlying();
			level.sendParticles(this.groundDust(level), true, false, this.getX(), this.getY() + 0.3, this.getZ(), 30, 5.0, 0.3, 5.0, 0.1);
			return;
		}
		Vec3 dir = to.scale(1.0 / hd);
		double wantY = t.getY() + (hd > FLY_LAND_DISTANCE * 2.0 ? FLY_CRUISE_HEIGHT : 1.0);
		double vy = Mth.clamp((wantY - this.getY()) * 0.15, -0.7, 0.7);
		if (this.horizontalCollision) {
			vy = 0.9; // something in the way: climb over it
		}
		double speed = Math.min(FLY_SPEED, (hd - FLY_LAND_DISTANCE) * 0.15 + 0.3);
		this.setDeltaMovement(dir.x * speed, vy, dir.z * speed);
		// tail first: he faces AWAY from where he's going, blasting the beam backward like a rocket
		float yaw = (float) (Mth.atan2(-dir.z, -dir.x) * Mth.RAD_TO_DEG) - 90.0F;
		this.setYRot(yaw);
		this.yBodyRot = yaw;
		this.yHeadRot = yaw;
		this.setXRot(0.0F);
		// the jet: a trail of glowing beam particles out of his mouth
		this.flightTrailTicks++;
		Vec3 mouth = this.flightMouthPos(), back = this.forward();
		for (double d = 0; d < 14.0; d += 1.0) {
			Vec3 p = mouth.add(back.scale(d));
			level.sendParticles(ParticleTypes.END_ROD, true, false, p.x, p.y, p.z, 2, 0.2 + d * 0.04, 0.2 + d * 0.04, 0.2 + d * 0.04, 0.01);
		}
		Vec3 tip = mouth.add(back.scale(14.0));
		level.sendParticles(ParticleTypes.CLOUD, true, false, tip.x, tip.y, tip.z, 3, 0.8, 0.8, 0.8, 0.05);
		if (this.flightTicks % 10 == 1) {
			level.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.BEACON_AMBIENT, SoundSource.NEUTRAL, 5.0F, 1.8F);
		}
	}

	// ---- roar ----

	public void roar() {
		this.roars++;
		this.level().playSound(null, this.getX(), this.getY() + 30.0 * SIZE_FACTOR, this.getZ(), ModSounds.GOJIRA_ROAR, SoundSource.NEUTRAL, ROAR_VOLUME,
				0.98F + this.random.nextFloat() * 0.12F);
		this.level().broadcastEntityEvent(this, EVENT_ROAR);
	}

	// ---- thinking ----

	public boolean isBusyAttacking() {
		return this.beamTicks > 0 || this.stompTicks > 0 || this.swipeTicks > 0 || this.whipTicks > 0 || this.kickTicks > 0 || this.danceTicks > 0;
	}

	@Override
	protected void customServerAiStep(final ServerLevel level) {
		super.customServerAiStep(level);
		if (this.stompCooldown > 0) {
			this.stompCooldown--;
		}
		if (this.beamCooldown > 0) {
			this.beamCooldown--;
		}
		if (this.kickCooldown > 0) {
			this.kickCooldown--;
		}
		if (this.flyCooldown > 0) {
			this.flyCooldown--;
		}
		if (this.stompTicks > 0 && --this.stompTicks == 0) {
			this.stompLands(level);
		}
		if (this.beamTicks > 0) {
			this.tickBeam(level);
		}
		if (this.meleeCooldown > 0) {
			this.meleeCooldown--;
		}
		if (this.swipeTicks > 0 && --this.swipeTicks == 0) {
			this.swipeLands(level);
		}
		if (this.whipTicks > 0 && --this.whipTicks == 0) {
			this.whipLands(level);
		}
		if (this.kickTicks > 0) {
			this.tickDropKick(level);
		}
		if (this.danceTicks > 0) {
			this.tickDance(level);
		}
		LivingEntity target = this.getTarget();
		if (this.isJetFlying()) {
			this.tickFlight(level, target);
		} else if (this.victoryPending && !this.isBusyAttacking()) {
			this.startVictoryDance();
		} else if (target != null && target.isAlive() && !this.isBusyAttacking()) {
			double dx = target.getX() - this.getX(), dz = target.getZ() - this.getZ(), dy = target.getY() - this.getY();
			double hd = Math.sqrt(dx * dx + dz * dz);
			boolean front = this.isInFront(target);
			if (this.flyCooldown <= 0 && this.tickCount % 5 == 0 && this.wantsToFly(target)) {
				this.startFlying();
			} else if (hd <= STOMP_RADIUS && dy > -3.0 && dy < 8.0 && this.stompCooldown <= 0) {
				this.startStomp();
			} else if (this.meleeCooldown <= 0 && front && hd <= SWIPE_RANGE && dy > -3.0 && dy < 24.0) {
				this.startSwipe();
			} else if (this.meleeCooldown <= 0 && !front && hd <= WHIP_RANGE && dy > -3.0 && dy < 12.0) {
				this.startWhip();
			} else if (this.kickCooldown <= 0 && this.meleeCooldown <= 0 && front && hd > KICK_MIN_DISTANCE && hd <= KICK_MAX_DISTANCE && dy > -3.0 && dy < 12.0
					&& (this.onGround() || this.isInWater())) {
				this.startDropKick();
			} else if (this.beamCooldown <= 0 && hd >= BEAM_MIN_DISTANCE && this.tickCount % 5 == 0 && this.canBeamReach(target)) {
				this.startBeam(target);
			}
		}
		if (--this.roarCooldown <= 0) {
			this.roarCooldown = ROAR_MIN_INTERVAL + this.random.nextInt(ROAR_EXTRA_INTERVAL);
			if (level.getNearestPlayer(this, ROAR_PLAYER_RANGE) != null && !this.isBusyAttacking()) {
				this.roar();
			}
		}
	}

	// ---- movement goals (like Gojira's: no pathfinding, walks straight and sidesteps) ----

	class ChaseGoal extends Goal {
		private int stuckTicks, sidestepTicks;
		private Vec3 sidestep = Vec3.ZERO;

		ChaseGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity t = ShowaGodzilla.this.getTarget();
			return t != null && t.isAlive();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			ShowaGodzilla g = ShowaGodzilla.this;
			LivingEntity t = g.getTarget();
			if (t == null || g.isJetFlying() || g.kickTicks > 0 || g.danceTicks > 0) {
				return; // flying, kicking and dancing move him on their own
			}
			g.getLookControl().setLookAt(t, 10.0F, 10.0F);
			if (g.beamTicks > 0) {
				return;
			}
			if (this.sidestepTicks > 0) {
				this.sidestepTicks--;
				g.getMoveControl().setWantedPosition(this.sidestep.x, this.sidestep.y, this.sidestep.z, 1.0);
				return;
			}
			double hd = g.horizontalDistanceTo(t.getX(), t.getZ());
			if (hd > STOMP_RADIUS * 0.5) {
				g.getMoveControl().setWantedPosition(t.getX(), t.getY(), t.getZ(), 1.0);
				this.stuckTicks = g.horizontalCollision ? this.stuckTicks + 1 : 0;
				if (this.stuckTicks > 30) {
					this.stuckTicks = 0;
					this.sidestepTicks = 40;
					Vec3 toT = new Vec3(t.getX() - g.getX(), 0, t.getZ() - g.getZ()).normalize();
					Vec3 side = new Vec3(-toT.z, 0, toT.x).scale(g.random.nextBoolean() ? 12.0 : -12.0);
					this.sidestep = g.position().add(side).add(toT.scale(4.0));
				}
			}
		}
	}

	class WanderGoal extends Goal {
		private @Nullable Vec3 dest;
		private int ticks;

		WanderGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			ShowaGodzilla g = ShowaGodzilla.this;
			if (g.getTarget() != null || g.isJetFlying() || g.danceTicks > 0 || g.random.nextInt(160) != 0) {
				return false;
			}
			double a = g.random.nextDouble() * Math.PI * 2, r = 12.0 + g.random.nextDouble() * 24.0;
			this.dest = g.position().add(Math.cos(a) * r, 0, Math.sin(a) * r);
			this.ticks = 200;
			return true;
		}

		@Override
		public boolean canContinueToUse() {
			ShowaGodzilla g = ShowaGodzilla.this;
			return this.dest != null && this.ticks > 0 && g.getTarget() == null && !g.isJetFlying() && g.horizontalDistanceTo(this.dest.x, this.dest.z) > 2.0;
		}

		@Override
		public void tick() {
			this.ticks--;
			if (this.dest != null) {
				ShowaGodzilla.this.getMoveControl().setWantedPosition(this.dest.x, this.dest.y, this.dest.z, 0.6);
			}
		}

		@Override
		public void stop() {
			this.dest = null;
		}
	}

	// ---- client animations ----

	@Override
	public void handleEntityEvent(final byte id) {
		switch (id) {
			case EVENT_STOMP -> this.clientStompTicks = STOMP_WINDUP + 8;
			case EVENT_STOMP_LAND -> clientStompLanded.accept(this);
			case EVENT_BEAM -> this.clientBeamTicks = BEAM_WINDUP + BEAM_FIRE;
			case EVENT_ROAR -> this.clientRoarTicks = ROAR_ANIM_TICKS;
			case EVENT_SWIPE -> this.clientSwipeTicks = SWIPE_ANIM;
			case EVENT_WHIP -> this.clientWhipTicks = WHIP_ANIM;
			case EVENT_KICK -> this.clientKickTicks = KICK_ANIM;
			case EVENT_DANCE -> this.clientDanceTicks = DANCE_TICKS;
			default -> super.handleEntityEvent(id);
		}
	}

	private static float progress(final int ticksLeft, final int total, final float partialTicks) {
		return ticksLeft <= 0 ? 0.0F : Math.min(1.0F, (total - ticksLeft + partialTicks) / total);
	}

	public float getStompProgress(final float partialTicks) {
		return progress(this.clientStompTicks, STOMP_WINDUP + 8, partialTicks);
	}

	/** 0..1 how brightly the plates glow: the beam, and full while jet-flying. */
	public float getSpikeGlow(final float partialTicks) {
		float fly = Mth.lerp(partialTicks, this.flyAnimO, this.flyAnim);
		if (this.clientBeamTicks <= 0) {
			return fly;
		}
		float elapsed = BEAM_WINDUP + BEAM_FIRE - this.clientBeamTicks + partialTicks;
		float beam = elapsed < BEAM_WINDUP ? elapsed / BEAM_WINDUP : Math.max(0.0F, Math.min(1.0F, (this.clientBeamTicks - partialTicks) / 6.0F));
		return Math.max(fly, beam);
	}

	public boolean isBeamFiring() {
		return this.clientBeamTicks > 0 && this.clientBeamTicks <= BEAM_FIRE;
	}

	public float getJawOpen(final float partialTicks) {
		float roar = 0.0F;
		if (this.clientRoarTicks > 0) {
			float t = (ROAR_ANIM_TICKS - this.clientRoarTicks + partialTicks) / ROAR_ANIM_TICKS;
			roar = t < 0.15F ? t / 0.15F : (t > 0.8F ? (1.0F - t) / 0.2F : 1.0F);
		}
		return Math.max(roar, Math.max(this.isBeamFiring() ? 1.0F : 0.0F, Mth.lerp(partialTicks, this.flyAnimO, this.flyAnim)));
	}

	public float getSwipeProgress(final float partialTicks) {
		return progress(this.clientSwipeTicks, SWIPE_ANIM, partialTicks);
	}

	public float getWhipProgress(final float partialTicks) {
		return progress(this.clientWhipTicks, WHIP_ANIM, partialTicks);
	}

	public float getKickProgress(final float partialTicks) {
		return progress(this.clientKickTicks, KICK_ANIM, partialTicks);
	}

	public float getDanceProgress(final float partialTicks) {
		return progress(this.clientDanceTicks, DANCE_TICKS, partialTicks);
	}

	public float getSwimAmount(final float partialTicks) {
		return Mth.lerp(partialTicks, this.swimAnimO, this.swimAnim);
	}

	/** 0 = standing, 1 = full jet-flight pose. */
	public float getFlyAmount(final float partialTicks) {
		return Mth.lerp(partialTicks, this.flyAnimO, this.flyAnim);
	}

	public float getDeathProgress(final float partialTicks) {
		return this.deathTime <= 0 ? 0.0F : Math.min(1.0F, (this.deathTime + partialTicks) / DEATH_TICKS);
	}

	/** How far the drop kick tips him back (degrees): he leans back as he jumps, is flat by the hit, then gets back up. */
	public static float kickTiltDegrees(final float p) {
		float hit = (float) KICK_HIT / KICK_ANIM, launch = (float) KICK_LAUNCH / KICK_ANIM;
		if (p <= launch) {
			return -8.0F * p / launch; // crouch: leans forward a little
		}
		if (p <= hit) {
			return 75.0F * (p - launch) / (hit - launch);
		}
		if (p <= 0.7F) {
			return 75.0F;
		}
		return 75.0F * Math.max(0.0F, 1.0F - (p - 0.7F) / 0.3F);
	}

	// ---- death: just falls over (no bones, land or water) ----

	@Override
	public void die(final DamageSource source) {
		this.killed = true;
		if (this.isJetFlying()) {
			this.stopFlying(); // falls out of the sky
		}
		super.die(source);
	}

	@Override
	protected void tickDeath() {
		this.deathTime++;
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		BlockParticleOption dust = this.groundDust(level);
		float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
		Vec3 dir = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
		if (this.deathTime < TOPPLE_TICKS && this.deathTime % 4 == 0) {
			level.sendParticles(dust, true, false, this.getX(), this.getY() + 0.3, this.getZ(), 20, 5.0, 0.3, 5.0, 0.1);
			this.deathDustBursts++;
		}
		if (this.deathTime == TOPPLE_TICKS) {
			this.deathDustBursts++;
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 5.0F, 0.45F);
			for (double d = 0; d < VISUAL_HEIGHT; d += 2.5) {
				Vec3 p = this.position().add(dir.scale(d));
				level.sendParticles(dust, true, false, p.x, p.y + 0.3, p.z, 16, 2.5, 0.3, 2.5, 0.2);
				level.sendParticles(ParticleTypes.CLOUD, true, false, p.x, p.y + 1.0, p.z, 4, 2.0, 0.5, 2.0, 0.05);
			}
		}
		if (this.deathTime >= DEATH_TICKS && !this.isRemoved()) {
			for (double d = 0; d < VISUAL_HEIGHT; d += 4.0) {
				Vec3 p = this.position().add(dir.scale(d)).add(0, ShowaDimensions.BODY_HALF_WIDTH * 0.5, 0);
				level.sendParticles(ParticleTypes.POOF, true, false, p.x, p.y, p.z, 10, 3.0, 2.0, 3.0, 0.05);
			}
			level.broadcastEntityEvent(this, (byte) 60);
			this.remove(Entity.RemovalReason.KILLED); // and that's all: no bones, no drops
		}
	}

	// ---- for tests ----

	public void calmForTest() {
		this.targetSelector.removeAllGoals(g -> true);
		this.goalSelector.removeAllGoals(g -> true);
		this.setTarget(null);
		this.roarCooldown = Integer.MAX_VALUE;
	}

	public int getStomps() {
		return this.stomps;
	}

	public int getBeamPulses() {
		return this.beamPulses;
	}

	public int getBeamHits() {
		return this.beamHits;
	}

	public int getSwipes() {
		return this.swipes;
	}

	public int getWhips() {
		return this.whips;
	}

	public int getMeleeHits() {
		return this.meleeHits;
	}

	public int getKicks() {
		return this.kicks;
	}

	public int getKickHits() {
		return this.kickHits;
	}

	public int getKickTicks() {
		return this.kickTicks;
	}

	public int getDances() {
		return this.dances;
	}

	public int getDanceTicks() {
		return this.danceTicks;
	}

	public int getDefeats() {
		return this.defeats;
	}

	public int getFlights() {
		return this.flights;
	}

	public int getLandings() {
		return this.landings;
	}

	public int getFlightTrailTicks() {
		return this.flightTrailTicks;
	}

	public int getDeathDustBursts() {
		return this.deathDustBursts;
	}

	public int getRoars() {
		return this.roars;
	}

	// ---- misc ----

	@Override
	public boolean removeWhenFarAway(final double distSqr) {
		return false;
	}

	@Override
	public boolean requiresCustomPersistence() {
		return true;
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean causeFallDamage(final double fallDistance, final float multiplier, final DamageSource source) {
		return false;
	}

	@Override
	public boolean doHurtTarget(final ServerLevel level, final Entity target) {
		return false;
	}

	@Override
	protected float getSoundVolume() {
		return 3.0F;
	}

	@Override
	public float getVoicePitch() {
		return 0.6F;
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	protected SoundEvent getHurtSound(final DamageSource source) {
		return SoundEvents.RAVAGER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.GOJIRA_ROAR;
	}

	@Override
	protected void playStepSound(final BlockPos pos, final BlockState state) {
		this.playSound(SoundEvents.RAVAGER_STEP, 2.5F, 0.45F);
	}

	@Override
	protected void addAdditionalSaveData(final ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("BeamCooldown", this.beamCooldown);
		output.putBoolean("Killed", this.killed);
		output.putBoolean("JetFlying", this.isJetFlying());
	}

	@Override
	protected void readAdditionalSaveData(final ValueInput input) {
		super.readAdditionalSaveData(input);
		this.beamCooldown = input.getIntOr("BeamCooldown", 0);
		this.killed = input.getBooleanOr("Killed", false) || input.getFloatOr("Health", MAX_HEALTH) <= 0.0F;
		if (this.killed) {
			super.setHealth(0.0F);
		}
		boolean flying = input.getBooleanOr("JetFlying", false) && !this.killed;
		this.entityData.set(DATA_FLYING, flying);
		this.setNoGravity(flying);
	}
}
