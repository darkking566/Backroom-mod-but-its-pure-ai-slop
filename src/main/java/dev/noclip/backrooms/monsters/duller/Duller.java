package dev.noclip.backrooms.monsters.duller;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.phys.Vec3;

/**
 * The Duller: a smooth, featureless grey-white plaster humanoid that stalks along concrete walls and pillars.
 *
 * Wall-Phasing Ambush AI:
 *  - Emits low scraping/rustling sounds and grey dust when hugging walls near a wanderer (warning players to
 *    step away from walls).
 *  - When prey approaches a wall or pillar (or stands behind one), the Duller phases cleanly through the wall
 *    in a burst of plaster dust to ambush from behind, then lunges with a stiff-armed strike.
 *  - If hit in open combat, it briefly retreats toward the nearest wall to reset its ambush.
 */
public class Duller extends Monster {
	private static final int STRIKE_HIT_AT = 9, STRIKE_TICKS = 16;
	private static final byte EVENT_PHASE = 4, EVENT_STRIKE = 5;

	public final AnimationState phaseAnimation = new AnimationState();
	public final AnimationState strikeAnimation = new AnimationState();

	private int strikeTimer = -1, strikeCooldown;
	private LivingEntity strikeTarget;
	private int phaseCooldown;
	private int retreatTicks;

	public Duller(EntityType<? extends Duller> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 30)
				.add(Attributes.ATTACK_DAMAGE, 7)
				.add(Attributes.MOVEMENT_SPEED, 0.27)
				.add(Attributes.FOLLOW_RANGE, 32)
				.add(Attributes.ARMOR, 3);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new WallPhaseAmbushGoal(this));
		goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.65));
		goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16));
		goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	void startStrike(LivingEntity target) {
		strikeTarget = target;
		strikeTimer = 0;
		level().broadcastEntityEvent(this, EVENT_STRIKE);
		playSound(SoundEvents.PLAYER_ATTACK_STRONG, 0.9F, 0.6F);
	}

	boolean canStrike() {
		return strikeTimer < 0 && tickCount >= strikeCooldown;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (phaseCooldown > 0) phaseCooldown--;
		if (retreatTicks > 0) retreatTicks--;

		// Emit eerie wall-scraping warning sound + dust when near a wall and a player is nearby
		if (tickCount % 35 == 0 && isNearSolidWall(blockPosition())) {
			Player nearest = level.getNearestPlayer(this, 18.0);
			if (nearest != null) {
				playSound(SoundEvents.SOUL_SAND_STEP, 0.75F, 0.45F);
				level.sendParticles(ParticleTypes.ASH, getX(), getY() + 1.2, getZ(), 6, 0.35, 0.6, 0.35, 0.01);
			}
		}

		if (strikeTimer >= 0) {
			strikeTimer++;
			if (strikeTarget != null && strikeTarget.isAlive()) {
				getLookControl().setLookAt(strikeTarget, 45, 45);
				if (strikeTimer == STRIKE_HIT_AT && distanceToSqr(strikeTarget) <= 9.0) {
					if (doHurtTarget(level, strikeTarget)) {
						strikeTarget.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 70, 1));
					}
				}
			}
			if (strikeTimer >= STRIKE_TICKS) {
				strikeTimer = -1;
				strikeTarget = null;
				strikeCooldown = tickCount + 16;
			}
		}
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == EVENT_PHASE) {
			phaseAnimation.start(tickCount);
		} else if (id == EVENT_STRIKE) {
			strikeAnimation.start(tickCount);
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt) retreatTicks = 36; // Briefly recoil toward a wall after taking a hit
		return hurt;
	}

	boolean isNearSolidWall(BlockPos pos) {
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			if (!level().getBlockState(pos.relative(dir)).isAir()) return true;
			if (!level().getBlockState(pos.relative(dir, 2)).isAir()) return true;
		}
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.GRAVEL_STEP;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.STONE_HIT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.STONE_BREAK;
	}

	private static final class WallPhaseAmbushGoal extends Goal {
		private final Duller duller;

		WallPhaseAmbushGoal(Duller duller) {
			this.duller = duller;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = duller.getTarget();
			return target != null && target.isAlive();
		}

		@Override
		public void stop() {
			duller.getNavigation().stop();
		}

		@Override
		public void tick() {
			LivingEntity target = duller.getTarget();
			if (target == null) return;
			double distSqr = duller.distanceToSqr(target);
			duller.getLookControl().setLookAt(target, 40, 40);

			// Brief tactical retreat toward nearest wall when struck
			if (duller.retreatTicks > 0) {
				Vec3 away = duller.position().subtract(target.position()).normalize();
				duller.getNavigation().moveTo(duller.getX() + away.x * 4.0, duller.getY(), duller.getZ() + away.z * 4.0, 1.15);
				return;
			}

			// Wall-Phase Ambush: if target is within 4..14 blocks and near a wall/pillar, phase behind them!
			if (duller.phaseCooldown == 0 && distSqr >= 16.0 && distSqr <= 196.0
					&& duller.isNearSolidWall(target.blockPosition()) && tryPhaseNear(target)) {
				duller.phaseCooldown = 150; // 7.5s cooldown
				return;
			}

			if (distSqr <= 5.8 && duller.canStrike()) {
				duller.getNavigation().stop();
				duller.startStrike(target);
			} else if (duller.strikeTimer < 0) {
				duller.getNavigation().moveTo(target, 1.18);
			}
		}

		/** Phases through concrete to emerge next to a wall 2.5-3.5 blocks behind or beside the prey. */
		private boolean tryPhaseNear(LivingEntity target) {
			if (!(duller.level() instanceof ServerLevel level)) return false;
			Vec3 look = target.getLookAngle().multiply(1, 0, 1);
			if (look.lengthSqr() < 1.0E-4) look = new Vec3(0, 0, 1);
			look = look.normalize();

			// Prefer emerging behind the player near a solid wall
			double[] angles = {Math.PI, Math.PI * 0.75, -Math.PI * 0.75, Math.PI * 0.5, -Math.PI * 0.5};
			for (double ang : angles) {
				double rx = look.x * Math.cos(ang) - look.z * Math.sin(ang);
				double rz = look.x * Math.sin(ang) + look.z * Math.cos(ang);
				int bx = (int) Math.floor(target.getX() + rx * 2.8);
				int by = target.getBlockY();
				int bz = (int) Math.floor(target.getZ() + rz * 2.8);
				BlockPos spot = new BlockPos(bx, by, bz);
				if (!level.isLoaded(spot)) continue;
				if (!level.getBlockState(spot.below()).isSolid()) continue;
				if (!level.getBlockState(spot).isAir() || !level.getBlockState(spot.above()).isAir()) continue;
				if (!duller.isNearSolidWall(spot)) continue;

				// Depart in a cloud of plaster dust & emerge from the wall!
				level.sendParticles(ParticleTypes.CLOUD, duller.getX(), duller.getY() + 1.1, duller.getZ(), 10, 0.3, 0.7, 0.3, 0.02);
				duller.teleportTo(bx + 0.5, by, bz + 0.5);
				level.sendParticles(ParticleTypes.CLOUD, duller.getX(), duller.getY() + 1.1, duller.getZ(), 12, 0.3, 0.7, 0.3, 0.02);
				level.broadcastEntityEvent(duller, EVENT_PHASE);
				duller.playSound(SoundEvents.BASALT_BREAK, 1.0F, 0.5F);
				return true;
			}
			return false;
		}
	}
}
