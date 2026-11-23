package dev.noclip.backrooms.level1;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A water pistol: a jet that shoves the nearest thing in front of you back and slows it, and puts out fires on it. */
final class SquirtGunItem extends net.minecraft.world.item.Item {
	private static final double RANGE = 8;

	SquirtGunItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
		Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
		for (int i = 1; i <= RANGE * 2; i++) {
			Vec3 at = eye.add(look.scale(i * 0.5));
			server.sendParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, 2, 0.05, 0.05, 0.05, 0.0);
		}
		LivingEntity target = null;
		double best = Double.MAX_VALUE;
		for (LivingEntity other : server.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RANGE), e -> e != player)) {
			Vec3 to = other.getBoundingBox().getCenter().subtract(eye);
			double distance = to.length();
			if (distance > RANGE || to.normalize().dot(look) < 0.95 || distance >= best) continue;
			best = distance;
			target = other;
		}
		if (target != null) {
			target.push(look.x * 0.8, 0.2, look.z * 0.8);
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
			target.clearFire();
		}
		server.playSound(null, player.blockPosition(), SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 0.5F, 1.8F);
		player.getCooldowns().addCooldown(player.getItemInHand(hand), 10);
		return InteractionResult.SUCCESS;
	}
}
