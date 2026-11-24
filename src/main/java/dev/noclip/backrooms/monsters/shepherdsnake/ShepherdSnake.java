package dev.noclip.backrooms.monsters.shepherdsnake;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The Shepherd Snake (牧蛇): a colossal ~18-block dark muscular serpent with spiraling muscle bands,
 * a sculpted skull-serpent head, and blazing white eyes.
 *
 * Colossal Wall-Smashing Boss AI:
 *  - Massive 120 HP, full knockback immunity, 48-block tracking range.
 *  - Bulldozes straight through concrete walls, pillars, shelves, cubicle panels, and props when pursuing
 *    or charging prey — smashing a towering tunnel right through the parking structure!
 *  - Safely protects floors (never breaks below feetY), bedrock/indestructible blocks, level exits/doors,
 *    and settlement beds/lockers so it never voids players or ruins exits.
 */
public class ShepherdSnake extends Monster {
	private static final EntityDataAccessor<Boolean> DATA_CHARGING =
			SynchedEntityData.defineId(ShepherdSnake.class, EntityDataSerializers.BOOLEAN);
	private static final byte EVENT_CRUSH = 4;
	private static final int CRUSH_HIT_AT = 10, CRUSH_TICKS = 17;

	public final AnimationState crushAnimation = new AnimationState();

	private int crushTimer = -1, crushCooldown;
	private LivingEntity crushTarget;
	private int chargeTicks;
	private int chargeCooldown;

	public ShepherdSnake(EntityType<? extends ShepherdSnake> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 120)
				.add(Attributes.ATTACK_DAMAGE, 11)
				.add(Attributes.MOVEMENT_SPEED, 0.27)
				.add(Attributes.FOLLOW_RANGE, 48)
				.add(Attributes.ARMOR, 6)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_CHARGING, false);
	}

	public boolean isCharging() {
		return entityData.get(DATA_CHARGING);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new ColossalBulldozeGoal(this));
		goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.68));
		goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 24));
		goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
	}

	void startCrush(LivingEntity target) {
		crushTarget = target;
		crushTimer = 0;
		level().broadcastEntityEvent(this, EVENT_CRUSH);
		playSound(SoundEvents.WARDEN_ROAR, 1.2F, 0.55F);
	}

	boolean canCrush() {
		return crushTimer < 0 && tickCount >= crushCooldown;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (chargeCooldown > 0) chargeCooldown--;
		if (chargeTicks > 0) {
			chargeTicks--;
			entityData.set(DATA_CHARGING, chargeTicks > 0);
		}

		// Heavy rumbling slither warning when moving near players
		if (tickCount % 32 == 0 && getDeltaMovement().horizontalDistanceSqr() > 0.002) {
			playSound(SoundEvents.RAVAGER_STEP, 1.25F, 0.45F);
		}

		// Colossal wall-breaking bulldoze! Breaks obstructing walls/pillars/shelves while pursuing or charging
		LivingEntity target = getTarget();
		if (target != null && target.isAlive()) {
			int interval = isCharging() ? 2 : 5;
			if (tickCount % interval == 0) {
				bulldozeObstacles(level, isCharging());
			}
		}

		// Execute colossal jaw crush attack
		if (crushTimer >= 0) {
			crushTimer++;
			if (crushTarget != null && crushTarget.isAlive()) {
				getLookControl().setLookAt(crushTarget, 45, 45);
				if (crushTimer == CRUSH_HIT_AT && distanceToSqr(crushTarget) <= 22.0) {
					if (doHurtTarget(level, crushTarget)) {
						crushTarget.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 1));
						Vec3 push = crushTarget.position().subtract(position()).normalize();
						crushTarget.push(push.x * 1.1, 0.42, push.z * 1.1);
						playSound(SoundEvents.RAVAGER_ATTACK, 1.4F, 0.65F);
					}
				}
			}
			if (crushTimer >= CRUSH_TICKS) {
				crushTimer = -1;
				crushTarget = null;
				crushCooldown = tickCount + 20;
			}
		}
	}

	/** Smashes obstructing blocks ahead and around the serpent's massive coils without destroying floors or exits. */
	private void bulldozeObstacles(ServerLevel level, boolean wide) {
		Vec3 fwd = Vec3.directionFromRotation(0, getYRot()).normalize();
		int cx = (int) Math.floor(getX() + fwd.x * 1.8);
		int cz = (int) Math.floor(getZ() + fwd.z * 1.8);
		int baseY = getBlockY(); // NEVER break y < baseY (protects floors!)
		int radius = wide ? 2 : 1;
		int maxDy = wide ? 4 : 3;
		int broken = 0;

		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				for (int dy = 0; dy <= maxDy; dy++) {
					BlockPos pos = new BlockPos(cx + dx, baseY + dy, cz + dz);
					BlockState state = level.getBlockState(pos);
					if (state.isAir() || !canSmash(level, pos, state)) continue;
					level.destroyBlock(pos, false);
					broken++;
				}
			}
		}

		if (broken > 0) {
			playSound(SoundEvents.BASALT_BREAK, 1.35F, 0.42F);
			if (broken >= 4) {
				playSound(SoundEvents.WARDEN_DIG, 1.1F, 0.55F);
			}
			level.sendParticles(ParticleTypes.EXPLOSION, cx + 0.5, baseY + 2.0, cz + 0.5, 2, 1.2, 1.0, 1.2, 0.05);
			level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, cx + 0.5, baseY + 1.5, cz + 0.5, 10, 1.4, 1.2, 1.4, 0.03);
		}
	}

	private static boolean canSmash(ServerLevel level, BlockPos pos, BlockState state) {
		float speed = state.getDestroySpeed(level, pos);
		if (speed < 0.0F || speed >= 20.0F) return false; // Never break bedrock or sealed/indestructible blocks
		Block block = state.getBlock();
		String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
		// Protect level exits, doors, vents, settlement beds, lockers, counters & markers
		if (path.contains("exit") || path.contains("door") || path.contains("hatch") || path.contains("gap")
				|| path.contains("vent") || path.contains("inverted") || path.contains("marker")
				|| path.contains("bed") || path.contains("cot") || path.contains("sleeping")
				|| path.contains("locker") || path.contains("counter") || path.contains("portal")) {
			return false;
		}
		return true;
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == EVENT_CRUSH) {
			crushAnimation.start(tickCount);
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.WARDEN_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.RAVAGER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.WARDEN_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.5F;
	}

	private static final class ColossalBulldozeGoal extends Goal {
		private final ShepherdSnake snake;
		private int repath;

		ColossalBulldozeGoal(ShepherdSnake snake) {
			this.snake = snake;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = snake.getTarget();
			return target != null && target.isAlive();
		}

		@Override
		public void stop() {
			snake.getNavigation().stop();
			snake.entityData.set(DATA_CHARGING, false);
		}

		@Override
		public void tick() {
			LivingEntity target = snake.getTarget();
			if (target == null) return;
			snake.getLookControl().setLookAt(target, 35, 35);
			double distSqr = snake.distanceToSqr(target);

			// Initiate bulldozing ram charge at mid-range (6..26 blocks)
			if (snake.chargeCooldown <= 0 && distSqr >= 36.0 && distSqr <= 676.0) {
				snake.chargeTicks = 75;     // 3.75 seconds of relentless wall-smashing ram!
				snake.chargeCooldown = 190; // 9.5s cooldown
				snake.entityData.set(DATA_CHARGING, true);
				snake.playSound(SoundEvents.WARDEN_ROAR, 1.5F, 0.45F);
			}

			if (distSqr <= 16.0 && snake.canCrush()) {
				snake.getNavigation().stop();
				snake.startCrush(target);
				return;
			}

			if (snake.isCharging()) {
				// Direct relentless bulldoze vector straight toward prey even through walls!
				Vec3 dir = target.position().subtract(snake.position()).multiply(1, 0, 1);
				if (dir.lengthSqr() > 0.01) {
					dir = dir.normalize();
					snake.setDeltaMovement(dir.x * 0.42, snake.getDeltaMovement().y, dir.z * 0.42);
					snake.setYRot((float) (Math.atan2(dir.z, dir.x) * (180.0 / Math.PI)) - 90.0F);
					snake.yBodyRot = snake.getYRot();
				}
			} else if (--repath <= 0) {
				repath = 10;
				snake.getNavigation().moveTo(target, 1.18);
			}
		}
	}
}
