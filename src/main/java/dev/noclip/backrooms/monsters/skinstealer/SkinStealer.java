package dev.noclip.backrooms.monsters.skinstealer;

import dev.noclip.backrooms.monsters.Tuning;
import dev.noclip.backrooms.monsters.Retreat;
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

/**
 * The Skin-Stealer: a gaunt pale humanoid wearing ill-fitting skin.
 *
 * Three-stage psychological AI:
 *  - DISGUISED (0): shuffles slowly toward a wanderer, stops 4-6 blocks away and stares silently with a tilted
 *    head without attacking immediately.
 *  - REVEALING (1): if the player lingers nearby (~5.5s), steps too close (<2.8 blocks), or attacks it, it
 *    freezes for 1.1s in a violent spasmodic reveal (unhinging its jaw) before lunging.
 *  - HUNTING (2): sprints at high speed and executes overhead claw slashes (8 dmg + brief Slowness). If prey
 *    escapes far away, it eventually slips back into DISGUISED mode.
 */
public class SkinStealer extends Monster {
	public static final int STATE_DISGUISED = 0, STATE_REVEALING = 1, STATE_HUNTING = 2;
	private static final int REVEAL_TICKS = 22;
	private static final int SLASH_HIT_AT = 9, SLASH_TICKS = 17;
	private static final byte EVENT_REVEAL = 4, EVENT_SLASH = 5;

	private static final EntityDataAccessor<Integer> DATA_MODE =
			SynchedEntityData.defineId(SkinStealer.class, EntityDataSerializers.INT);

	public final AnimationState revealAnimation = new AnimationState();
	public final AnimationState slashAnimation = new AnimationState();

	private int revealTimer = -1;
	private int slashTimer = -1, slashCooldown;
	private LivingEntity slashTarget;
	private int watchTicks;
	private int calmTicks;

	public SkinStealer(EntityType<? extends SkinStealer> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, Tuning.hp(36))
				.add(Attributes.ATTACK_DAMAGE, 8)
				.add(Attributes.MOVEMENT_SPEED, 0.25)
				.add(Attributes.FOLLOW_RANGE, 32)
				.add(Attributes.ARMOR, 2);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_MODE, STATE_DISGUISED);
	}

	public int mode() {
		return entityData.get(DATA_MODE);
	}

	void setMode(int mode) {
		entityData.set(DATA_MODE, mode);
	}

	@Override
	protected void registerGoals() {
		Retreat.add(this, goalSelector, targetSelector);
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new DeceptionHuntGoal(this));
		goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
		goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16));
		goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	void beginReveal() {
		if (mode() != STATE_DISGUISED) return;
		setMode(STATE_REVEALING);
		revealTimer = 0;
		level().broadcastEntityEvent(this, EVENT_REVEAL);
		playSound(SoundEvents.SKELETON_HURT, 1.0F, 0.45F);
		playSound(SoundEvents.WARDEN_ROAR, 0.55F, 0.75F);
	}

	void beginSlash(LivingEntity target) {
		slashTarget = target;
		slashTimer = 0;
		level().broadcastEntityEvent(this, EVENT_SLASH);
		playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.55F);
	}

	boolean canSlash() {
		return slashTimer < 0 && tickCount >= slashCooldown;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (revealTimer >= 0) {
			revealTimer++;
			if (revealTimer >= REVEAL_TICKS) {
				revealTimer = -1;
				setMode(STATE_HUNTING);
			}
		}
		if (slashTimer >= 0) {
			slashTimer++;
			if (slashTarget != null && slashTarget.isAlive()) {
				getLookControl().setLookAt(slashTarget, 45, 45);
				if (slashTimer == SLASH_HIT_AT && distanceToSqr(slashTarget) <= 9.5) {
					if (doHurtTarget(level, slashTarget)) {
						slashTarget.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
					}
				}
			}
			if (slashTimer >= SLASH_TICKS) {
				slashTimer = -1;
				slashTarget = null;
				slashCooldown = tickCount + 16;
			}
		}
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == EVENT_REVEAL) {
			revealAnimation.start(tickCount);
		} else if (id == EVENT_SLASH) {
			slashAnimation.start(tickCount);
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && mode() == STATE_DISGUISED) beginReveal();
		return hurt;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return mode() == STATE_HUNTING ? SoundEvents.ZOMBIE_AMBIENT : SoundEvents.VILLAGER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.HUSK_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.HUSK_DEATH;
	}

	private static final class DeceptionHuntGoal extends Goal {
		private final SkinStealer mob;

		DeceptionHuntGoal(SkinStealer mob) {
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
			mob.watchTicks = 0;
		}

		@Override
		public void tick() {
			LivingEntity target = mob.getTarget();
			if (target == null) return;
			double distSqr = mob.distanceToSqr(target);
			mob.getLookControl().setLookAt(target, 35, 35);

			int m = mob.mode();
			if (m == STATE_REVEALING) {
				mob.getNavigation().stop();
				return;
			}
			if (m == STATE_DISGUISED) {
				// Slowly approach to 4.5 blocks like a curious wanderer, then stop and stare!
				if (distSqr > 25.0) {
					mob.getNavigation().moveTo(target, 0.72);
				} else {
					mob.getNavigation().stop();
				}
				// Trigger reveal if player gets too close (<2.8 blocks) or lingers nearby (~5.5s)
				if (distSqr < 7.8) {
					mob.beginReveal();
				} else if (distSqr < 56.0) {
					mob.watchTicks++;
					if (mob.watchTicks >= 110) mob.beginReveal();
				} else if (mob.watchTicks > 0) {
					mob.watchTicks--;
				}
				return;
			}

			// STATE_HUNTING: relentless sprint + overhead claw slash!
			if (distSqr > 576.0) {
				if (++mob.calmTicks >= 200) {
					mob.calmTicks = 0;
					mob.watchTicks = 0;
					mob.setMode(STATE_DISGUISED);
					return;
				}
			} else {
				mob.calmTicks = 0;
			}

			if (distSqr <= 6.2 && mob.canSlash()) {
				mob.getNavigation().stop();
				mob.beginSlash(target);
			} else if (mob.slashTimer < 0) {
				mob.getNavigation().moveTo(target, 1.42);
			}
		}
	}
}
