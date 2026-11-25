package dev.noclip.backrooms.level0p1.robots;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A robot of Zenith Station. Neutral, as the Wiki has it: they go about what is left of the station's business, wander,
 * glance at you, and never attack. Each kind is its own entity type (client side: RobotRenderer draws the model made
 * in Blender, art/robots). Broken ones spit sparks.
 */
public class Robot extends PathfinderMob {
	private final Kind kind;

	public enum Kind {
		CLEANER("cleaner", 0.16, 0.55F, 0.9F, 1.3F), CHEF("chef", 0.2, 0.6F, 1.2F, 1.7F), SERVANT("servant", 0.15, 0.5F, 0.6F, 1.6F);

		public final String id;
		final double speed;
		final float walkSpeed, width, height;

		Kind(String id, double speed, float walkSpeed, float width, float height) {
			this.id = id;
			this.speed = speed;
			this.walkSpeed = walkSpeed;
			this.width = width;
			this.height = height;
		}
	}

	public Robot(EntityType<? extends Robot> type, Level level) {
		super(type, level);
		Kind found = Kind.CLEANER;
		for (Kind candidate : Kind.values()) {
			if (Robots.type(candidate) == type) found = candidate;
		}
		kind = found;
	}

	public Kind kind() {
		return kind;
	}

	public static AttributeSupplier.Builder createAttributes(Kind kind) {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 24).add(Attributes.MOVEMENT_SPEED, kind.speed)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0, 0.004F));
		goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
		goalSelector.addGoal(7, new RandomLookAroundGoal(this));
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (level() instanceof ServerLevel server && kind == Kind.SERVANT && random.nextInt(60) == 0) {
			server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.1, getZ() + 0.1, 5, 0.25, 0.3, 0.25, 0.05);
			server.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.COPPER_BULB_TURN_OFF, SoundSource.NEUTRAL, 0.4F, 1.6F);
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return kind == Kind.CLEANER ? SoundEvents.BEACON_AMBIENT : null;
	}

	@Override
	protected SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
		return SoundEvents.IRON_GOLEM_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.IRON_GOLEM_DEATH;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 200;
	}

	@Override
	protected float getSoundVolume() {
		return 0.3F;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return true;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}
}
