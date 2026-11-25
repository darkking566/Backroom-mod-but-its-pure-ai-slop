package dev.noclip.backrooms.monsters.wretch;

import dev.noclip.backrooms.monsters.Tuning;
import dev.noclip.backrooms.monsters.Retreat;
import dev.noclip.backrooms.level1.Level1Items;
import java.util.EnumSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Wretch (悲屍): a dehydrated, scorched-crimson mutated wanderer with exposed ribs and crooked claws.
 *
 * Distinct Psychological AI:
 *  - SOBBING (0): Hunches in dim corners trembling and sobbing softly. Occasionally hobbles aimlessly.
 *  - FRENZY (1): Triggered when a wanderer enters sight (< 12 blocks), sprints/makes noise nearby (< 18 blocks),
 *    or strikes it. Erupts into an erratic, high-speed berserk sprint and feral double-claw maul attacks.
 *  - RECOIL (2): Almond Water interaction! Holding Almond Water within 7 blocks briefly deters it, and
 *    right-clicking the Wretch with Almond Water splashes it (consuming 1 bottle -> empty glass bottle),
 *    forcing a violent 6-second recoil/convulsion that halts its frenzy so the player can escape.
 */
public class Wretch extends Monster {
	public static final int STATE_SOBBING = 0;
	public static final int STATE_FRENZY = 1;
	public static final int STATE_RECOIL = 2;

	private static final EntityDataAccessor<Integer> DATA_STATE =
			SynchedEntityData.defineId(Wretch.class, EntityDataSerializers.INT);

	private static final int MAUL_HIT_AT = 5, MAUL_TICKS = 11;
	private static final byte EVENT_MAUL = 4, EVENT_RECOIL = 5;

	public final AnimationState maulAnimation = new AnimationState();
	public final AnimationState recoilAnimation = new AnimationState();

	private int maulTimer = -1, maulCooldown;
	private LivingEntity maulTarget;
	private int recoilTicks;
	private int holdDeterCooldown;
	private int frenzyCalmTicks;

	public Wretch(EntityType<? extends Wretch> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, Tuning.hp(28))
				.add(Attributes.ATTACK_DAMAGE, 6)
				.add(Attributes.MOVEMENT_SPEED, 0.25)
				.add(Attributes.FOLLOW_RANGE, 28)
				.add(Attributes.ARMOR, 1);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_STATE, STATE_SOBBING);
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
		goalSelector.addGoal(1, new AlmondRecoilGoal(this));
		goalSelector.addGoal(2, new FrenzyHuntGoal(this));
		goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.55));
		goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
	}

	void triggerRecoil(int ticks, boolean splash) {
		recoilTicks = Math.max(recoilTicks, ticks);
		maulTimer = -1;
		maulTarget = null;
		setAiState(STATE_RECOIL);
		getNavigation().stop();
		level().broadcastEntityEvent(this, EVENT_RECOIL);
		playSound(SoundEvents.HUSK_HURT, 1.1F, splash ? 1.45F : 1.2F);
		if (level() instanceof ServerLevel sl) {
			sl.sendParticles(splash ? ParticleTypes.SPLASH : ParticleTypes.ASH,
					getX(), getY() + 1.3, getZ(), splash ? 24 : 10, 0.4, 0.5, 0.4, 0.08);
		}
	}

	void startMaul(LivingEntity target) {
		maulTarget = target;
		maulTimer = 0;
		level().broadcastEntityEvent(this, EVENT_MAUL);
		playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.9F, 0.65F);
	}

	boolean canMaul() {
		return maulTimer < 0 && tickCount >= maulCooldown && recoilTicks <= 0;
	}

	private static boolean holdingAlmondWater(Player p) {
		return p.getMainHandItem().is(Level1Items.ALMOND_WATER)
				|| p.getOffhandItem().is(Level1Items.ALMOND_WATER);
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (stack.is(Level1Items.ALMOND_WATER)) {
			if (!level().isClientSide()) {
				if (!player.getAbilities().instabuild) {
					stack.shrink(1);
					ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
					if (!player.getInventory().add(bottle)) {
						player.drop(bottle, false);
					}
				}
				playSound(SoundEvents.SPLASH_POTION_BREAK, 0.9F, 1.15F);
				// Push Wretch slightly back and force a 6-second convulsive recoil
				Vec3 push = position().subtract(player.position()).normalize();
				setDeltaMovement(push.x * 0.45, 0.22, push.z * 0.45);
				triggerRecoil(120, true);
				setTarget(null);
			}
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (holdDeterCooldown > 0) holdDeterCooldown--;

		// Handle active recoil countdown
		if (recoilTicks > 0) {
			recoilTicks--;
			setAiState(STATE_RECOIL);
			if (recoilTicks % 24 == 0) {
				level().broadcastEntityEvent(this, EVENT_RECOIL);
			}
			if (recoilTicks == 0) {
				setAiState(getTarget() != null ? STATE_FRENZY : STATE_SOBBING);
			}
			return;
		}

		// Detect nearby players to shift from SOBBING -> FRENZY, or deter if holding Almond Water
		Player nearest = level.getNearestPlayer(this, 18.0);
		if (nearest != null && !nearest.isCreative() && !nearest.isSpectator()) {
			double distSqr = distanceToSqr(nearest);
			// Holding Almond Water at close range briefly makes the Wretch flinch back
			if (distSqr < 49.0 && holdingAlmondWater(nearest) && holdDeterCooldown <= 0 && hasLineOfSight(nearest)) {
				holdDeterCooldown = 110;
				Vec3 away = position().subtract(nearest.position()).normalize();
				setDeltaMovement(away.x * 0.3, 0.14, away.z * 0.3);
				triggerRecoil(42, false);
				return;
			}
			// Trigger frenzy on sight (<12 blocks), or loud movement (<18 blocks)
			boolean loud = nearest.isSprinting() || !nearest.onGround();
			if ((distSqr < 144.0 && hasLineOfSight(nearest)) || (loud && distSqr < 324.0)) {
				if (getTarget() != nearest) {
					setTarget(nearest);
					if (aiState() != STATE_FRENZY) {
						playSound(SoundEvents.HUSK_AMBIENT, 1.2F, 1.55F);
					}
				}
				frenzyCalmTicks = 160;
			}
		}

		LivingEntity target = getTarget();
		if (target != null && target.isAlive() && distanceToSqr(target) < 784.0) {
			setAiState(STATE_FRENZY);
			if (tickCount % 16 == 0) {
				level.sendParticles(ParticleTypes.ASH, getX(), getY() + 1.1, getZ(), 2, 0.25, 0.35, 0.25, 0.01);
			}
		} else {
			if (frenzyCalmTicks > 0) {
				frenzyCalmTicks--;
			} else {
				setTarget(null);
				setAiState(STATE_SOBBING);
			}
		}

		// Eerie sobbing sounds while hunched in corners
		if (aiState() == STATE_SOBBING && tickCount % 75 == 0 && random.nextInt(3) == 0) {
			playSound(SoundEvents.GHAST_AMBIENT, 0.32F, 0.55F + random.nextFloat() * 0.15F);
		}

		// Execute maul hit frame
		if (maulTimer >= 0) {
			maulTimer++;
			if (maulTarget != null && maulTarget.isAlive()) {
				getLookControl().setLookAt(maulTarget, 55, 55);
				if (maulTimer == MAUL_HIT_AT && distanceToSqr(maulTarget) <= 7.8) {
					if (doHurtTarget(level, maulTarget)) {
						maulTarget.addEffect(new MobEffectInstance(MobEffects.HUNGER, 100, 1));
						maulTarget.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
					}
				}
			}
			if (maulTimer >= MAUL_TICKS) {
				maulTimer = -1;
				maulTarget = null;
				maulCooldown = tickCount + 10;
			}
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && recoilTicks <= 0 && source.getEntity() instanceof LivingEntity attacker) {
			if (!(attacker instanceof Player p) || (!p.isCreative() && !p.isSpectator())) {
				setTarget(attacker);
				setAiState(STATE_FRENZY);
				frenzyCalmTicks = 200;
			}
		}
		return hurt;
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == EVENT_MAUL) {
			maulAnimation.start(tickCount);
		} else if (id == EVENT_RECOIL) {
			recoilAnimation.start(tickCount);
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return aiState() == STATE_FRENZY ? SoundEvents.HUSK_AMBIENT : SoundEvents.SOUL_ESCAPE.value();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.HUSK_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.HUSK_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return aiState() == STATE_FRENZY ? 1.35F : 0.65F;
	}

	/** Freezes and backs away slowly while recoiling from Almond Water. */
	private static final class AlmondRecoilGoal extends Goal {
		private final Wretch mob;

		AlmondRecoilGoal(Wretch mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return mob.recoilTicks > 0;
		}

		@Override
		public void tick() {
			mob.getNavigation().stop();
		}
	}

	/** Erratic, frantic high-speed pursuit and double-claw mauling. */
	private static final class FrenzyHuntGoal extends Goal {
		private final Wretch mob;
		private int repath;

		FrenzyHuntGoal(Wretch mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = mob.getTarget();
			return mob.recoilTicks <= 0 && target != null && target.isAlive();
		}

		@Override
		public void stop() {
			mob.getNavigation().stop();
		}

		@Override
		public void tick() {
			LivingEntity target = mob.getTarget();
			if (target == null) return;
			mob.getLookControl().setLookAt(target, 50, 45);
			double distSqr = mob.distanceToSqr(target);
			if (distSqr <= 5.8 && mob.canMaul()) {
				mob.getNavigation().stop();
				mob.startMaul(target);
				return;
			}
			if (--repath <= 0) {
				repath = 6;
				mob.getNavigation().moveTo(target, 1.48);
			}
		}
	}
}
