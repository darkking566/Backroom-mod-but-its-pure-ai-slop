package dev.noclip.backrooms.monsters.clump;

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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Clump: a blind knot of eight grasping human arms that scuttles at terrifying speed toward any noise.
 *
 * Acoustic AI:
 *  - Completely blind: has no eyesight targeting. Sneaking (Shift) or standing completely still makes zero noise,
 *    allowing players to slip past even 3 blocks away.
 *  - Detects sprinting (22 blocks), swinging/breaking (20 blocks), jumping (18 blocks), and normal walking (11 blocks).
 *  - Freshly dropped/thrown items act as sound decoys, drawing the Clump away to investigate the clatter.
 */
public class Clump extends Monster {
	private static final int GRAB_HIT_AT = 9, GRAB_TICKS = 16;
	private static final byte EVENT_GRAB = 4;
	private static final EntityDataAccessor<Boolean> DATA_CHARGING =
			SynchedEntityData.defineId(Clump.class, EntityDataSerializers.BOOLEAN);

	public final AnimationState grabAnimation = new AnimationState();

	private int grabTimer = -1, grabCooldown;
	private Vec3 soundPos;
	private int alertTicks;

	public Clump(EntityType<? extends Clump> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 32)
				.add(Attributes.ATTACK_DAMAGE, 7)
				.add(Attributes.MOVEMENT_SPEED, 0.28)
				.add(Attributes.FOLLOW_RANGE, 26)
				.add(Attributes.ARMOR, 2);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_CHARGING, false);
	}

	public boolean isCharging() {
		return entityData.get(DATA_CHARGING);
	}

	void setCharging(boolean charging) {
		if (isCharging() != charging) {
			entityData.set(DATA_CHARGING, charging);
			if (charging) playSound(SoundEvents.SPIDER_AMBIENT, 1.0F, 0.55F);
		}
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new AcousticHuntGoal(this));
		goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.65));
	}

	void hearNoise(Vec3 pos, int durationTicks) {
		soundPos = pos;
		alertTicks = Math.max(alertTicks, durationTicks);
	}

	void startGrab() {
		grabTimer = 0;
		level().broadcastEntityEvent(this, EVENT_GRAB);
		playSound(SoundEvents.SPIDER_HURT, 1.0F, 0.5F);
	}

	boolean canGrab() {
		return grabTimer < 0 && tickCount >= grabCooldown;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (alertTicks > 0) {
			alertTicks--;
			if (alertTicks == 0) soundPos = null;
		}
		if (grabTimer >= 0) {
			grabTimer++;
			if (grabTimer == GRAB_HIT_AT) {
				AABB reach = getBoundingBox().inflate(1.6, 0.6, 1.6);
				for (Player p : level.getEntitiesOfClass(Player.class, reach)) {
					if (p.isCreative() || p.isSpectator() || !p.isAlive()) continue;
					if (doHurtTarget(level, p)) {
						p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 1));
					}
				}
			}
			if (grabTimer >= GRAB_TICKS) {
				grabTimer = -1;
				grabCooldown = tickCount + 14;
			}
		}
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == EVENT_GRAB) {
			grabAnimation.start(tickCount);
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && source.getEntity() instanceof LivingEntity attacker) {
			hearNoise(attacker.position(), 120);
		}
		return hurt;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.SPIDER_STEP;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.SPIDER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SPIDER_DEATH;
	}

	/** Completely blind AI: scans for player footsteps/swings/jumps and dropped item decoys. */
	private static final class AcousticHuntGoal extends Goal {
		private final Clump clump;

		AcousticHuntGoal(Clump clump) {
			this.clump = clump;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			scanForSounds();
			return clump.soundPos != null && clump.alertTicks > 0;
		}

		@Override
		public boolean canContinueToUse() {
			scanForSounds();
			return clump.soundPos != null && clump.alertTicks > 0;
		}

		@Override
		public void stop() {
			clump.setCharging(false);
			clump.getNavigation().stop();
		}

		@Override
		public void tick() {
			Vec3 targetPos = clump.soundPos;
			if (targetPos == null) return;

			clump.setCharging(true);
			clump.getLookControl().setLookAt(targetPos.x, targetPos.y + 0.5, targetPos.z, 45, 30);

			// If any survival player is right within arm's reach, grab them!
			AABB touch = clump.getBoundingBox().inflate(1.5, 0.5, 1.5);
			for (Player p : clump.level().getEntitiesOfClass(Player.class, touch)) {
				if (p.isCreative() || p.isSpectator() || !p.isAlive()) continue;
				if (clump.canGrab()) {
					clump.getNavigation().stop();
					clump.startGrab();
					return;
				}
			}

			double distSqr = clump.position().distanceToSqr(targetPos);
			if (distSqr <= 2.25) {
				clump.getNavigation().stop();
				// Briefly grope around the sound spot before losing interest
				clump.alertTicks = Math.min(clump.alertTicks, 20);
			} else if (clump.grabTimer < 0) {
				clump.getNavigation().moveTo(targetPos.x, targetPos.y, targetPos.z, 1.52);
			}
		}

		private void scanForSounds() {
			Level level = clump.level();
			// 1. Freshly dropped/thrown item decoys (within 18 blocks)
			AABB itemBox = clump.getBoundingBox().inflate(18.0, 5.0, 18.0);
			for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, itemBox)) {
				if (item.tickCount < 18) {
					clump.hearNoise(item.position(), 85);
					return;
				}
			}
			// 2. Noisy players (silent crouching/standing is completely undetected!)
			AABB hearBox = clump.getBoundingBox().inflate(22.0, 6.0, 22.0);
			for (Player p : level.getEntitiesOfClass(Player.class, hearBox)) {
				if (p.isCreative() || p.isSpectator() || !p.isAlive()) continue;
				double dist = clump.distanceTo(p);
				// Bumping right into the Clump (<1.6 blocks) always alerts it
				if (dist < 1.6) {
					clump.hearNoise(p.position(), 60);
					return;
				}
				double horizSpeed = p.getDeltaMovement().horizontalDistance();
				boolean loudSprint = p.isSprinting() || horizSpeed > 0.14;
				boolean jumping = !p.onGround() && Math.abs(p.getDeltaMovement().y) > 0.12;
				boolean swinging = p.swinging;
				boolean normalWalk = !p.isShiftKeyDown() && horizSpeed > 0.04;

				if ((loudSprint && dist <= 22.0)
						|| (swinging && dist <= 20.0)
						|| (jumping && dist <= 18.0)
						|| (normalWalk && dist <= 11.0)) {
					clump.hearNoise(p.position(), 80);
					return;
				}
			}
		}
	}
}
