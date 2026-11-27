package dev.noclip.backrooms.mixin;

import dev.noclip.backrooms.level0.Level0Mirror;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Level 0 repeats its centre Stage (level0.Level0Mirror): a sound or particles near one edge of the Stage are heard and
 * seen across the other edge too, where the copy next door shows the same place. Played again at the copies' positions
 * for whoever is near them.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMirrorEffects {
	@Unique
	private static final ThreadLocal<Boolean> NOCLIP_BACKROOMS$ECHOING = ThreadLocal.withInitial(() -> false);

	@Inject(method = "playSeededSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
			at = @At("RETURN"))
	private void noclipBackrooms$echoSound(Entity except, double x, double y, double z, Holder<SoundEvent> sound, SoundSource source,
			float volume, float pitch, long seed, CallbackInfo info) {
		ServerLevel level = (ServerLevel) (Object) this;
		if (NOCLIP_BACKROOMS$ECHOING.get() || !Level0Mirror.echoes(level)) return;
		NOCLIP_BACKROOMS$ECHOING.set(true);
		try {
			for (Vec3 at : Level0Mirror.copiesHeard(level, x, z, sound.value().getRange(volume))) {
				level.playSeededSound(except, at.x, y, at.z, sound, source, volume, pitch, seed);
			}
		} finally {
			NOCLIP_BACKROOMS$ECHOING.set(false);
		}
	}

	@Inject(method = "sendParticles(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDIDDDD)I", at = @At("RETURN"))
	private <T extends ParticleOptions> void noclipBackrooms$echoParticles(T particle, boolean overrideLimiter, boolean alwaysShow,
			double x, double y, double z, int count, double xDist, double yDist, double zDist, double speed, CallbackInfoReturnable<Integer> info) {
		ServerLevel level = (ServerLevel) (Object) this;
		if (NOCLIP_BACKROOMS$ECHOING.get() || !Level0Mirror.echoes(level)) return;
		NOCLIP_BACKROOMS$ECHOING.set(true);
		try {
			for (Vec3 at : Level0Mirror.copiesHeard(level, x, z, overrideLimiter ? 512 : 32)) {
				level.sendParticles(particle, overrideLimiter, alwaysShow, at.x, y, at.z, count, xDist, yDist, zDist, speed);
			}
		} finally {
			NOCLIP_BACKROOMS$ECHOING.set(false);
		}
	}
}
