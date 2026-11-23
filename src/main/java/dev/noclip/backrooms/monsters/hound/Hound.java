package dev.noclip.backrooms.monsters.hound;

import dev.noclip.backrooms.monsters.Monsters;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Hound: a gaunt thing on all fours with a curtain of black hair over its face. It prowls, and when it has you
 * it lunges: it crouches, springs, rears up and bites, then drops back down (the attack_roar clip, two seconds,
 * the bite landing three quarters of a second in). Backing off during the crouch makes it miss.
 */
public class Hound extends Monster {
	/** Ticks into a lunge: the spring, the bite, the end of it. Matches the attack_roar clip. */
	public static final int SPRING_AT = 11, BITE_AT = 15, LUNGE_TICKS = 40;
	private static final int REST = 10;
	private static final double BITE_SLACK = 1.5;
	private static final byte LUNGE_EVENT = 4;

	/** Client side: when the current lunge started, for the model. */
	public final AnimationState lungeAnimation = new AnimationState();
	private int lunge = -1, restUntil;
	private LivingEntity prey;

	public Hound(EntityType<? extends Hound> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 30)
				.add(Attributes.ATTACK_DAMAGE, 7)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.FOLLOW_RANGE, 40)
				.add(Attributes.ARMOR, 2);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new LungeGoal(this));
		goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
		goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12));
		goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	public boolean lunging() {
		return lunge >= 0;
	}

	boolean canLunge() {
		return lunge < 0 && tickCount >= restUntil;
	}

	/** Starts a lunge at the target; the bite comes {@link #BITE_AT} ticks later if it is still in reach. */
	public void lunge(LivingEntity target) {
		prey = target;
		lunge = 0;
		level().broadcastEntityEvent(this, LUNGE_EVENT);
		playSound(Monsters.HOUND_ROAR, 1.3F, getVoicePitch());
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (lunge < 0) return;
		lunge++;
		if (prey != null && prey.isAlive()) {
			Vec3 to = prey.position().subtract(position()).multiply(1, 0, 1);
			if (lunge < SPRING_AT) getLookControl().setLookAt(prey, 30, 30);
			if (lunge == SPRING_AT && to.lengthSqr() > 1.0E-4) {
				to = to.normalize();
				float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90;
				setYRot(yaw);
				setYBodyRot(yaw);
				setYHeadRot(yaw);
				setDeltaMovement(to.x * 0.45, 0.2, to.z * 0.45);
			}
			if (lunge == BITE_AT && inReach(prey) && hasLineOfSight(prey)) doHurtTarget(level, prey);
		}
		if (lunge >= LUNGE_TICKS) {
			lunge = -1;
			prey = null;
			restUntil = tickCount + REST;
		}
	}

	private boolean inReach(LivingEntity target) {
		double reach = getBbWidth() / 2 + target.getBbWidth() / 2 + BITE_SLACK;
		return isWithinMeleeAttackRange(target) || distanceToSqr(target) <= reach * reach;
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == LUNGE_EVENT) {
			lungeAnimation.start(tickCount);
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return Monsters.HOUND_GROWL;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 140;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return Monsters.HOUND_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return Monsters.HOUND_DEATH;
	}

	/** Chases like any melee mob, but instead of hitting on contact it stops and lunges. */
	static final class LungeGoal extends MeleeAttackGoal {
		private final Hound hound;

		LungeGoal(Hound hound) {
			super(hound, 1.3, true);
			this.hound = hound;
		}

		@Override
		public void tick() {
			if (hound.lunging()) {
				// Holds still for the crouch; the spring itself is a push, not a path.
				hound.getNavigation().stop();
				return;
			}
			super.tick();
		}

		@Override
		protected void checkAndPerformAttack(LivingEntity target) {
			if (hound.canLunge() && canPerformAttack(target)) hound.lunge(target);
		}
	}
}
