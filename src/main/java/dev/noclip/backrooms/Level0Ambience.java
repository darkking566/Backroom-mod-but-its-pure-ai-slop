package dev.noclip.backrooms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Dynamic positional audio for Level 0:
 * - Lit fluorescent lamps hum steadily, with occasional loud surge fluctuations.
 * - Darkened zones fall silent; only distant lit lamps beyond the border remain faintly audible.
 * - Occasional wall scratching and distant whispers echo from nearby corridors.
 */
final class Level0Ambience {
	// Replay slightly before the 14s clip ends so the gentle fade edges overlap smoothly.
	private static final int HUM_INTERVAL_TICKS = 260;
	private static final int EERIE_MIN_TICKS = 500;
	private static final int EERIE_MAX_TICKS = 1100;

	private static final Map<UUID, Integer> NEXT_HUM_TICK = new HashMap<>();
	private static final Map<UUID, Integer> NEXT_EERIE_TICK = new HashMap<>();

	private Level0Ambience() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Level0Ambience::tick);
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.level().dimension() != BackroomsMod.LEVEL_0) {
				NEXT_HUM_TICK.remove(player.getUUID());
				NEXT_EERIE_TICK.remove(player.getUUID());
				continue;
			}
			ServerLevel level = player.level();
			RandomSource random = level.getRandom();
			UUID id = player.getUUID();

			int nextHum = NEXT_HUM_TICK.getOrDefault(id, now);
			if (now >= nextHum) {
				playLampHum(level, player, random);
				NEXT_HUM_TICK.put(id, now + HUM_INTERVAL_TICKS);
			}

			int nextEerie = NEXT_EERIE_TICK.computeIfAbsent(id, k -> now + EERIE_MIN_TICKS / 2 + random.nextInt(EERIE_MIN_TICKS));
			if (now >= nextEerie) {
				playEerieSound(level, player, random);
				NEXT_EERIE_TICK.put(id, now + EERIE_MIN_TICKS + random.nextInt(EERIE_MAX_TICKS - EERIE_MIN_TICKS));
			}
		}
	}

	private static void playLampHum(ServerLevel level, ServerPlayer player, RandomSource random) {
		long seed = Level0ChunkGenerator.mazeSeed(level.getChunkSource().randomState());
		int px = player.getBlockX(), pz = player.getBlockZ();
		int pcx = Math.floorDiv(px, Maze.CELL), pcz = Math.floorDiv(pz, Maze.CELL);

		// Find the nearest working lamp within 4 cells (~28 blocks).
		int bestX = 0, bestZ = 0;
		double bestDistSq = Double.MAX_VALUE;
		for (int dcx = -4; dcx <= 4; dcx++) {
			for (int dcz = -4; dcz <= 4; dcz++) {
				int lx = (pcx + dcx) * Maze.CELL + Maze.CENTER;
				int lz = (pcz + dcz) * Maze.CELL + Maze.CENTER;
				if (!Maze.lampWorks(seed, lx, lz)) continue;
				double dx = (lx + 0.5) - player.getX();
				double dz = (lz + 0.5) - player.getZ();
				double distSq = dx * dx + dz * dz;
				if (distSq < bestDistSq) {
					bestDistSq = distSq;
					bestX = lx;
					bestZ = lz;
				}
			}
		}

		// Deep inside a darkened zone: complete silence.
		if (bestDistSq == Double.MAX_VALUE) return;

		Maze.Zone zone = Maze.zoneAt(seed, px, pz);
		double dist = Math.sqrt(bestDistSq);

		// Inside a darkened zone near a lit border: only a faint directional hum from afar.
		if (zone == Maze.Zone.DARKENED) {
			if (dist > 26.0) return;
			level.playSound(null, bestX + 0.5, Level0ChunkGenerator.CEILING_Y - 0.5, bestZ + 0.5,
					ModSounds.FLUORESCENT_HUM, SoundSource.AMBIENT, 0.28F, 0.94F + random.nextFloat() * 0.06F);
			return;
		}

		// Occasional loud volume surge as described in Level 0 lore.
		boolean surge = random.nextInt(7) == 0;
		float volume = surge ? 0.92F : 0.42F;
		float basePitch = zone == Maze.Zone.RED_ROOM ? 0.84F : 0.97F;
		float pitch = surge ? basePitch + 0.09F : basePitch + random.nextFloat() * 0.06F;

		level.playSound(null, bestX + 0.5, Level0ChunkGenerator.CEILING_Y - 0.5, bestZ + 0.5,
				ModSounds.FLUORESCENT_HUM, SoundSource.AMBIENT, volume, pitch);
	}

	private static void playEerieSound(ServerLevel level, ServerPlayer player, RandomSource random) {
		long seed = Level0ChunkGenerator.mazeSeed(level.getChunkSource().randomState());
		boolean scratch = random.nextFloat() < 0.55F;

		// Pick a nearby offset 7..15 blocks away; for scratches prefer a solid wall block.
		double sx = player.getX();
		double sz = player.getZ();
		for (int attempt = 0; attempt < 12; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = 7.0 + random.nextDouble() * 8.0;
			double tx = player.getX() + Math.cos(angle) * distance;
			double tz = player.getZ() + Math.sin(angle) * distance;
			int bx = (int) Math.floor(tx), bz = (int) Math.floor(tz);
			boolean isWall = Maze.isWall(seed, bx, bz);
			if (scratch == isWall || attempt == 11) {
				sx = tx;
				sz = tz;
				break;
			}
		}

		BlockPos soundPos = BlockPos.containing(sx, Level0ChunkGenerator.FLOOR_Y + 2, sz);
		if (scratch) {
			level.playSound(null, soundPos, ModSounds.WALL_SCRATCH, SoundSource.AMBIENT,
					0.36F + random.nextFloat() * 0.22F, 0.86F + random.nextFloat() * 0.26F);
		} else {
			level.playSound(null, soundPos, ModSounds.DISTANT_WHISPER, SoundSource.AMBIENT,
					0.28F + random.nextFloat() * 0.18F, 0.84F + random.nextFloat() * 0.20F);
		}
	}
}
