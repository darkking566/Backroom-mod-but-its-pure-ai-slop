package dev.noclip.backrooms.monsters.arms;

import dev.noclip.backrooms.monsters.Tuning;
import dev.noclip.backrooms.monsters.smiler.Smiler;
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
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Arms: five elongated pale arms dangling from a dark ceiling rift during blackouts.
 *
 * Tactical counterweight to the Smiler:
 *  - Walking underneath in the dark triggers a downward snatch that deals damage and pins you (Slowness III).
 *  - Shining or holding a light source (flashlight, torch, lantern) toward them causes them to shriek, recoil
 *    up into the ceiling rift, and vanish!
 */
public class Arms extends Monster {
	public static final String BLACKOUT_TAG = "noclip_backrooms_arms";
	private static final int SNATCH_HIT_AT = 9, SNATCH_TICKS = 17;
	private static final int RECOIL_TICKS = 15;
	private static final byte EVENT_SNATCH = 4, EVENT_RECOIL = 5;

	private static final EntityDataAccessor<Boolean> DATA_REACHING =
			SynchedEntityData.defineId(Arms.class, EntityDataSerializers.BOOLEAN);

	public final AnimationState snatchAnimation = new AnimationState();
	public final AnimationState recoilAnimation = new AnimationState();

	private int snatchTimer = -1, snatchCooldown;
	private int recoilTimer = -1, stunTicks;

	public Arms(EntityType<? extends Arms> type, Level level) {
		super(type, level);
		setNoGravity(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, Tuning.hp(22))
				.add(Attributes.ATTACK_DAMAGE, 5)
				.add(Attributes.MOVEMENT_SPEED, 0.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_REACHING, false);
	}

	public boolean isReaching() {
		return entityData.get(DATA_REACHING);
	}

	@Override
	public void travel(Vec3 movementInput) {
		setDeltaMovement(Vec3.ZERO);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		setDeltaMovement(Vec3.ZERO);

		if (recoilTimer >= 0) {
			recoilTimer++;
			if (recoilTimer >= RECOIL_TICKS) {
				recoilTimer = -1;
				level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1.4, getZ(), 12, 0.4, 0.3, 0.4, 0.02);
				if (entityTags().contains(BLACKOUT_TAG)) {
					discard();
					return;
				}
				stunTicks = 100; // Spawn-egg Arms retract briefly then re-emerge after 5s for testing
			}
			return;
		}
		if (stunTicks > 0) {
			stunTicks--;
			entityData.set(DATA_REACHING, false);
			return;
		}

		// 1. Check if any nearby player is shining/holding a light source toward the Arms -> RECOIL!
		AABB lightBox = getBoundingBox().inflate(11.0, 5.0, 11.0);
		for (Player p : level.getEntitiesOfClass(Player.class, lightBox)) {
			if (p.isSpectator() || !p.isAlive()) continue;
			boolean hasLight = isHoldingLight(p);
			if (!hasLight) continue;
			Vec3 toArms = getEyePosition().subtract(p.getEyePosition()).normalize();
			double dot = p.getLookAngle().normalize().dot(toArms);
			if (dot > 0.45 || distanceToSqr(p) <= 25.0) {
				recoilTimer = 0;
				entityData.set(DATA_REACHING, false);
				level.broadcastEntityEvent(this, EVENT_RECOIL);
				playSound(SoundEvents.PHANTOM_HURT, 1.0F, 1.45F);
				return;
			}
		}

		// 2. Overhead scratching warning sound when a player is within 10 blocks
		if (tickCount % 40 == 0 && level.getNearestPlayer(this, 10.0) != null) {
			playSound(SoundEvents.SILVERFISH_STEP, 0.65F, 0.55F);
		}

		// 3. Check for unlit prey passing underneath to reach & snatch!
		AABB grabZone = new AABB(getX() - 3.0, getY() - 3.5, getZ() - 3.0, getX() + 3.0, getY() + 1.5, getZ() + 3.0);
		Player prey = null;
		for (Player p : level.getEntitiesOfClass(Player.class, grabZone)) {
			if (!p.isCreative() && !p.isSpectator() && p.isAlive()) {
				prey = p;
				break;
			}
		}
		entityData.set(DATA_REACHING, prey != null);

		if (snatchTimer >= 0) {
			snatchTimer++;
			if (snatchTimer == SNATCH_HIT_AT && prey != null) {
				if (doHurtTarget(level, prey)) {
					prey.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 70, 2));
					playSound(SoundEvents.SPIDER_HURT, 1.0F, 0.45F);
				}
			}
			if (snatchTimer >= SNATCH_TICKS) {
				snatchTimer = -1;
				snatchCooldown = tickCount + 22;
			}
		} else if (prey != null && tickCount >= snatchCooldown) {
			snatchTimer = 0;
			level.broadcastEntityEvent(this, EVENT_SNATCH);
			playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.9F, 0.5F);
		}
	}

	private static boolean isHoldingLight(Player player) {
		return Smiler.isLightItem(player.getMainHandItem()) || Smiler.isLightItem(player.getOffhandItem());
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == EVENT_SNATCH) {
			snatchAnimation.start(tickCount);
		} else if (id == EVENT_RECOIL) {
			recoilAnimation.start(tickCount);
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.HUSK_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.PHANTOM_DEATH;
	}
}
