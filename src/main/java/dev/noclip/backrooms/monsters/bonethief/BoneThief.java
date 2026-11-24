package dev.noclip.backrooms.monsters.bonethief;

import java.util.EnumSet;
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
import net.minecraft.world.phys.Vec3;

/**
 * Entity 121 - Bone Thief (竊骨者):
 * Heavily armored warty quadruped that unhinges its cavernous jaw to expose hypnotic throat-eyes.
 * Making eye contact while its maw is open drains skeletal strength (Slowness + Mining Fatigue) before it chomps.
 */
public class BoneThief extends Monster {
	public static final int STATE_WADDLE = 0;
	public static final int STATE_GAZE = 1;

	private static final EntityDataAccessor<Integer> DATA_STATE =
			SynchedEntityData.defineId(BoneThief.class, EntityDataSerializers.INT);
	private static final byte EVENT_CHOMP = 4;

	public final AnimationState chompAnimation = new AnimationState();

	public BoneThief(EntityType<? extends BoneThief> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 46)
				.add(Attributes.ATTACK_DAMAGE, 8)
				.add(Attributes.MOVEMENT_SPEED, 0.22)
				.add(Attributes.FOLLOW_RANGE, 26)
				.add(Attributes.ARMOR, 8)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.65);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_STATE, STATE_WADDLE);
	}

	public int aiState() {
		return entityData.get(DATA_STATE);
	}

	private void setAiState(int state) {
		entityData.set(DATA_STATE, state);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new GazeAndChompGoal(this));
		goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
		goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12));
		goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	private boolean isTargetLookingAtMaw(LivingEntity target) {
		Vec3 look = target.getViewVector(1.0F).normalize();
		Vec3 toMob = getEyePosition().subtract(target.getEyePosition()).normalize();
		return look.dot(toMob) > 0.65 && hasLineOfSight(target);
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == EVENT_CHOMP) {
			chompAnimation.start(tickCount);
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.FROGSPAWN_HATCH;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.SKELETON_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SKELETON_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.58F;
	}

	private static final class GazeAndChompGoal extends Goal {
		private final BoneThief mob;
		private int chompCooldown;

		GazeAndChompGoal(BoneThief mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = mob.getTarget();
			return target != null && target.isAlive();
		}

		@Override
		public void stop() {
			mob.setAiState(STATE_WADDLE);
			mob.getNavigation().stop();
		}

		@Override
		public void tick() {
			LivingEntity target = mob.getTarget();
			if (target == null || !(mob.level() instanceof ServerLevel serverLevel)) return;
			if (chompCooldown > 0) chompCooldown--;

			mob.getLookControl().setLookAt(target, 35, 35);
			double distSqr = mob.distanceToSqr(target);

			if (distSqr <= 6.5) {
				mob.setAiState(STATE_WADDLE);
				mob.getNavigation().stop();
				if (chompCooldown <= 0) {
					chompCooldown = 26;
					serverLevel.broadcastEntityEvent(mob, EVENT_CHOMP);
					mob.playSound(SoundEvents.EVOKER_FANGS_ATTACK, 1.0F, 0.65F);
					if (mob.doHurtTarget(serverLevel, target)) {
						target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 140, 1));
					}
				}
				return;
			}

			if (distSqr <= 121.0 && mob.hasLineOfSight(target)) {
				mob.setAiState(STATE_GAZE);
				if (mob.tickCount % 20 == 0 && mob.isTargetLookingAtMaw(target)) {
					target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 1));
					target.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 60, 1));
					mob.playSound(SoundEvents.SKELETON_STEP, 0.85F, 0.55F);
				}
				mob.getNavigation().moveTo(target, 0.95);
			} else {
				mob.setAiState(STATE_WADDLE);
				mob.getNavigation().moveTo(target, 1.12);
			}
		}
	}
}
