package dev.noclip.backrooms.monsters.nguithrxurh;

import dev.noclip.backrooms.monsters.Tuning;
import dev.noclip.backrooms.monsters.Retreat;
import java.util.EnumSet;
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
 * Nguithr'xurh: a deep-zone apex arachnid predator with black-and-white banded legs, pinpoint reflective eyes,
 * and a radiating halo of parasitic web-tendrils.
 *
 * Threat-Display & Explosive Pounce AI:
 *  - High durability (48 HP, 4 armor) and fast skittering pursuit.
 *  - At mid-range (6..15 blocks), rears its front four banded legs high in a 0.45s threat display, then
 *    launches an explosive forward leap directly onto prey!
 *  - Strikes inflict bleeding (Wither), Weakness, and brief parasitic web-snare Slowness.
 */
public class Nguithrxurh extends Monster {
	private static final byte EVENT_THREAT = 4, EVENT_BITE = 5;
	private static final int BITE_HIT_AT = 5, BITE_TICKS = 11;

	public final AnimationState threatAnimation = new AnimationState();
	public final AnimationState biteAnimation = new AnimationState();

	private int threatWindup = -1;
	private int pounceCooldown;
	private int biteTimer = -1, biteCooldown;
	private LivingEntity attackTarget;

	public Nguithrxurh(EntityType<? extends Nguithrxurh> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, Tuning.hp(48))
				.add(Attributes.ATTACK_DAMAGE, 8)
				.add(Attributes.MOVEMENT_SPEED, 0.33)
				.add(Attributes.FOLLOW_RANGE, 34)
				.add(Attributes.ARMOR, 4)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.35);
	}

	@Override
	protected void registerGoals() {
		Retreat.add(this, goalSelector, targetSelector);
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new ApexPounceGoal(this));
		goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.75));
		goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16));
		goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	void beginThreatPounce(LivingEntity target) {
		attackTarget = target;
		threatWindup = 9; // 0.45s threat-display telegraph before leaping
		getNavigation().stop();
		level().broadcastEntityEvent(this, EVENT_THREAT);
		playSound(SoundEvents.SPIDER_AMBIENT, 1.3F, 0.55F);
	}

	void startBite(LivingEntity target) {
		attackTarget = target;
		biteTimer = 0;
		level().broadcastEntityEvent(this, EVENT_BITE);
		playSound(SoundEvents.SPIDER_HURT, 0.9F, 0.7F);
	}

	boolean canBite() {
		return biteTimer < 0 && threatWindup < 0 && tickCount >= biteCooldown;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (pounceCooldown > 0) pounceCooldown--;

		if (threatWindup >= 0) {
			threatWindup--;
			if (attackTarget != null && attackTarget.isAlive()) {
				getLookControl().setLookAt(attackTarget, 60, 45);
			}
			if (threatWindup == 0 && attackTarget != null && attackTarget.isAlive()) {
				// Explosive forward leap!
				Vec3 dir = attackTarget.position().subtract(position());
				Vec3 horiz = new Vec3(dir.x, 0, dir.z);
				if (horiz.lengthSqr() > 0.01) horiz = horiz.normalize();
				setDeltaMovement(horiz.x * 1.05, 0.44, horiz.z * 1.05);
				playSound(SoundEvents.SPIDER_STEP, 1.4F, 0.45F);
				level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.5, getZ(), 12, 0.5, 0.2, 0.5, 0.05);
				pounceCooldown = 110; // 5.5s cooldown
				startBite(attackTarget);
			}
		}

		if (biteTimer >= 0) {
			biteTimer++;
			if (attackTarget != null && attackTarget.isAlive()) {
				getLookControl().setLookAt(attackTarget, 55, 45);
				if (biteTimer == BITE_HIT_AT && distanceToSqr(attackTarget) <= 9.5) {
					if (doHurtTarget(level, attackTarget)) {
						attackTarget.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
						attackTarget.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
						attackTarget.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 1));
						level.sendParticles(ParticleTypes.ASH, attackTarget.getX(), attackTarget.getY() + 1.0,
								attackTarget.getZ(), 10, 0.4, 0.4, 0.4, 0.04);
					}
				}
			}
			if (biteTimer >= BITE_TICKS) {
				biteTimer = -1;
				biteCooldown = tickCount + 12;
			}
		}
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == EVENT_THREAT) {
			threatAnimation.start(tickCount);
		} else if (id == EVENT_BITE) {
			biteAnimation.start(tickCount);
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.SPIDER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.SPIDER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SPIDER_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.62F;
	}

	private static final class ApexPounceGoal extends Goal {
		private final Nguithrxurh mob;
		private int repath;

		ApexPounceGoal(Nguithrxurh mob) {
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
			mob.getNavigation().stop();
		}

		@Override
		public void tick() {
			if (mob.threatWindup >= 0) {
				mob.getNavigation().stop();
				return;
			}
			LivingEntity target = mob.getTarget();
			if (target == null) return;
			mob.getLookControl().setLookAt(target, 45, 45);
			double distSqr = mob.distanceToSqr(target);

			if (mob.pounceCooldown <= 0 && distSqr >= 36.0 && distSqr <= 225.0 && mob.hasLineOfSight(target) && mob.onGround()) {
				mob.beginThreatPounce(target);
				return;
			}
			if (distSqr <= 6.8 && mob.canBite()) {
				mob.getNavigation().stop();
				mob.startBite(target);
				return;
			}
			if (--repath <= 0) {
				repath = 7;
				mob.getNavigation().moveTo(target, 1.28);
			}
		}
	}
}
