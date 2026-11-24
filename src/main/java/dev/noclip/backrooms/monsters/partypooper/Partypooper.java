package dev.noclip.backrooms.monsters.partypooper;

import dev.noclip.backrooms.level1.Level1Items;
import dev.noclip.backrooms.monsters.partygoer.Partygoer;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Entity 68 - Partypooper (派對掃興客):
 * Benevolent survivor in dark streetwear and a cobalt-blue sad mask =( who beckons wanderers to safety,
 * trades supplies for Almond Water, and actively hunts Partygoers and nearby monsters.
 */
public class Partypooper extends PathfinderMob {
	public static final int STATE_IDLE = 0;
	public static final int STATE_BECKON = 1;
	public static final int STATE_COMBAT = 2;
	public static final int STATE_GIFT = 3;

	private static final EntityDataAccessor<Integer> DATA_STATE =
			SynchedEntityData.defineId(Partypooper.class, EntityDataSerializers.INT);

	private int gestureTicks;

	public Partypooper(EntityType<? extends Partypooper> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 36)
				.add(Attributes.ATTACK_DAMAGE, 6.5)
				.add(Attributes.MOVEMENT_SPEED, 0.28)
				.add(Attributes.FOLLOW_RANGE, 28)
				.add(Attributes.ARMOR, 4);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_STATE, STATE_IDLE);
	}

	public int aiState() {
		return entityData.get(DATA_STATE);
	}

	private void setAiState(int state) {
		entityData.set(DATA_STATE, state);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.28, true));
		goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.75));
		goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10));
		goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this, Partypooper.class).setAlertOthers());
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Partygoer.class, true));
		targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false,
				(entity, level) -> entity instanceof Monster));
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (getTarget() == player) return super.mobInteract(player, hand);
		ItemStack stack = player.getItemInHand(hand);
		if (stack.is(Level1Items.ALLOY) || stack.is(Level1Items.BANDAGE)) {
			if (!level().isClientSide()) {
				if (!player.getAbilities().instabuild) stack.shrink(1);
				ItemStack aid = new ItemStack(Level1Items.ALMOND_WATER);
				if (!player.getInventory().add(aid)) player.drop(aid, false);
				player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0));
				gestureTicks = 40;
				setAiState(STATE_GIFT);
				playSound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.9F, 1.05F);
			}
			return InteractionResult.SUCCESS;
		}
		if (!level().isClientSide()) {
			gestureTicks = 45;
			setAiState(STATE_BECKON);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		LivingEntity target = getTarget();
		if (target != null && target.isAlive()) {
			setAiState(STATE_COMBAT);
			return;
		}
		if (gestureTicks > 0) {
			gestureTicks--;
			return;
		}
		Player near = level.getNearestPlayer(this, 7.0);
		if (near != null && !near.isSpectator() && random.nextInt(50) == 0) {
			gestureTicks = 40;
			setAiState(STATE_BECKON);
		} else {
			setAiState(STATE_IDLE);
		}
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.PLAYER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.PLAYER_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.88F;
	}
}
