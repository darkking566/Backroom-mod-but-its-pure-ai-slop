package dev.noclip.backrooms.monsters.smiler;

import dev.noclip.backrooms.monsters.Tuning;
import dev.noclip.backrooms.monsters.Retreat;
import dev.noclip.backrooms.monsters.Monsters;
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
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Smiler: a hovering dark-smoke predator with glowing eyes and an ear-to-ear jagged grin.
 *
 * Three-stage reactive AI:
 *  - STALK: when prey stays dark and quiet (not holding light, not sprinting/jumping), it circles silently at
 *    7-11 blocks distance and watches without rushing.
 *  - ENRAGED: when prey holds a light source, sprints, jumps, or provokes it, its jaw unhinges and it charges
 *    at high speed to execute a wind-up bite + Darkness ambush.
 *  - LURED: if a glowing item is dropped nearby while the player is dark and quiet, it drifts toward the light
 *    source first, giving the player a tactical window to escape.
 */
public class Smiler extends Monster {
	public static final int BITE_AT = 11, BITE_TICKS = 20;
	private static final byte BITE_EVENT = 4;
	private static final EntityDataAccessor<Boolean> DATA_ENRAGED = SynchedEntityData.defineId(Smiler.class, EntityDataSerializers.BOOLEAN);

	public final AnimationState biteAnimation = new AnimationState();
	private int bite = -1, biteCooldown;
	private LivingEntity biteTarget;
	private int rageMemory;

	public Smiler(EntityType<? extends Smiler> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, Tuning.hp(28))
				.add(Attributes.ATTACK_DAMAGE, 6)
				.add(Attributes.MOVEMENT_SPEED, 0.27)
				.add(Attributes.FOLLOW_RANGE, 36);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ENRAGED, false);
	}

	public boolean isEnraged() {
		return entityData.get(DATA_ENRAGED);
	}

	void setEnraged(boolean enraged) {
		if (isEnraged() != enraged) {
			entityData.set(DATA_ENRAGED, enraged);
			if (enraged) playSound(SoundEvents.ENDERMAN_SCREAM, 0.7F, 0.55F);
		}
	}

	@Override
	protected void registerGoals() {
		Retreat.add(this, goalSelector, targetSelector);
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new SmilerHuntGoal(this));
		goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.65));
		goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16));
		goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	void startBite(LivingEntity target) {
		biteTarget = target;
		bite = 0;
		level().broadcastEntityEvent(this, BITE_EVENT);
		playSound(SoundEvents.PHANTOM_BITE, 1.1F, 0.65F);
	}

	boolean canBite() {
		return bite < 0 && tickCount >= biteCooldown;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (level().isClientSide() && random.nextInt(4) == 0) {
			level().addParticle(ParticleTypes.SMOKE,
					getX() + (random.nextDouble() - 0.5) * 0.6,
					getY() + 0.3 + random.nextDouble() * 1.1,
					getZ() + (random.nextDouble() - 0.5) * 0.6,
					0, 0.015, 0);
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (rageMemory > 0) rageMemory--;
		if (bite < 0) return;
		bite++;
		if (biteTarget != null && biteTarget.isAlive()) {
			getLookControl().setLookAt(biteTarget, 45, 45);
			if (bite == BITE_AT && distanceToSqr(biteTarget) <= 9.0) {
				if (doHurtTarget(level, biteTarget)) {
					biteTarget.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0));
				}
			}
		}
		if (bite >= BITE_TICKS) {
			bite = -1;
			biteTarget = null;
			biteCooldown = tickCount + 18;
		}
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == BITE_EVENT) {
			biteAnimation.start(tickCount);
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt) rageMemory = 120; // Provoking it enrages it for 6 seconds!
		return hurt;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.AMBIENT_CAVE.value();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.PHANTOM_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.PHANTOM_DEATH;
	}

	/** True when the player is holding a light source, sprinting, or jumping. */
	static boolean isLoudOrLit(Player player) {
		if (player.isSprinting() || (!player.onGround() && player.getDeltaMovement().y > 0.1)) return true;
		return isLightItem(player.getMainHandItem()) || isLightItem(player.getOffhandItem());
	}

	public static boolean isLightItem(ItemStack stack) {
		if (stack.isEmpty()) return false;
		String id = stack.getItem().toString();
		if (id.contains("flashlight") || id.contains("torch") || id.contains("lantern") || id.contains("glow")) return true;
		if (stack.getItem() instanceof BlockItem blockItem) {
			return blockItem.getBlock().defaultBlockState().getLightEmission() > 0;
		}
		return false;
	}

	/** Three-stage Smiler AI: LURED by dropped light -> ENRAGED rush on light/noise -> STALK circling in darkness. */
	private static final class SmilerHuntGoal extends Goal {
		private final Smiler smiler;
		private int strafeDir = 1;
		private int strafeTimer;

		SmilerHuntGoal(Smiler smiler) {
			this.smiler = smiler;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = smiler.getTarget();
			return target != null && target.isAlive();
		}

		@Override
		public void stop() {
			smiler.setEnraged(false);
			smiler.getNavigation().stop();
		}

		@Override
		public void tick() {
			LivingEntity target = smiler.getTarget();
			if (target == null) return;

			boolean triggered = smiler.rageMemory > 0 || (target instanceof Player p && isLoudOrLit(p));
			double distSqr = smiler.distanceToSqr(target);

			// 1. LURED mode: if prey is quiet & dark, check for a dropped glowing item within 12 blocks!
			if (!triggered && distSqr > 9.0) {
				ItemEntity lure = findLightLure();
				if (lure != null) {
					smiler.setEnraged(false);
					smiler.getLookControl().setLookAt(lure, 30, 30);
					if (smiler.distanceToSqr(lure) > 4.0) {
						smiler.getNavigation().moveTo(lure, 0.85);
					} else {
						smiler.getNavigation().stop();
					}
					return;
				}
			}

			smiler.getLookControl().setLookAt(target, 35, 35);

			// 2. ENRAGED mode: charge fast and execute wind-up bite!
			if (triggered || distSqr < 7.5) {
				smiler.setEnraged(true);
				if (distSqr <= 5.5 && smiler.canBite()) {
					smiler.getNavigation().stop();
					smiler.startBite(target);
				} else if (smiler.bite < 0) {
					smiler.getNavigation().moveTo(target, 1.38);
				}
				return;
			}

			// 3. STALK mode: quiet prey -> hover & circle silently at 7-11 blocks distance!
			smiler.setEnraged(false);
			if (--strafeTimer <= 0) {
				strafeTimer = 35 + smiler.getRandom().nextInt(35);
				strafeDir = -strafeDir;
			}
			double dist = Math.sqrt(distSqr);
			if (dist > 11.0) {
				smiler.getNavigation().moveTo(target, 0.72);
			} else if (dist < 6.5) {
				// Back off slightly while keeping eyes locked on prey
				Vec3 away = smiler.position().subtract(target.position()).normalize();
				smiler.getNavigation().moveTo(smiler.getX() + away.x * 3.0, smiler.getY(), smiler.getZ() + away.z * 3.0, 0.6);
			} else {
				// Circle around the player ominously
				Vec3 toTarget = target.position().subtract(smiler.position()).normalize();
				Vec3 side = new Vec3(-toTarget.z * strafeDir, 0, toTarget.x * strafeDir);
				smiler.getNavigation().moveTo(smiler.getX() + side.x * 3.5, smiler.getY(), smiler.getZ() + side.z * 3.5, 0.62);
			}
		}

		private ItemEntity findLightLure() {
			AABB box = smiler.getBoundingBox().inflate(12.0, 4.0, 12.0);
			for (ItemEntity item : smiler.level().getEntitiesOfClass(ItemEntity.class, box)) {
				if (isLightItem(item.getItem())) return item;
			}
			return null;
		}
	}
}
