package dev.noclip.backrooms;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Pitfall abyss and sensory lures in Pits zones:
 * - Falling deep into a pit triggers blinding darkness and lethal void consumption.
 * - Standing near a pit edge reveals faint blue sparks and whispers from the blackness below.
 */
final class PitEffects {
	private static final int ABYSS_CHECK_TICKS = 5;
	private static final int LURE_CHECK_TICKS = 20;
	private static final double LETHAL_Y = 20.0;

	private PitEffects() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(PitEffects::tick);
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		if (now % ABYSS_CHECK_TICKS != 0) return;

		ServerLevel level = server.getLevel(BackroomsMod.LEVEL_0);
		if (level == null) return;

		long seed = Level0ChunkGenerator.mazeSeed(level.getChunkSource().randomState());
		RandomSource random = level.getRandom();
		boolean checkLure = now % LURE_CHECK_TICKS == 0;

		for (ServerPlayer player : level.players()) {
			if (player.isCreative() || player.isSpectator() || !player.isAlive()) continue;

			if (player.getY() < LETHAL_Y && Maze.isPit(seed, player.getBlockX(), player.getBlockZ())) {
				player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, true, false, true));
				player.hurtServer(level, level.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
				continue;
			}

			if (checkLure && Maze.zoneAt(seed, player.getBlockX(), player.getBlockZ()) == Maze.Zone.PITS) {
				emitPitLure(level, seed, player, random);
			}
		}
	}

	private static void emitPitLure(ServerLevel level, long seed, ServerPlayer player, RandomSource random) {
		int px = player.getBlockX(), pz = player.getBlockZ();
		for (int attempt = 0; attempt < 8; attempt++) {
			int tx = px + random.nextInt(13) - 6;
			int tz = pz + random.nextInt(13) - 6;
			if (!Maze.isPit(seed, tx, tz)) continue;

			double py = Level0ChunkGenerator.FLOOR_Y - 5.0 - random.nextDouble() * 5.0;
			level.sendParticles(player, ParticleTypes.SOUL_FIRE_FLAME, false, false,
					tx + 0.5, py, tz + 0.5, 2, 0.3, 0.6, 0.3, 0.005);
			if (random.nextInt(36) == 0) {
				level.playSound(null, tx + 0.5, Level0ChunkGenerator.FLOOR_Y - 4.0, tz + 0.5,
						ModSounds.DISTANT_WHISPER, SoundSource.AMBIENT, 0.20F, 0.72F + random.nextFloat() * 0.12F);
			}
			return;
		}
	}
}
