package dev.noclip.backrooms.monsters.faceling;

import dev.noclip.backrooms.level1.Level1Items;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
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
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Entity 9 - Adult Faceling (無面靈):
 * Docile and uncannily polite wanderers in stained office attire unless provoked.
 * Inspects nearby players curiously, trades alloy for bandages/supplies, and fights back fiercely if attacked.
 */
public class Faceling extends PathfinderMob {
	public static final int STATE_CALM = 0;
	public static final int STATE_INSPECT = 1;
	public static final int STATE_AGGRO = 2;

	private static final EntityDataAccessor<Integer> DATA_STATE =
			SynchedEntityData.defineId(Faceling.class, EntityDataSerializers.INT);

	private int inspectTicks;
	private int angerTicks;

	public Faceling(EntityType<? extends Faceling> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 26)
				.add(Attributes.ATTACK_DAMAGE, 5)
				.add(Attributes.MOVEMENT_SPEED, 0.25)
				.add(Attributes.FOLLOW_RANGE, 24);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_STATE, STATE_CALM);
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
		goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.32, true));
		goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.72));
		goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8));
		goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (aiState() == STATE_AGGRO) return super.mobInteract(player, hand);
		ItemStack stack = player.getItemInHand(hand);
		if (stack.is(Level1Items.ALLOY)) {
			if (!level().isClientSide()) {
				if (!player.getAbilities().instabuild) stack.shrink(1);
				ItemStack gift = new ItemStack(random.nextBoolean() ? Level1Items.BANDAGE : Level1Items.ALMOND_WATER);
				if (!player.getInventory().add(gift)) player.drop(gift, false);
				inspectTicks = 70;
				setAiState(STATE_INSPECT);
				playSound(SoundEvents.VILLAGER_TRADE, 0.7F, 0.75F);
			}
			return InteractionResult.SUCCESS;
		}
		if (!level().isClientSide()) {
			inspectTicks = 45;
			setAiState(STATE_INSPECT);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		LivingEntity target = getTarget();
		if (target != null && target.isAlive() && angerTicks > 0) {
			angerTicks--;
			setAiState(STATE_AGGRO);
			return;
		}
		if (angerTicks <= 0 && target != null) {
			setTarget(null);
		}
		if (inspectTicks > 0) {
			inspectTicks--;
			setAiState(STATE_INSPECT);
			return;
		}
		Player near = level.getNearestPlayer(this, 4.5);
		if (near != null && !near.isSpectator() && random.nextInt(40) == 0) {
			inspectTicks = 35;
			setAiState(STATE_INSPECT);
		} else {
			setAiState(STATE_CALM);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && source.getEntity() instanceof LivingEntity attacker) {
			if (!(attacker instanceof Player p) || (!p.isCreative() && !p.isSpectator())) {
				setTarget(attacker);
				angerTicks = 400;
				setAiState(STATE_AGGRO);
			}
		}
		return hurt;
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
		return 0.68F;
	}
}
