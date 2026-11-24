package dev.noclip.backrooms.monsters.beastlevel5;

import java.util.EnumSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
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
import org.jetbrains.annotations.Nullable;

/**
 * Entity 18 - The Beast of Level 5 / Terror Hotel Gentleman (第五層野獸紳士):
 * Two-phase boss in Victorian evening dress with an abyssal cephalopod head.
 * Below 50% health, unfurls hidden wings and back tentacles (phase2_unfurl) with a boss bar and Darkness aura.
 */
public class BeastOfLevel5 extends Monster {
	private static final EntityDataAccessor<Boolean> DATA_PHASE2 =
			SynchedEntityData.defineId(BeastOfLevel5.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_LASHING =
			SynchedEntityData.defineId(BeastOfLevel5.class, EntityDataSerializers.BOOLEAN);
	private static final byte EVENT_UNFURL = 4;

	private final ServerBossEvent bossEvent;

	public final AnimationState unfurlAnimation = new AnimationState();
	private int unfurlFreezeTicks;

	public BeastOfLevel5(EntityType<? extends BeastOfLevel5> type, Level level) {
		super(type, level);
		this.bossEvent = new ServerBossEvent(
				this.getUUID(),
				Component.translatable("entity.noclip_backrooms.beast_level5"),
				BossEvent.BossBarColor.PURPLE,
				BossEvent.BossBarOverlay.NOTCHED_10);
		xpReward = 50;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 180)
				.add(Attributes.ATTACK_DAMAGE, 10)
				.add(Attributes.MOVEMENT_SPEED, 0.27)
				.add(Attributes.FOLLOW_RANGE, 40)
				.add(Attributes.ARMOR, 6)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.75);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_PHASE2, false);
		builder.define(DATA_LASHING, false);
	}

	public boolean isPhase2() {
		return entityData.get(DATA_PHASE2);
	}

	public boolean isLashing() {
		return entityData.get(DATA_LASHING);
	}

	void setLashing(boolean lashing) {
		entityData.set(DATA_LASHING, lashing);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new BossStalkGoal(this));
		goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
		goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16));
		goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		bossEvent.setProgress(getHealth() / getMaxHealth());

		if (!isPhase2() && getHealth() <= getMaxHealth() * 0.5F) {
			entityData.set(DATA_PHASE2, true);
			unfurlFreezeTicks = 28;
			level.broadcastEntityEvent(this, EVENT_UNFURL);
			playSound(SoundEvents.WARDEN_ROAR, 1.3F, 0.75F);
			level.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 1.5, getZ(), 32, 0.6, 0.8, 0.6, 0.06);
			for (Player p : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(14))) {
				if (!p.isCreative() && !p.isSpectator()) {
					p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0));
				}
			}
		}

		if (unfurlFreezeTicks > 0) {
			unfurlFreezeTicks--;
			getNavigation().stop();
		}
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		bossEvent.addPlayer(player);
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		bossEvent.removePlayer(player);
	}

	@Override
	public void setCustomName(@Nullable Component name) {
		super.setCustomName(name);
		bossEvent.setName(getDisplayName());
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == EVENT_UNFURL) {
			unfurlAnimation.start(tickCount);
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ELDER_GUARDIAN_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ELDER_GUARDIAN_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ELDER_GUARDIAN_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.72F;
	}

	private static final class BossStalkGoal extends Goal {
		private final BeastOfLevel5 mob;
		private int attackCooldown;

		BossStalkGoal(BeastOfLevel5 mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = mob.getTarget();
			return mob.unfurlFreezeTicks <= 0 && target != null && target.isAlive();
		}

		@Override
		public void stop() {
			mob.setLashing(false);
			mob.getNavigation().stop();
		}

		@Override
		public void tick() {
			LivingEntity target = mob.getTarget();
			if (target == null || !(mob.level() instanceof ServerLevel serverLevel)) return;
			if (attackCooldown > 0) attackCooldown--;

			mob.getLookControl().setLookAt(target, 40, 40);
			double distSqr = mob.distanceToSqr(target);
			double reachSqr = mob.isPhase2() ? 10.2 : 6.8;

			if (distSqr <= reachSqr) {
				mob.setLashing(true);
				mob.getNavigation().stop();
				if (attackCooldown <= 0) {
					attackCooldown = mob.isPhase2() ? 16 : 22;
					if (mob.doHurtTarget(serverLevel, target) && mob.isPhase2()) {
						target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0));
					}
				}
			} else {
				mob.setLashing(false);
				mob.getNavigation().moveTo(target, mob.isPhase2() ? 1.34 : 1.06);
			}
		}
	}
}
