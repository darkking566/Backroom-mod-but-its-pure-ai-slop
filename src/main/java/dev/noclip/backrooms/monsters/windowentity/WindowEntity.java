package dev.noclip.backrooms.monsters.windowentity;

import dev.noclip.backrooms.monsters.Tuning;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Entity 2 ("Window" / 窗戶): a gaunt, rib-ridged pale-palmed silhouette that hovers right outside
 * un-blacked trap windows in Level 4's misty rain canyons, beckoning wanderers closer before lunging
 * through the glass. When standalone (spawned via egg), stalks and lunges at nearby players.
 */
public class WindowEntity extends Monster {
	public static final String LEVEL4_WINDOW_TAG = "noclip_backrooms_level4_window_entity";
	private static final byte EVENT_LUNGE = 4, EVENT_BANISH = 5;

	private static final EntityDataAccessor<Boolean> DATA_BECKONING =
			SynchedEntityData.defineId(WindowEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_WINDOW_BOUND =
			SynchedEntityData.defineId(WindowEntity.class, EntityDataSerializers.BOOLEAN);

	public final AnimationState lungeAnimation = new AnimationState();
	public final AnimationState banishAnimation = new AnimationState();

	private BlockPos boundWindowPos;
	private Direction boundInwardDir;
	private int attackCooldown;
	private int banishTimer = -1;

	public WindowEntity(EntityType<? extends WindowEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, Tuning.hp(26))
				.add(Attributes.ATTACK_DAMAGE, 6)
				.add(Attributes.MOVEMENT_SPEED, 0.22)
				.add(Attributes.FOLLOW_RANGE, 20)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_BECKONING, false);
		builder.define(DATA_WINDOW_BOUND, false);
	}

	public boolean isBeckoning() {
		return entityData.get(DATA_BECKONING);
	}

	public boolean isWindowBound() {
		return entityData.get(DATA_WINDOW_BOUND);
	}

	public void bindToWindow(BlockPos windowPos, Direction inwardDir) {
		this.boundWindowPos = windowPos.immutable();
		this.boundInwardDir = inwardDir;
		entityData.set(DATA_WINDOW_BOUND, true);
		setNoGravity(true);
		float yaw = inwardDir.toYRot();
		setYRot(yaw);
		setYHeadRot(yaw);
		yBodyRot = yaw;
	}

	public void triggerLunge() {
		if (!level().isClientSide()) {
			level().broadcastEntityEvent(this, EVENT_LUNGE);
		}
	}

	public void triggerBanish() {
		if (banishTimer >= 0) return;
		banishTimer = 0;
		if (!level().isClientSide()) {
			level().broadcastEntityEvent(this, EVENT_BANISH);
			playSound(SoundEvents.GLASS_BREAK, 0.65F, 0.55F);
		}
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 18.0F));
		goalSelector.addGoal(6, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	public void travel(Vec3 movementInput) {
		if (isWindowBound()) {
			setDeltaMovement(Vec3.ZERO);
			return;
		}
		super.travel(movementInput);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (attackCooldown > 0) attackCooldown--;

		if (banishTimer >= 0) {
			banishTimer++;
			if (banishTimer >= 16) {
				level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1.1, getZ(), 14, 0.35, 0.55, 0.35, 0.02);
				discard();
			}
			return;
		}

		Player nearest = level.getNearestPlayer(this, isWindowBound() ? 36.0 : 18.0);
		if (isWindowBound()) {
			setDeltaMovement(Vec3.ZERO);
			if (nearest == null) {
				discard();
				return;
			}
			if (boundWindowPos != null && level.isLoaded(boundWindowPos)) {
				BlockState st = level.getBlockState(boundWindowPos);
				if (st.isAir() || st.getBlock().getDescriptionId().endsWith("office_blacked_window")) {
					triggerBanish();
					return;
				}
			}
			double dist = distanceTo(nearest);
			entityData.set(DATA_BECKONING, dist <= 16.0);
			facePosition(nearest.getX(), nearest.getZ());
			return;
		}

		boolean beckoning = nearest != null && !nearest.isCreative() && !nearest.isSpectator() && distanceTo(nearest) <= 14.0;
		entityData.set(DATA_BECKONING, beckoning);
		if (nearest != null && !nearest.isCreative() && !nearest.isSpectator()) {
			double dist = distanceTo(nearest);
			facePosition(nearest.getX(), nearest.getZ());
			if (dist > 2.4 && dist < 16.0) {
				getNavigation().moveTo(nearest, 0.95);
			}
			if (dist <= 2.8 && attackCooldown <= 0) {
				attackCooldown = 36;
				triggerLunge();
				nearest.hurtServer(level, level.damageSources().mobAttack(this),
						(float) getAttributeValue(Attributes.ATTACK_DAMAGE));
				nearest.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, true, false, true));
				playSound(SoundEvents.PLAYER_ATTACK_STRONG, 0.85F, 0.55F);
			}
		}
	}

	private void facePosition(double tx, double tz) {
		float yaw = (float) (Mth.atan2(tz - getZ(), tx - getX()) * Mth.RAD_TO_DEG) - 90.0F;
		setYRot(yaw);
		setYHeadRot(yaw);
		yBodyRot = yaw;
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == EVENT_LUNGE) {
			lungeAnimation.start(tickCount);
		} else if (id == EVENT_BANISH) {
			banishAnimation.start(tickCount);
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.WARDEN_NEARBY_CLOSER;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.GLASS_HIT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.GLASS_BREAK;
	}
}
