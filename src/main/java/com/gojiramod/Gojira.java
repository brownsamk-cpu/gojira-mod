package com.gojiramod;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Gojira (gojiramod:gojira), June and Leon's kaiju: a giant dark grey dinosaur-like monster with a long tail and pale spikes
 * down its back. It lives in the ocean (swims and walks on the sea floor, wades onto land), is ALWAYS angry at every living
 * mob and player it comes across (nearest first; not other Gojiras, armor stands, or creative/spectator players), and can't
 * be beaten: it heals much faster than anyone can hurt it and its health never drops below 1.
 *
 * <p>Attacks: a STOMP (when you're near its feet: shockwave damage + knockback + dust + a little screen shake) and a glowing
 * WHITE BEAM from its mouth (spikes glow white during a 2 second wind-up and roar, then the beam sweeps toward you and hurts
 * whatever it touches), a CLAW SWIPE at things in front of it and a TAIL WHIP at things behind it. It never breaks blocks.
 *
 * <p>Animations (0.1.4): it swims (body tilts flat, tail sweeps, legs kick, arms tucked) in deep water, swipes and whips,
 * and when it really dies (only {@code /kill} or the void) it slowly topples over, its plates' glow fades, and dust flies.
 * If it dies underwater, it leaves a giant bone-block skeleton lying on the sea floor (0.1.5, {@link GojiraSkeleton}).
 *
 * <p>Size: the model is drawn {@link #VISUAL_HEIGHT} blocks tall but the real hitbox is only {@link #HITBOX_WIDTH} x
 * {@link #HITBOX_HEIGHT} (a 55-block hitbox couldn't move through the world and would be very laggy). Attacks are measured from
 * where the visual feet and mouth are, not from the hitbox.
 *
 * <p>Grown-up off switches: {@code /kill @e[type=gojiramod:gojira]} still works (it bypasses the "can't be beaten" rule),
 * and switching to Peaceful removes it like any other monster.
 */
public class Gojira extends Monster {
	// ---- size (measured from the model data by tools/gojira.py, see GojiraDimensions) ----
	/** 0.1.2: 164/3 = 54.6667 blocks tall to look at (it was 50 in 0.1.0/0.1.1). */
	public static final float VISUAL_HEIGHT = GojiraDimensions.VISUAL_HEIGHT;
	/** How much bigger than the original 50-block design (used to scale the attack reach and effects). */
	public static final float SIZE_FACTOR = VISUAL_HEIGHT / 50.0F;
	public static final float VISUAL_SCALE = GojiraDimensions.VISUAL_SCALE;
	public static final float HITBOX_WIDTH = 6.0F, HITBOX_HEIGHT = 16.0F;
	public static final double MOUTH_UP = GojiraDimensions.MOUTH_UP, MOUTH_FORWARD = GojiraDimensions.MOUTH_FORWARD;

	// ---- can't be beaten ----
	public static final float MAX_HEALTH = 1000.0F;
	public static final float MIN_HEALTH = 1.0F;
	/** Heals 2 health every tick = 40 health a second (a strong player sword hit is ~10-20, at most every half second). */
	public static final float REGEN_PER_TICK = 2.0F;

	// ---- stomp ----
	/** Reaches past the visual feet and toes (12 blocks at the old 50-block size, ~13.1 now; the toes reach ~12.6). */
	public static final double STOMP_RADIUS = Math.max(12.0 * SIZE_FACTOR, GojiraDimensions.FEET_REACH + 0.5);
	public static final float STOMP_DAMAGE = 6.0F; // 3 hearts on Normal
	public static final int STOMP_WINDUP = 16, STOMP_COOLDOWN = 50;

	// ---- melee: claw swipe and tail whip ----

	private Vec3 forward() {
		float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
	}

	/** Is {@code e} in front of it (true) or behind it (false)? */
	public boolean isInFront(final Entity e) {
		Vec3 to = new Vec3(e.getX() - this.getX(), 0, e.getZ() - this.getZ());
		return to.lengthSqr() < 1.0E-4 || to.normalize().dot(this.forward()) >= 0.0;
	}

	/** Raises its clawed arm; it swipes down {@link #SWIPE_WINDUP} ticks later, hitting things in front of it. */
	public void startSwipe() {
		this.swipeTicks = SWIPE_WINDUP;
		this.meleeCooldown = MELEE_COOLDOWN + SWIPE_ANIM;
		this.level().broadcastEntityEvent(this, EVENT_SWIPE);
		this.playSound(SoundEvents.RAVAGER_ATTACK, 3.0F, 0.5F);
	}

	/** Winds its tail up; it whips around {@link #WHIP_WINDUP} ticks later, hitting things behind it. */
	public void startWhip() {
		this.whipTicks = WHIP_WINDUP;
		this.meleeCooldown = MELEE_COOLDOWN + WHIP_ANIM;
		this.level().broadcastEntityEvent(this, EVENT_WHIP);
		this.playSound(SoundEvents.RAVAGER_ROAR, 2.0F, 0.6F);
	}

	private void meleeHits(final ServerLevel level, final boolean front, final double range, final double up, final float damage, final double push) {
		AABB area = this.getBoundingBox().inflate(range, 0, range).expandTowards(0, up, 0).expandTowards(0, -3.0, 0);
		Vec3 fwd = this.forward(), side = new Vec3(-fwd.z, 0, fwd.x);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area, e -> e != this && e.isAlive() && !(e instanceof Gojira) && !(e instanceof ArmorStand))) {
			double dx = e.getX() - this.getX(), dz = e.getZ() - this.getZ();
			if (dx * dx + dz * dz > range * range || this.isInFront(e) != front) {
				continue;
			}
			if (e.hurtServer(level, this.damageSources().mobAttack(this), damage)) {
				this.meleeHits++;
				Vec3 away = new Vec3(dx, 0, dz);
				away = away.lengthSqr() < 1.0E-4 ? fwd : away.normalize();
				Vec3 dir = front ? away : away.add(side.scale(new Vec3(dx, 0, dz).dot(side) >= 0 ? 1.0 : -1.0)).normalize(); // a whip flings sideways
				e.push(dir.x * push, 0.45, dir.z * push);
				e.needsSync = true;
				if (e instanceof ServerPlayer sp) {
					sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
				}
			}
		}
	}

	private void swipeLands(final ServerLevel level) {
		this.swipes++;
		Vec3 f = this.forward().scale(SWIPE_RANGE * 0.6);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, true, false, this.getX() + f.x, this.getY() + 4.0, this.getZ() + f.z, 6, 4.0, 2.0, 4.0, 0.0);
		level.playSound(null, this.getX() + f.x, this.getY() + 4.0, this.getZ() + f.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 3.0F, 0.5F);
		this.meleeHits(level, true, SWIPE_RANGE, 24.0, SWIPE_DAMAGE, 1.1);
	}

	private void whipLands(final ServerLevel level) {
		this.whips++;
		Vec3 b = this.forward().scale(-WHIP_RANGE * 0.6);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, true, false, this.getX() + b.x, this.getY() + 2.0, this.getZ() + b.z, 8, 6.0, 1.0, 6.0, 0.0);
		level.sendParticles(ParticleTypes.CLOUD, true, false, this.getX() + b.x, this.getY() + 0.5, this.getZ() + b.z, 20, 6.0, 0.3, 6.0, 0.05);
		level.playSound(null, this.getX() + b.x, this.getY() + 2.0, this.getZ() + b.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 3.0F, 0.4F);
		this.meleeHits(level, false, WHIP_RANGE, 12.0, WHIP_DAMAGE, 1.6);
	}

	// ---- white beam ----
	public static final double BEAM_RANGE = 80.0, BEAM_MIN_DISTANCE = 14.0;
	public static final float BEAM_DAMAGE = 3.0F; // 1.5 hearts per pulse
	/** 2 s wind-up, then 4 damage pulses 12 ticks apart (just over the 10-tick hurt cooldown, so every pulse can land). */
	public static final int BEAM_WINDUP = 40, BEAM_FIRE = 48, BEAM_PULSE = 12, BEAM_COOLDOWN = 160;

	// ---- melee (0.1.4): claw swipe in front, tail whip behind ----
	public static final double SWIPE_RANGE = 18.0, WHIP_RANGE = 26.0;
	public static final float SWIPE_DAMAGE = 5.0F, WHIP_DAMAGE = 4.0F; // 2.5 and 2 hearts on Normal
	/** Ticks from the start of the move until it hits, and the whole animation length. */
	public static final int SWIPE_WINDUP = 10, SWIPE_ANIM = 20, WHIP_WINDUP = 12, WHIP_ANIM = 24, MELEE_COOLDOWN = 30;

	// ---- death (0.1.4): only /kill or the void ----
	/** It topples for {@link #TOPPLE_TICKS}, lies still while its glow fades, and is removed after {@link #DEATH_TICKS} (5 s). */
	public static final int TOPPLE_TICKS = 60, DEATH_TICKS = 100;
	/** 0.1.5: dying in water at least this deep (around its hitbox) leaves a bone-block skeleton on the sea floor. */
	public static final double SKELETON_WATER_DEPTH = 6.0;

	// ---- swimming (0.1.4) ----
	/** Swims (instead of wading) when the water is at least this deep around its hitbox, or when it's off the bottom. */
	public static final double SWIM_DEPTH = 10.0, SWIM_DEPTH_FLOATING = 5.0;
	private static final EntityDataAccessor<Boolean> DATA_SWIMMING = SynchedEntityData.defineId(Gojira.class, EntityDataSerializers.BOOLEAN);

	public static final double BOSS_BAR_RANGE = 96.0;
	public static final int ROAR_MIN_INTERVAL = 400, ROAR_EXTRA_INTERVAL = 300;
	public static final float ROAR_VOLUME = 6.0F; // variable-range sound: heard ~96 blocks away

	public static final byte EVENT_STOMP = 4, EVENT_STOMP_LAND = 39, EVENT_BEAM = 62, EVENT_ROAR = 69, EVENT_SWIPE = 90, EVENT_WHIP = 91;
	public static final int ROAR_ANIM_TICKS = 50;

	/** Set by the client mod: called on the client when a stomp lands (used for a small camera shake). */
	public static Consumer<Gojira> clientStompLanded = g -> {
	};

	private final ServerBossEvent bossEvent = (ServerBossEvent) new ServerBossEvent(java.util.UUID.randomUUID(),
			Component.translatable("entity.gojiramod.gojira"), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS).setDarkenScreen(false);
	private boolean allowDeath;
	/** 0.1.6: set for good once /kill (or the void) gets him; saved, so a reload can't bring him back. */
	private boolean killed;
	private int stompTicks, stompCooldown, beamTicks, beamCooldown, roarCooldown = 60;
	private int stomps, beamPulses, beamHits, roars;
	private int swipeTicks, whipTicks, meleeCooldown, swipes, whips, meleeHits, deathDustBursts;
	private int clientSwipeTicks, clientWhipTicks;
	private boolean diedUnderwater;
	private int skeletonBlocks = -1;
	private float swimAnim, swimAnimO;
	private @Nullable LivingEntity beamTarget;
	private @Nullable Vec3 beamAim;
	private int clientStompTicks, clientBeamTicks, clientRoarTicks;

	public Gojira(final EntityType<? extends Gojira> type, final Level level) {
		super(type, level);
		this.xpReward = 0;
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
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
	}

	/** True while it's swimming in deep water (synced to clients for the swim animation). */
	public boolean isKaijuSwimming() {
		return this.entityData.get(DATA_SWIMMING);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(2, new ChaseGoal());
		this.goalSelector.addGoal(5, new WanderGoal());
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 48.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new HuntEverythingGoal());
	}

	// ---- can't be beaten ----

	@Override
	public void setHealth(final float health) {
		if (this.killed) {
			// 0.1.6: once /kill got him he stays dead: nothing (regen, heal, a reload, max-health changes) can raise his health again
			super.setHealth(0.0F);
			return;
		}
		// Never lets its health reach 0 (no death), unless it's /kill or the void (see hurtServer).
		// The client just mirrors the server (e.g. the death event sets health 0 there), so it never clamps.
		super.setHealth(this.allowDeath || this.level().isClientSide() ? health : Math.max(health, MIN_HEALTH));
	}

	@Override
	public void heal(final float heal) {
		if (!this.killed) {
			super.heal(heal);
		}
	}

	/** True once /kill or the void has got him: he's dying (or dead) for good. */
	public boolean isKilled() {
		return this.killed;
	}

	@Override
	public boolean hurtServer(final ServerLevel level, final DamageSource source, final float damage) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			this.allowDeath = true; // /kill, /damage ... out_of_world, falling out of the world: the grown-up off switch
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
			this.swimAnimO = this.swimAnim;
			this.swimAnim = Mth.approach(this.swimAnim, this.isKaijuSwimming() && this.isAlive() ? 1.0F : 0.0F, 0.05F);
			return;
		}
		double depth = this.isInWater() ? this.getFluidHeight(FluidTags.WATER) : 0.0;
		this.entityData.set(DATA_SWIMMING, this.isAlive() && (depth >= SWIM_DEPTH || (depth >= SWIM_DEPTH_FLOATING && !this.onGround())));
		if (this.killed && this.getHealth() > 0.0F) {
			super.setHealth(0.0F); // belt and braces: a killed Gojira never comes back
		}
		if (!this.killed && this.isAlive() && this.getHealth() < this.getMaxHealth()) {
			this.heal(REGEN_PER_TICK);
		}
		// swimming: when chasing someone at the surface or on land it swims up, keeping its head and shoulders out of the water
		LivingEntity target = this.getTarget();
		if (this.isInWater() && target != null && target.getY() > this.getY() - 1.0 && this.getFluidHeight(FluidTags.WATER) > 2.0) {
			Vec3 dm = this.getDeltaMovement();
			this.setDeltaMovement(dm.x, Math.min(0.3, dm.y + 0.08), dm.z);
		}
	}

	// ---- stomp ----

	/** Lifts a foot; it comes down {@link #STOMP_WINDUP} ticks later. */
	public void startStomp() {
		this.stompTicks = STOMP_WINDUP;
		this.stompCooldown = STOMP_COOLDOWN;
		this.level().broadcastEntityEvent(this, EVENT_STOMP);
		this.playSound(SoundEvents.RAVAGER_ROAR, 2.0F, 0.4F);
	}

	private void stompLands(final ServerLevel level) {
		this.stomps++;
		this.level().broadcastEntityEvent(this, EVENT_STOMP_LAND);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 4.0F, 0.5F);
		BlockState ground = level.getBlockState(BlockPos.containing(this.getX(), this.getY() - 0.5, this.getZ()));
		if (ground.isAir()) {
			ground = net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
		}
		BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, ground);
		for (int i = 0; i < 24; i++) {
			double a = i * Math.PI * 2 / 24;
			double r = STOMP_RADIUS * 0.7;
			level.sendParticles(dust, true, false, this.getX() + Math.cos(a) * r, this.getY() + 0.3, this.getZ() + Math.sin(a) * r, 8, 1.5, 0.2, 1.5, 0.2);
		}
		level.sendParticles(ParticleTypes.CLOUD, true, false, this.getX(), this.getY() + 0.5, this.getZ(), 60, STOMP_RADIUS * 0.4, 0.3, STOMP_RADIUS * 0.4, 0.1);
		level.sendParticles(ParticleTypes.EXPLOSION, true, false, this.getX(), this.getY() + 0.5, this.getZ(), 6, 4.0, 0.3, 4.0, 0.0);
		AABB area = new AABB(this.getX() - STOMP_RADIUS, this.getY() - 2.0, this.getZ() - STOMP_RADIUS, this.getX() + STOMP_RADIUS, this.getY() + 6.0,
				this.getZ() + STOMP_RADIUS);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area, e -> e != this && e.isAlive() && !(e instanceof Gojira))) {
			double dx = e.getX() - this.getX(), dz = e.getZ() - this.getZ();
			if (dx * dx + dz * dz > STOMP_RADIUS * STOMP_RADIUS) {
				continue;
			}
			if (e.hurtServer(level, this.damageSources().mobAttack(this), STOMP_DAMAGE)) {
				Vec3 away = new Vec3(dx, 0, dz);
				away = away.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : away.normalize();
				e.push(away.x * 1.4, 0.7, away.z * 1.4);
				e.needsSync = true;
				if (e instanceof ServerPlayer sp) {
					sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
				}
			}
		}
	}

	// ---- white beam ----

	/** Where the (visual) mouth is: {@link #MOUTH_FORWARD} blocks in front of the body and {@link #MOUTH_UP} blocks up. */
	public Vec3 mouthPos() {
		float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
		return new Vec3(this.getX() - Mth.sin(yaw) * MOUTH_FORWARD, this.getY() + MOUTH_UP, this.getZ() + Mth.cos(yaw) * MOUTH_FORWARD);
	}

	private static Vec3 aimPoint(final LivingEntity target) {
		return target.position().add(0, target.getBbHeight() * 0.6, 0);
	}

	/** Can the beam reach the target from the mouth without hitting blocks on the way? */
	public boolean canBeamReach(final LivingEntity target) {
		Vec3 from = this.mouthPos(), to = aimPoint(target);
		if (from.distanceTo(to) > BEAM_RANGE) {
			return false;
		}
		BlockHitResult hit = this.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceTo(to) < 1.0;
	}

	/** Spikes glow, ROAR, and {@link #BEAM_WINDUP} ticks later the beam fires at the target for {@link #BEAM_FIRE} ticks. */
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
			// turn to face the target, and the aim slowly follows it (so you can dodge by running sideways)
			double dx = target.getX() - this.getX(), dz = target.getZ() - this.getZ();
			float want = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
			float yaw = Mth.approachDegrees(this.yBodyRot, want, 4.0F);
			this.setYRot(yaw);
			this.yBodyRot = yaw;
			this.yHeadRot = yaw;
			Vec3 want3 = aimPoint(target);
			this.beamAim = this.beamAim == null ? want3 : this.beamAim.lerp(want3, 0.12);
		}
		Vec3 mouth = this.mouthPos();
		if (this.beamTicks >= BEAM_FIRE) {
			// wind-up: white sparks rise off the glowing spikes and gather at the mouth
			float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
			for (int i = 0; i < 3; i++) {
				double back = this.random.nextDouble() * 12.0 * SIZE_FACTOR, up = (12.0 + this.random.nextDouble() * 22.0) * SIZE_FACTOR;
				level.sendParticles(ParticleTypes.END_ROD, true, false, this.getX() + Mth.sin(yaw) * back, this.getY() + up, this.getZ() - Mth.cos(yaw) * back,
						1, 1.0, 1.0, 1.0, 0.02);
			}
			level.sendParticles(ParticleTypes.END_ROD, true, false, mouth.x, mouth.y, mouth.z, 3, 0.8, 0.8, 0.8, 0.05);
			if (this.beamTicks == BEAM_FIRE) {
				level.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 5.0F, 0.5F);
			}
		} else {
			int firing = BEAM_FIRE - this.beamTicks; // 1..BEAM_FIRE
			boolean pulse = firing % BEAM_PULSE == 1;
			this.fireBeam(level, mouth, pulse, firing % 2 == 1);
			if (pulse) {
				level.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 5.0F, 1.6F);
			}
		}
		if (this.beamTicks <= 0) {
			this.beamTarget = null;
			this.beamAim = null;
		}
	}

	/** One tick of the beam: a line of white glowing particles, and (on a pulse) damage to everything along the line. */
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
			level.sendParticles(ParticleTypes.CLOUD, true, false, end.x, end.y, end.z, 3, 0.4, 0.4, 0.4, 0.03);
		}
		if (damage) {
			this.beamPulses++;
			AABB box = new AABB(from, end).inflate(1.5);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != this && e.isAlive() && !(e instanceof Gojira))) {
				if (e.getBoundingBox().inflate(0.5).clip(from, end).isPresent() && e.hurtServer(level, this.damageSources().mobAttack(this), BEAM_DAMAGE)) {
					this.beamHits++;
				}
			}
		}
	}

	// ---- roar ----

	public void roar() {
		this.roars++;
		this.level().playSound(null, this.getX(), this.getY() + 30.0 * SIZE_FACTOR, this.getZ(), ModSounds.GOJIRA_ROAR, SoundSource.HOSTILE, ROAR_VOLUME,
				0.92F + this.random.nextFloat() * 0.12F);
		this.level().broadcastEntityEvent(this, EVENT_ROAR);
	}

	// ---- thinking ----

	public boolean isBusyAttacking() {
		return this.beamTicks > 0 || this.stompTicks > 0 || this.swipeTicks > 0 || this.whipTicks > 0;
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
		if (this.stompTicks > 0) {
			this.stompTicks--;
			if (this.stompTicks == 0) {
				this.stompLands(level);
			}
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
		LivingEntity target = this.getTarget();
		if (target != null && target.isAlive() && !this.isBusyAttacking()) {
			double dx = target.getX() - this.getX(), dz = target.getZ() - this.getZ(), dy = target.getY() - this.getY();
			double hd = Math.sqrt(dx * dx + dz * dz);
			boolean front = this.isInFront(target);
			if (hd <= STOMP_RADIUS && dy > -3.0 && dy < 8.0 && this.stompCooldown <= 0) {
				this.startStomp();
			} else if (this.meleeCooldown <= 0 && front && hd <= SWIPE_RANGE && dy > -3.0 && dy < 24.0) {
				this.startSwipe();
			} else if (this.meleeCooldown <= 0 && !front && hd <= WHIP_RANGE && dy > -3.0 && dy < 12.0) {
				this.startWhip();
			} else if (this.beamCooldown <= 0 && hd >= BEAM_MIN_DISTANCE && this.tickCount % 5 == 0 && this.canBeamReach(target)) {
				this.startBeam(target);
			}
		}
		if (--this.roarCooldown <= 0) {
			this.roarCooldown = ROAR_MIN_INTERVAL + this.random.nextInt(ROAR_EXTRA_INTERVAL);
			if (level.getNearestPlayer(this, BOSS_BAR_RANGE) != null && !this.isBusyAttacking()) {
				this.roar();
			}
		}
		this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
		if (this.tickCount % 10 == 0) {
			this.updateBossBarPlayers(level);
		}
	}

	/** Shows the "Gojira" boss bar to players within {@link #BOSS_BAR_RANGE} blocks and hides it from everyone else. */
	public void updateBossBarPlayers(final ServerLevel level) {
		Set<ServerPlayer> near = new HashSet<>();
		for (ServerPlayer p : level.players()) {
			if (p.isAlive() && !p.isSpectator() && p.distanceToSqr(this) <= BOSS_BAR_RANGE * BOSS_BAR_RANGE) {
				near.add(p);
			}
		}
		for (ServerPlayer p : Set.copyOf(this.bossEvent.getPlayers())) {
			if (!near.contains(p)) {
				this.bossEvent.removePlayer(p);
			}
		}
		for (ServerPlayer p : near) {
			if (!this.bossEvent.getPlayers().contains(p)) {
				this.bossEvent.addPlayer(p);
			}
		}
	}

	public ServerBossEvent getBossEvent() {
		return this.bossEvent;
	}

	@Override
	public void stopSeenByPlayer(final ServerPlayer player) {
		super.stopSeenByPlayer(player);
		this.bossEvent.removePlayer(player);
	}

	@Override
	public void remove(final Entity.RemovalReason reason) {
		super.remove(reason);
		this.bossEvent.removeAllPlayers();
	}

	@Override
	public void setCustomName(final @Nullable Component name) {
		super.setCustomName(name);
		this.bossEvent.setName(this.getDisplayName());
	}

	// ---- movement goals (no pathfinding: a mob this big walks straight at you and sidesteps when it bumps into things) ----

	private double horizontalDistanceTo(final double x, final double z) {
		double dx = x - this.getX(), dz = z - this.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** What it attacks: any living mob or player, but never another Gojira or an armor stand. (Creative and spectator players
	 * are already left out by the targeting rules.) */
	public static boolean isPrey(final LivingEntity e) {
		return !(e instanceof Gojira) && !(e instanceof ArmorStand) && (e instanceof Mob || e instanceof Player);
	}

	/** Always angry: goes for the NEAREST living mob or survival/adventure player within 64 blocks, even out of sight (it's
	 * huge: its eyes are 15 blocks up). */
	class HuntEverythingGoal extends NearestAttackableTargetGoal<LivingEntity> {
		HuntEverythingGoal() {
			super(Gojira.this, LivingEntity.class, 10, false, false, (e, level) -> isPrey(e));
			this.targetConditions.ignoreLineOfSight();
		}
	}

	class ChaseGoal extends Goal {
		private int stuckTicks, sidestepTicks;
		private Vec3 sidestep = Vec3.ZERO;

		ChaseGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity t = Gojira.this.getTarget();
			return t != null && t.isAlive();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity t = Gojira.this.getTarget();
			if (t == null) {
				return;
			}
			Gojira.this.getLookControl().setLookAt(t, 10.0F, 10.0F);
			if (Gojira.this.beamTicks > 0) {
				return; // stands still while it uses the beam
			}
			if (this.sidestepTicks > 0) {
				this.sidestepTicks--;
				Gojira.this.getMoveControl().setWantedPosition(this.sidestep.x, this.sidestep.y, this.sidestep.z, 1.0);
				return;
			}
			double hd = Gojira.this.horizontalDistanceTo(t.getX(), t.getZ());
			if (hd > STOMP_RADIUS * 0.5) {
				Gojira.this.getMoveControl().setWantedPosition(t.getX(), t.getY(), t.getZ(), 1.0);
				this.stuckTicks = Gojira.this.horizontalCollision ? this.stuckTicks + 1 : 0;
				if (this.stuckTicks > 30) {
					this.stuckTicks = 0;
					this.sidestepTicks = 40;
					Vec3 toT = new Vec3(t.getX() - Gojira.this.getX(), 0, t.getZ() - Gojira.this.getZ()).normalize();
					Vec3 side = new Vec3(-toT.z, 0, toT.x).scale(Gojira.this.random.nextBoolean() ? 12.0 : -12.0);
					this.sidestep = Gojira.this.position().add(side).add(toT.scale(4.0));
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
			if (Gojira.this.getTarget() != null || Gojira.this.random.nextInt(160) != 0) {
				return false;
			}
			double a = Gojira.this.random.nextDouble() * Math.PI * 2, r = 12.0 + Gojira.this.random.nextDouble() * 24.0;
			this.dest = Gojira.this.position().add(Math.cos(a) * r, 0, Math.sin(a) * r);
			this.ticks = 200;
			return true;
		}

		@Override
		public boolean canContinueToUse() {
			return this.dest != null && this.ticks > 0 && Gojira.this.getTarget() == null
					&& Gojira.this.horizontalDistanceTo(this.dest.x, this.dest.z) > 2.0;
		}

		@Override
		public void tick() {
			this.ticks--;
			if (this.dest != null) {
				Gojira.this.getMoveControl().setWantedPosition(this.dest.x, this.dest.y, this.dest.z, 0.6);
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
			default -> super.handleEntityEvent(id);
		}
	}

	/** 0 = not stomping; goes 0 to 1 while a leg lifts (to ~0.67) and slams down. */
	public float getStompProgress(final float partialTicks) {
		int total = STOMP_WINDUP + 8;
		return this.clientStompTicks <= 0 ? 0.0F : Math.min(1.0F, (total - this.clientStompTicks + partialTicks) / total);
	}

	/** 0..1 how brightly the spikes glow (ramps up during the beam wind-up, full while firing, fades at the end). */
	public float getSpikeGlow(final float partialTicks) {
		if (this.clientBeamTicks <= 0) {
			return 0.0F;
		}
		float elapsed = BEAM_WINDUP + BEAM_FIRE - this.clientBeamTicks + partialTicks;
		if (elapsed < BEAM_WINDUP) {
			return elapsed / BEAM_WINDUP;
		}
		return Math.max(0.0F, Math.min(1.0F, (this.clientBeamTicks - partialTicks) / 6.0F)); // fades out over the last 6 ticks
	}

	/** True while the beam is actually firing (mouth wide open). */
	public boolean isBeamFiring() {
		return this.clientBeamTicks > 0 && this.clientBeamTicks <= BEAM_FIRE;
	}

	/** 0..1 how far the jaw is open (roaring or firing the beam). */
	public float getJawOpen(final float partialTicks) {
		float roar = 0.0F;
		if (this.clientRoarTicks > 0) {
			float t = (ROAR_ANIM_TICKS - this.clientRoarTicks + partialTicks) / ROAR_ANIM_TICKS;
			roar = t < 0.15F ? t / 0.15F : (t > 0.8F ? (1.0F - t) / 0.2F : 1.0F);
		}
		return Math.max(roar, this.isBeamFiring() ? 1.0F : 0.0F);
	}

	/** 0 = not swiping; 0 to 1 through a claw swipe (the claws come down at {@link #SWIPE_WINDUP}/{@link #SWIPE_ANIM}). */
	public float getSwipeProgress(final float partialTicks) {
		return this.clientSwipeTicks <= 0 ? 0.0F : Math.min(1.0F, (SWIPE_ANIM - this.clientSwipeTicks + partialTicks) / SWIPE_ANIM);
	}

	/** 0 = not whipping; 0 to 1 through a tail whip. */
	public float getWhipProgress(final float partialTicks) {
		return this.clientWhipTicks <= 0 ? 0.0F : Math.min(1.0F, (WHIP_ANIM - this.clientWhipTicks + partialTicks) / WHIP_ANIM);
	}

	/** 0 = walking upright, 1 = fully swimming (eases in and out over a second). */
	public float getSwimAmount(final float partialTicks) {
		return Mth.lerp(partialTicks, this.swimAnimO, this.swimAnim);
	}

	/** 0 = alive; 0 to 1 through the death animation. */
	public float getDeathProgress(final float partialTicks) {
		return this.deathTime <= 0 ? 0.0F : Math.min(1.0F, (this.deathTime + partialTicks) / DEATH_TICKS);
	}

	/** How far it has toppled over, in degrees (0 standing, 90 lying on its side); a slow start, like a falling tree. */
	public static float toppleDegrees(final float deathTicks) {
		float t = Mth.clamp(deathTicks / TOPPLE_TICKS, 0.0F, 1.0F);
		return 90.0F * t * t;
	}

	/** Which way it falls when it dies (to its side): matches the renderer's roll. */
	public Vec3 toppleDirection() {
		float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
		return new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
	}

	// ---- death: only /kill or the void; a slow topple instead of the usual quick flip and poof ----

	@Override
	public void die(final DamageSource source) {
		// remember whether it died underwater: then it leaves its skeleton on the sea floor when the animation ends
		if (!this.killed) {
			this.diedUnderwater = this.isInWater() && this.getFluidHeight(FluidTags.WATER) >= SKELETON_WATER_DEPTH;
		}
		this.killed = true;
		super.die(source);
	}

	public boolean diedUnderwater() {
		return this.diedUnderwater;
	}

	/** How many bone blocks its skeleton placed (-1 = none yet / it didn't die underwater). */
	public int getSkeletonBlocks() {
		return this.skeletonBlocks;
	}

	/** Lays its skeleton down where it fell (on its side, snout the way it faced), on the sea floor below. */
	private void leaveSkeleton(final ServerLevel level) {
		Direction facing = Direction.fromYRot(this.yBodyRot);
		Vec3 fall = this.toppleDirection();
		Direction fell = Direction.getApproximateNearest(fall.x, 0, fall.z);
		this.skeletonBlocks = GojiraSkeleton.place(level, this.blockPosition(), facing, fell);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SKELETON_DEATH, SoundSource.HOSTILE, 3.0F, 0.4F);
	}

	@Override
	protected void tickDeath() {
		this.deathTime++;
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		this.bossEvent.setProgress(0.0F);
		BlockState ground = level.getBlockState(BlockPos.containing(this.getX(), this.getY() - 0.5, this.getZ()));
		BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, ground.isAir() ? net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState() : ground);
		Vec3 dir = this.toppleDirection();
		if (this.deathTime < TOPPLE_TICKS && this.deathTime % 4 == 0) {
			// its feet scrape as it starts to tip over
			level.sendParticles(dust, true, false, this.getX(), this.getY() + 0.3, this.getZ(), 20, 5.0, 0.3, 5.0, 0.1);
			this.deathDustBursts++;
		}
		if (this.deathTime == TOPPLE_TICKS) {
			// CRASH: a long line of dust where its body hits the ground
			this.deathDustBursts++;
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 5.0F, 0.4F);
			for (double d = 0; d < VISUAL_HEIGHT; d += 2.5) {
				Vec3 p = this.position().add(dir.scale(d));
				level.sendParticles(dust, true, false, p.x, p.y + 0.3, p.z, 16, 2.5, 0.3, 2.5, 0.2);
				level.sendParticles(ParticleTypes.CLOUD, true, false, p.x, p.y + 1.0, p.z, 4, 2.0, 0.5, 2.0, 0.05);
			}
		}
		if (this.deathTime > TOPPLE_TICKS && this.deathTime % 5 == 0) {
			// dust settles around it
			Vec3 p = this.position().add(dir.scale(this.random.nextDouble() * VISUAL_HEIGHT));
			level.sendParticles(ParticleTypes.CLOUD, true, false, p.x, p.y + 1.0, p.z, 3, 2.0, 0.5, 2.0, 0.01);
		}
		if (this.deathTime >= DEATH_TICKS && !this.isRemoved()) {
			if (this.diedUnderwater) {
				this.leaveSkeleton(level);
			}
			for (double d = 0; d < VISUAL_HEIGHT; d += 4.0) {
				Vec3 p = this.position().add(dir.scale(d)).add(0, GojiraDimensions.BODY_HALF_WIDTH * 0.5, 0);
				level.sendParticles(ParticleTypes.POOF, true, false, p.x, p.y, p.z, 10, 3.0, 2.0, 3.0, 0.05);
			}
			level.broadcastEntityEvent(this, (byte) 60);
			this.remove(Entity.RemovalReason.KILLED);
		}
	}

	// ---- for tests ----

	/** Tests: stop picking targets and stop walking (so it can't wander off and bother other tests). */
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

	public int getDeathDustBursts() {
		return this.deathDustBursts;
	}

	public int getRoars() {
		return this.roars;
	}

	public int getBeamTicks() {
		return this.beamTicks;
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
		return false; // it uses its own stomp, swipe, whip and beam attacks instead
	}

	@Override
	protected float getSoundVolume() {
		return 3.0F;
	}

	@Override
	public float getVoicePitch() {
		return 0.5F;
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return null; // it roars on its own schedule instead
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
		this.playSound(SoundEvents.RAVAGER_STEP, 2.5F, 0.4F);
	}

	@Override
	protected void addAdditionalSaveData(final ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("BeamCooldown", this.beamCooldown);
		output.putBoolean("DiedUnderwater", this.diedUnderwater);
		output.putBoolean("Killed", this.killed);
	}

	@Override
	protected void readAdditionalSaveData(final ValueInput input) {
		super.readAdditionalSaveData(input);
		this.beamCooldown = input.getIntOr("BeamCooldown", 0);
		this.diedUnderwater = input.getBooleanOr("DiedUnderwater", false);
		// 0.1.6: a Gojira saved mid-death (Health 0) used to load with health 1 (the "can't be beaten" floor) and regen to full.
		// Older saves have no "Killed" flag, so a saved health of 0 counts too. Then the death animation just carries on.
		this.killed = input.getBooleanOr("Killed", false) || input.getFloatOr("Health", MAX_HEALTH) <= 0.0F;
		if (this.killed) {
			super.setHealth(0.0F);
		}
		if (this.hasCustomName()) {
			this.bossEvent.setName(this.getDisplayName());
		}
	}
}
