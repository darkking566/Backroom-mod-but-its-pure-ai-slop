package dev.noclip.backrooms.monsters.bacteria;

import dev.noclip.backrooms.monsters.Tuning;
import dev.noclip.backrooms.monsters.Retreat;
import dev.noclip.backrooms.monsters.smiler.Smiler;
import java.util.EnumSet;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Bacteria (無盡之菌):
 * Towering tangle of black mycelium and corroded copper wires that twitches in dim halls,
 * then erupts into a distorted shriek and high-speed charge upon spotting prey.
 */
public class Bacteria extends Monster {
	public static final int STATE_IDLE = 0;
	public static final int STATE_SPRINT = 1;
	public static final int STATE_FLINCH = 2;

	private static final EntityDataAccessor<Integer> DATA_STATE =
			SynchedEntityData.defineId(Bacteria.class, EntityDataSerializers.INT);
	private static final byte EVENT_LUNGE = 4;

	public final AnimationState lungeAnimation = new AnimationState();
	private int flinchTicks;

	public Bacteria(EntityType<? extends Bacteria> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, Tuning.hp(44))
				.add(Attributes.ATTACK_DAMAGE, 7.5)
				.add(Attributes.MOVEMENT_SPEED, 0.30)
				.add(Attributes.FOLLOW_RANGE, 38)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.35);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_STATE, STATE_IDLE);
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
		goalSelector.addGoal(2, new BacteriaChaseGoal(this));
		goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
		goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (level().isClientSide() && random.nextInt(5) == 0) {
			level().addParticle(ParticleTypes.MYCELIUM,
					getX() + (random.nextDouble() - 0.5) * 0.7,
					getY() + 0.4 + random.nextDouble() * 2.0,
					getZ() + (random.nextDouble() - 0.5) * 0.7,
					0, 0, 0);
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (flinchTicks > 0) {
			flinchTicks--;
			setAiState(STATE_FLINCH);
			getNavigation().stop();
			return;
		}
		LivingEntity target = getTarget();
		if (target != null && target.isAlive()) {
			if (aiState() != STATE_SPRINT) {
				playSound(SoundEvents.WARDEN_ROAR, 0.9F, 1.45F);
			}
			setAiState(STATE_SPRINT);
		} else {
			setAiState(STATE_IDLE);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && source.getEntity() instanceof Player p
				&& (Smiler.isLightItem(p.getMainHandItem()) || Smiler.isLightItem(p.getOffhandItem()))) {
			flinchTicks = 28;
		}
		return hurt;
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == EVENT_LUNGE) {
			lungeAnimation.start(tickCount);
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return aiState() == STATE_SPRINT ? SoundEvents.ENDERMAN_SCREAM : SoundEvents.SCULK_CLICKING;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.WARDEN_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.WARDEN_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 1.38F;
	}

	private static final class BacteriaChaseGoal extends Goal {
		private final Bacteria mob;
		private int attackCooldown;

		BacteriaChaseGoal(Bacteria mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = mob.getTarget();
			return mob.flinchTicks <= 0 && target != null && target.isAlive();
		}

		@Override
		public void stop() {
			mob.getNavigation().stop();
		}

		@Override
		public void tick() {
			LivingEntity target = mob.getTarget();
			if (target == null || !(mob.level() instanceof ServerLevel serverLevel)) return;
			if (attackCooldown > 0) attackCooldown--;

			mob.getLookControl().setLookAt(target, 50, 45);
			double distSqr = mob.distanceToSqr(target);
			if (distSqr <= 6.2) {
				mob.getNavigation().stop();
				if (attackCooldown <= 0) {
					attackCooldown = 22;
					serverLevel.broadcastEntityEvent(mob, EVENT_LUNGE);
					if (mob.doHurtTarget(serverLevel, target)) {
						target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0));
						target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0));
					}
				}
			} else {
				mob.getNavigation().moveTo(target, 1.38);
			}
		}
	}
}
