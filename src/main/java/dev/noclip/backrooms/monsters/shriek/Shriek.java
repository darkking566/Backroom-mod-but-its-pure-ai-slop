package dev.noclip.backrooms.monsters.shriek;

import java.util.EnumSet;
import java.util.List;
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
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Shriek (啼物): a surreal arched tangle of brightly colored vibrating tendrils on four splayed ribbon feet.
 *
 * Ecosystem Alarm AI:
 *  - Fragile (22 HP) and avoids prolonged melee.
 *  - Upon spotting a wanderer within 16 blocks, it stops, flares all its colorful crests, and unleashes a
 *    piercing disorienting shriek:
 *      1) Disorients & knocks back players within 10 blocks (Darkness + Nausea + minor sonic damage).
 *      2) Alerts EVERY other monster within 36 blocks (Hounds, Clumps, Wretches, Dullers, Skin-Stealers,
 *         Smilers, Marauders), immediately locking their target onto the player!
 *  - After shrieking, it skitters away rapidly for 6 seconds before looking for another angle to call again.
 */
public class Shriek extends Monster {
	private static final EntityDataAccessor<Boolean> DATA_FLEEING =
			SynchedEntityData.defineId(Shriek.class, EntityDataSerializers.BOOLEAN);
	private static final byte EVENT_SHRIEK = 4;

	public final AnimationState shriekAnimation = new AnimationState();

	private int shriekWindup = -1;
	private int shriekCooldown;
	private int fleeTicks;
	private Player alarmTarget;

	public Shriek(EntityType<? extends Shriek> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 22)
				.add(Attributes.ATTACK_DAMAGE, 3)
				.add(Attributes.MOVEMENT_SPEED, 0.28)
				.add(Attributes.FOLLOW_RANGE, 28);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_FLEEING, false);
	}

	public boolean isFleeing() {
		return entityData.get(DATA_FLEEING);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new ShriekAlarmGoal(this));
		goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.75));
		goalSelector.addGoal(7, new RandomLookAroundGoal(this));
	}

	void beginShriek(Player target) {
		alarmTarget = target;
		shriekWindup = 9; // ~0.45s telegraph before sonic blast
		getNavigation().stop();
		level().broadcastEntityEvent(this, EVENT_SHRIEK);
		playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.8F);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (shriekCooldown > 0) shriekCooldown--;
		if (fleeTicks > 0) {
			fleeTicks--;
			entityData.set(DATA_FLEEING, fleeTicks > 0);
		}

		if (shriekWindup >= 0) {
			shriekWindup--;
			if (alarmTarget != null && alarmTarget.isAlive()) {
				getLookControl().setLookAt(alarmTarget, 60, 45);
			}
			if (shriekWindup == 0) {
				unleashShriek(level);
			}
		}
	}

	private void unleashShriek(ServerLevel level) {
		playSound(SoundEvents.GHAST_SCREAM, 1.8F, 1.65F);
		playSound(SoundEvents.WARDEN_SONIC_BOOM, 0.75F, 1.85F);
		level.sendParticles(ParticleTypes.SONIC_BOOM, getX(), getY() + 1.2, getZ(), 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.NOTE, getX(), getY() + 1.5, getZ(), 24, 0.9, 0.6, 0.9, 1.0);

		// 1) Disorient & knock back players within 10 blocks
		AABB blastBox = getBoundingBox().inflate(10.0, 5.0, 10.0);
		Player primary = alarmTarget;
		for (Player p : level.getEntitiesOfClass(Player.class, blastBox)) {
			if (p.isCreative() || p.isSpectator()) continue;
			if (primary == null) primary = p;
			p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 65, 0));
			p.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 75, 0));
			Vec3 push = p.position().subtract(position()).normalize();
			p.push(push.x * 0.65, 0.25, push.z * 0.65);
			p.hurtServer(level, damageSources().mobAttack(this), 2.0F);
		}

		// 2) Ecosystem linkage: rally ALL other monsters within 36 blocks onto the player!
		if (primary != null && primary.isAlive() && !primary.isCreative() && !primary.isSpectator()) {
			AABB rallyBox = getBoundingBox().inflate(36.0, 14.0, 36.0);
			List<Monster> nearby = level.getEntitiesOfClass(Monster.class, rallyBox, m -> m != this && m.isAlive());
			for (Monster ally : nearby) {
				ally.setTarget(primary);
				ally.getNavigation().moveTo(primary, 1.25);
			}
		}

		shriekCooldown = 180; // 9 seconds before next call
		fleeTicks = 120;      // 6 seconds skittering retreat
		entityData.set(DATA_FLEEING, true);
		alarmTarget = null;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && source.getEntity() instanceof Player p && !p.isCreative() && !p.isSpectator()) {
			// Striking it triggers a panic flee (or immediate alarm if off cooldown)
			if (shriekCooldown <= 0 && shriekWindup < 0) {
				beginShriek(p);
			} else {
				fleeTicks = 90;
				entityData.set(DATA_FLEEING, true);
			}
		}
		return hurt;
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == EVENT_SHRIEK) {
			shriekAnimation.start(tickCount);
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.AMETHYST_BLOCK_CHIME;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.GHAST_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.GHAST_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 1.7F;
	}

	private static final class ShriekAlarmGoal extends Goal {
		private final Shriek mob;
		private int repath;

		ShriekAlarmGoal(Shriek mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (mob.shriekWindup >= 0) return true;
			Player nearest = mob.level().getNearestPlayer(mob, 18.0);
			return nearest != null && !nearest.isCreative() && !nearest.isSpectator();
		}

		@Override
		public void tick() {
			if (mob.shriekWindup >= 0) {
				mob.getNavigation().stop();
				return;
			}
			Player nearest = mob.level().getNearestPlayer(mob, 20.0);
			if (nearest == null || nearest.isCreative() || nearest.isSpectator()) return;

			double distSqr = mob.distanceToSqr(nearest);
			// Spot player within 16 blocks -> stop and SHRIEK!
			if (mob.shriekCooldown <= 0 && distSqr <= 256.0 && mob.hasLineOfSight(nearest)) {
				mob.beginShriek(nearest);
				return;
			}

			// Otherwise keep safe distance / flee rapidly after calling
			if (mob.fleeTicks > 0 || distSqr < 81.0) {
				if (--repath <= 0) {
					repath = 10;
					Vec3 away = mob.position().subtract(nearest.position());
					if (away.lengthSqr() < 0.01) away = new Vec3(1, 0, 0);
					away = away.normalize().scale(10.0);
					mob.getNavigation().moveTo(mob.getX() + away.x, mob.getY(), mob.getZ() + away.z, 1.38);
				}
			} else {
				mob.getLookControl().setLookAt(nearest, 35, 35);
			}
		}
	}
}
