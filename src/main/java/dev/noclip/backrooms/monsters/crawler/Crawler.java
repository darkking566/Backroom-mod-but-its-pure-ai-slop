package dev.noclip.backrooms.monsters.crawler;

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
import net.minecraft.world.phys.AABB;

/**
 * The Crawler (爬菌): a spindly spider-like arthropod completely colonized by fluffy white fungal mold puffs.
 *
 * Spore-Cloud Ambush AI:
 *  - Scuttles silently through damp zones and Garden sectors.
 *  - When prey closes within 5.5 blocks (every 5s), it halts briefly to swell its fungal core, then erupts
 *    a dense white spore cloud inflicting Slowness II, Nausea, and Poison.
 *  - Also strikes with quick front-leg lunges at close range.
 *  - On death, its fungal body ruptures in a final smaller spore burst (rewarding ranged kills or backing off).
 */
public class Crawler extends Monster {
	private static final byte EVENT_SPORE = 4, EVENT_LUNGE = 5;
	private static final int LUNGE_HIT_AT = 5, LUNGE_TICKS = 11;

	public final AnimationState sporeAnimation = new AnimationState();
	public final AnimationState lungeAnimation = new AnimationState();

	private int sporeWindup = -1;
	private int sporeCooldown;
	private int lungeTimer = -1, lungeCooldown;
	private LivingEntity lungeTarget;

	public Crawler(EntityType<? extends Crawler> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 26)
				.add(Attributes.ATTACK_DAMAGE, 5)
				.add(Attributes.MOVEMENT_SPEED, 0.30)
				.add(Attributes.FOLLOW_RANGE, 26);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new SporeStalkGoal(this));
		goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.72));
		goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12));
		goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	void beginSporeBurst() {
		sporeWindup = 7; // 0.35s swelling telegraph
		getNavigation().stop();
		level().broadcastEntityEvent(this, EVENT_SPORE);
		playSound(SoundEvents.PUFFER_FISH_BLOW_UP, 0.85F, 0.65F);
	}

	void startLunge(LivingEntity target) {
		lungeTarget = target;
		lungeTimer = 0;
		level().broadcastEntityEvent(this, EVENT_LUNGE);
		playSound(SoundEvents.SPIDER_AMBIENT, 0.9F, 1.35F);
	}

	boolean canLunge() {
		return lungeTimer < 0 && sporeWindup < 0 && tickCount >= lungeCooldown;
	}

	private void releaseSporeCloud(ServerLevel level, double radius, int durationScale) {
		playSound(SoundEvents.FIRE_EXTINGUISH, 0.9F, 1.4F);
		level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.7, getZ(), 28, radius * 0.35, 0.55, radius * 0.35, 0.04);
		level.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + 0.8, getZ(), 36, radius * 0.4, 0.65, radius * 0.4, 0.03);

		AABB area = getBoundingBox().inflate(radius, 2.5, radius);
		for (Player p : level.getEntitiesOfClass(Player.class, area)) {
			if (p.isCreative() || p.isSpectator()) continue;
			p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50 * durationScale, 1));
			p.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 60 * durationScale, 0));
			p.addEffect(new MobEffectInstance(MobEffects.POISON, 40 * durationScale, 0));
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (sporeCooldown > 0) sporeCooldown--;

		// Ambient drifting white spores
		if (tickCount % 14 == 0) {
			level.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + 0.7, getZ(), 2, 0.35, 0.3, 0.35, 0.01);
		}

		if (sporeWindup >= 0) {
			sporeWindup--;
			if (sporeWindup == 0) {
				releaseSporeCloud(level, 5.5, 2);
				sporeCooldown = 100; // 5s cooldown
			}
		}

		if (lungeTimer >= 0) {
			lungeTimer++;
			if (lungeTarget != null && lungeTarget.isAlive()) {
				getLookControl().setLookAt(lungeTarget, 55, 45);
				if (lungeTimer == LUNGE_HIT_AT && distanceToSqr(lungeTarget) <= 6.5) {
					if (doHurtTarget(level, lungeTarget)) {
						lungeTarget.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
					}
				}
			}
			if (lungeTimer >= LUNGE_TICKS) {
				lungeTimer = -1;
				lungeTarget = null;
				lungeCooldown = tickCount + 14;
			}
		}
	}

	@Override
	public void die(DamageSource cause) {
		super.die(cause);
		if (level() instanceof ServerLevel sl) {
			releaseSporeCloud(sl, 3.8, 1);
		}
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == EVENT_SPORE) {
			sporeAnimation.start(tickCount);
		} else if (id == EVENT_LUNGE) {
			lungeAnimation.start(tickCount);
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
		return 1.35F;
	}

	private static final class SporeStalkGoal extends Goal {
		private final Crawler mob;
		private int repath;

		SporeStalkGoal(Crawler mob) {
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
			if (mob.sporeWindup >= 0) {
				mob.getNavigation().stop();
				return;
			}
			LivingEntity target = mob.getTarget();
			if (target == null) return;
			mob.getLookControl().setLookAt(target, 45, 45);
			double distSqr = mob.distanceToSqr(target);

			if (mob.sporeCooldown <= 0 && distSqr <= 28.0 && mob.hasLineOfSight(target)) {
				mob.beginSporeBurst();
				return;
			}
			if (distSqr <= 5.2 && mob.canLunge()) {
				mob.getNavigation().stop();
				mob.startLunge(target);
				return;
			}
			if (--repath <= 0) {
				repath = 8;
				mob.getNavigation().moveTo(target, 1.22);
			}
		}
	}
}
