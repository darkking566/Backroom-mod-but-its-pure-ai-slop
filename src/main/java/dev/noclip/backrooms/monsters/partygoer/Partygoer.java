package dev.noclip.backrooms.monsters.partygoer;

import dev.noclip.backrooms.monsters.Tuning;
import dev.noclip.backrooms.monsters.Retreat;
import dev.noclip.backrooms.monsters.partypooper.Partypooper;
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
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Entity 67 - Partygoer (派對客):
 * Stands motionless like a mannequin clutching a red balloon until prey draws near,
 * then splits its jaw open (ambush_reveal) and skips cheerfully to latch onto wanderers or Partypoopers.
 */
public class Partygoer extends Monster {
	public static final int STATE_STATUE = 0;
	public static final int STATE_SKIP = 1;
	public static final int STATE_GRAB = 2;

	private static final EntityDataAccessor<Integer> DATA_STATE =
			SynchedEntityData.defineId(Partygoer.class, EntityDataSerializers.INT);
	private static final byte EVENT_REVEAL = 4;

	public final AnimationState revealAnimation = new AnimationState();
	private boolean revealed;

	public Partygoer(EntityType<? extends Partygoer> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, Tuning.hp(34))
				.add(Attributes.ATTACK_DAMAGE, 6.5)
				.add(Attributes.MOVEMENT_SPEED, 0.27)
				.add(Attributes.FOLLOW_RANGE, 28)
				.add(Attributes.ARMOR, 2);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_STATE, STATE_STATUE);
	}

	public int aiState() {
		return entityData.get(DATA_STATE);
	}

	private void setAiState(int state) {
		entityData.set(DATA_STATE, state);
	}

	@Override
	protected void registerGoals() {
		Retreat.add(this, goalSelector, targetSelector);
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new PartyChaseGoal(this));
		goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.55) {
			@Override
			public boolean canUse() {
				return revealed && super.canUse();
			}
		});
		targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
		targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Partypooper.class, true));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		LivingEntity target = getTarget();
		if (target == null || !target.isAlive()) {
			revealed = false;
			setAiState(STATE_STATUE);
		} else if (!revealed && distanceToSqr(target) <= 225.0) {
			revealed = true;
			level.broadcastEntityEvent(this, EVENT_REVEAL);
			playSound(SoundEvents.WITCH_CELEBRATE, 1.0F, 0.6F);
			setAiState(STATE_SKIP);
		}
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == EVENT_REVEAL) {
			revealAnimation.start(tickCount);
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return aiState() == STATE_STATUE ? null : SoundEvents.NOTE_BLOCK_CHIME.value();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.SLIME_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SLIME_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.72F;
	}

	private static final class PartyChaseGoal extends Goal {
		private final Partygoer mob;
		private int hitCooldown;

		PartyChaseGoal(Partygoer mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = mob.getTarget();
			return target != null && target.isAlive() && mob.revealed;
		}

		@Override
		public void stop() {
			mob.getNavigation().stop();
		}

		@Override
		public void tick() {
			LivingEntity target = mob.getTarget();
			if (target == null || !(mob.level() instanceof ServerLevel serverLevel)) return;
			if (hitCooldown > 0) hitCooldown--;

			mob.getLookControl().setLookAt(target, 40, 40);
			double distSqr = mob.distanceToSqr(target);
			if (distSqr <= 5.2) {
				mob.setAiState(STATE_GRAB);
				mob.getNavigation().stop();
				if (hitCooldown <= 0) {
					hitCooldown = 20;
					if (mob.doHurtTarget(serverLevel, target)) {
						target.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 120, 0));
						target.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 0));
					}
				}
			} else {
				mob.setAiState(STATE_SKIP);
				mob.getNavigation().moveTo(target, 1.24);
			}
		}
	}
}
