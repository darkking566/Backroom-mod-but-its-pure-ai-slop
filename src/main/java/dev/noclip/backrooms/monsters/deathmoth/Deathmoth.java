package dev.noclip.backrooms.monsters.deathmoth;

import dev.noclip.backrooms.monsters.Tuning;
import dev.noclip.backrooms.monsters.Retreat;
import dev.noclip.backrooms.level1.Level1Items;
import dev.noclip.backrooms.monsters.smiler.Smiler;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Entity 4 - Female Deathmoth (雌性死亡飛蛾):
 * A territorial giant moth that hovers through dark halls, enters phototactic frenzy toward light sources,
 * spits corrosive acid at mid-range, and can be briefly pacified with Almond Water.
 */
public class Deathmoth extends Monster {
	public static final int STATE_HOVER = 0;
	public static final int STATE_CHASE = 1;
	public static final int STATE_LIGHT_FRENZY = 2;
	public static final int STATE_FEEDING = 3;

	private static final EntityDataAccessor<Integer> DATA_STATE =
			SynchedEntityData.defineId(Deathmoth.class, EntityDataSerializers.INT);

	private static final byte EVENT_SPIT = 4;
	private static final int SPIT_AT = 11, SPIT_TICKS = 26;

	public final AnimationState spitAnimation = new AnimationState();
	private int spitTimer = -1, spitCooldown;
	private LivingEntity spitTarget;
	private int pacifiedTicks;

	public Deathmoth(EntityType<? extends Deathmoth> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, Tuning.hp(36))
				.add(Attributes.ATTACK_DAMAGE, 6)
				.add(Attributes.MOVEMENT_SPEED, 0.29)
				.add(Attributes.FOLLOW_RANGE, 32)
				.add(Attributes.ARMOR, 3);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_STATE, STATE_HOVER);
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
		goalSelector.addGoal(2, new MothHuntGoal(this));
		goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.75));
		goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12));
		goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
				(entity, level) -> pacifiedTicks <= 0));
	}

	@Override
	protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
		// Winged hover prevents fall damage.
	}

	@Override
	public void aiStep() {
		super.aiStep();
		Vec3 vel = getDeltaMovement();
		if (!onGround() && vel.y < -0.12) {
			setDeltaMovement(vel.x, vel.y * 0.65, vel.z);
		}
		if (level().isClientSide() && random.nextInt(5) == 0) {
			level().addParticle(ParticleTypes.SPORE_BLOSSOM_AIR,
					getX() + (random.nextDouble() - 0.5) * 0.9,
					getY() + 0.5 + random.nextDouble() * 0.7,
					getZ() + (random.nextDouble() - 0.5) * 0.9,
					0, -0.01, 0);
		}
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (stack.is(Level1Items.ALMOND_WATER)) {
			if (!level().isClientSide()) {
				if (!player.getAbilities().instabuild) {
					stack.shrink(1);
					ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
					if (!player.getInventory().add(bottle)) player.drop(bottle, false);
				}
				pacifiedTicks = 180;
				spitTimer = -1;
				setTarget(null);
				setAiState(STATE_FEEDING);
				playSound(SoundEvents.HONEY_DRINK.value(), 0.9F, 0.85F);
			}
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	void startSpit(LivingEntity target) {
		spitTarget = target;
		spitTimer = 0;
		level().broadcastEntityEvent(this, EVENT_SPIT);
		playSound(SoundEvents.LLAMA_SPIT, 1.1F, 0.55F);
	}

	boolean canSpit() {
		return spitTimer < 0 && tickCount >= spitCooldown && pacifiedTicks <= 0;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (pacifiedTicks > 0) {
			pacifiedTicks--;
			setAiState(pacifiedTicks > 130 ? STATE_FEEDING : STATE_HOVER);
			return;
		}

		LivingEntity target = getTarget();
		if (target != null && target.isAlive()) {
			boolean lit = target instanceof Player p
					&& (Smiler.isLightItem(p.getMainHandItem()) || Smiler.isLightItem(p.getOffhandItem()));
			setAiState(lit ? STATE_LIGHT_FRENZY : STATE_CHASE);
		} else {
			setAiState(STATE_HOVER);
		}

		if (spitTimer >= 0) {
			spitTimer++;
			if (spitTarget != null && spitTarget.isAlive()) {
				getLookControl().setLookAt(spitTarget, 45, 45);
				if (spitTimer == SPIT_AT && hasLineOfSight(spitTarget) && distanceToSqr(spitTarget) <= 169.0) {
					Vec3 dir = spitTarget.getEyePosition().subtract(getEyePosition()).normalize();
					for (int i = 1; i <= 6; i++) {
						level.sendParticles(ParticleTypes.ITEM_SLIME,
								getX() + dir.x * i * 1.4, getEyeY() + dir.y * i * 1.4, getZ() + dir.z * i * 1.4,
								4, 0.12, 0.12, 0.12, 0.02);
					}
					if (spitTarget.hurtServer(level, damageSources().mobAttack(this), 4.5F)) {
						spitTarget.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
						spitTarget.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0));
					}
					playSound(SoundEvents.SLIME_SQUISH, 1.0F, 0.7F);
				}
			}
			if (spitTimer >= SPIT_TICKS) {
				spitTimer = -1;
				spitTarget = null;
				spitCooldown = tickCount + 65;
			}
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt) pacifiedTicks = 0;
		return hurt;
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == EVENT_SPIT) {
			spitAnimation.start(tickCount);
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.PHANTOM_FLAP;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.SILVERFISH_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SILVERFISH_DEATH;
	}

	private static final class MothHuntGoal extends Goal {
		private final Deathmoth mob;
		private int attackCooldown;

		MothHuntGoal(Deathmoth mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = mob.getTarget();
			return mob.pacifiedTicks <= 0 && target != null && target.isAlive();
		}

		@Override
		public void stop() {
			mob.getNavigation().stop();
		}

		@Override
		public void tick() {
			LivingEntity target = mob.getTarget();
			if (target == null || !(mob.level() instanceof ServerLevel serverLevel)) return;
			if (attackCooldown > 0) attackCooldown--;

			mob.getLookControl().setLookAt(target, 40, 40);
			double distSqr = mob.distanceToSqr(target);
			boolean frenzy = mob.aiState() == STATE_LIGHT_FRENZY;

			if (distSqr <= 5.2) {
				mob.getNavigation().stop();
				if (attackCooldown <= 0) {
					attackCooldown = 18;
					mob.doHurtTarget(serverLevel, target);
				}
				return;
			}
			if (distSqr >= 20.0 && distSqr <= 144.0 && mob.canSpit() && mob.hasLineOfSight(target)) {
				mob.getNavigation().stop();
				mob.startSpit(target);
				return;
			}
			if (mob.spitTimer < 0) {
				mob.getNavigation().moveTo(target, frenzy ? 1.35 : 1.08);
			}
		}
	}
}
