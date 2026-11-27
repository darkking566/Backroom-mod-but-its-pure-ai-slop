package dev.noclip.backrooms.level0;

import dev.noclip.backrooms.core.people.Person;
import dev.noclip.backrooms.core.rpg.Badges;
import dev.noclip.backrooms.core.trade.ShopRole;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Dynamic positional audio for Level 0:
 * - Lit fluorescent lamps hum steadily, with occasional loud surge fluctuations that strain hearing if you linger nearby.
 * - Darkened zones fall silent apart from distant lit borders or phantom hums that jump behind you as you chase them.
 * - Wall scratching and distant whispers echo from nearby corridors and sometimes trail behind you as you walk.
 */
final class Level0Ambience {
	// Replay slightly before the 14s clip ends so the gentle fade edges overlap smoothly.
	private static final int HUM_INTERVAL_TICKS = 260;
	private static final int PIANO_INTERVAL_TICKS = 360;
	private static final int MANILA_PIANO_RADIUS = 20;
	private static final int MANILA_HUM_SILENCE_RADIUS = 12;
	private static final int EERIE_MIN_TICKS = 700;
	private static final int EERIE_MAX_TICKS = 1500;
	private static final int SURGE_STRAIN_DELAY_TICKS = 50;
	private static final double SURGE_STRAIN_RADIUS = 8.5;
	private static final int ECHO_DELAY_TICKS = 50;

	private static final Map<UUID, Integer> NEXT_HUM_TICK = new HashMap<>();
	private static final Map<UUID, Integer> NEXT_PIANO_TICK = new HashMap<>();
	private static final Map<UUID, Integer> NEXT_EERIE_TICK = new HashMap<>();
	private static final Map<UUID, Boolean> DARK_DECOY_BEHIND = new HashMap<>();
	private static final Map<UUID, SurgeWarning> PENDING_SURGE = new HashMap<>();
	private static final Map<UUID, PendingEcho> PENDING_ECHO = new HashMap<>();

	private Level0Ambience() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Level0Ambience::tick);
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			if (player.level().dimension() != Level0.LEVEL_0) {
				NEXT_HUM_TICK.remove(id);
				NEXT_PIANO_TICK.remove(id);
				NEXT_EERIE_TICK.remove(id);
				DARK_DECOY_BEHIND.remove(id);
				PENDING_SURGE.remove(id);
				PENDING_ECHO.remove(id);
				continue;
			}
			ServerLevel level = player.level();
			RandomSource random = level.getRandom();
			long seed = Level0ChunkGenerator.mazeSeed(level.getChunkSource().randomState());
			ManilaRoomPlan.Room nearbyManila = ManilaRoomPlan.nearbyRoom(
					seed, player.getBlockX(), player.getBlockZ(), MANILA_PIANO_RADIUS);

			if (nearbyManila != null) {
				PENDING_SURGE.remove(id);
				PENDING_ECHO.remove(id);
				if (now % 40 == 0) {
					ensureManilaMartialMaster(level, nearbyManila);
				}
				if (nearbyManila.isInteriorBlock(player.getBlockX(), player.getBlockZ())
						&& player.hasEffect(MobEffects.NAUSEA)) {
					player.removeEffect(MobEffects.NAUSEA);
				}
				int nextPiano = NEXT_PIANO_TICK.getOrDefault(id, now);
				if (now >= nextPiano) {
					level.playSound(null, nearbyManila.centerX() + 0.5, Level0ChunkGenerator.CEILING_Y - 0.5,
							nearbyManila.centerZ() + 0.5, ModSounds.MANILA_PIANO, SoundSource.AMBIENT, 0.55F, 1.0F);
					NEXT_PIANO_TICK.put(id, now + PIANO_INTERVAL_TICKS);
				}
				double distSq = Math.pow(player.getX() - (nearbyManila.centerX() + 0.5), 2)
						+ Math.pow(player.getZ() - (nearbyManila.centerZ() + 0.5), 2);
				if (distSq > MANILA_HUM_SILENCE_RADIUS * MANILA_HUM_SILENCE_RADIUS) {
					int nextHum = NEXT_HUM_TICK.getOrDefault(id, now);
					if (now >= nextHum) {
						playLampHum(level, player, random, now);
						NEXT_HUM_TICK.put(id, now + HUM_INTERVAL_TICKS);
					}
				}
				continue;
			}
			NEXT_PIANO_TICK.remove(id);

			checkPendingSurge(level, player, now);
			checkPendingEcho(level, player, random, now);

			int nextHum = NEXT_HUM_TICK.getOrDefault(id, now);
			if (now >= nextHum) {
				playLampHum(level, player, random, now);
				NEXT_HUM_TICK.put(id, now + HUM_INTERVAL_TICKS);
			}

			int nextEerie = NEXT_EERIE_TICK.computeIfAbsent(id, k -> now + EERIE_MIN_TICKS / 2 + random.nextInt(EERIE_MIN_TICKS));
			if (now >= nextEerie) {
				playEerieSound(level, player, random, now);
				NEXT_EERIE_TICK.put(id, now + EERIE_MIN_TICKS + random.nextInt(EERIE_MAX_TICKS - EERIE_MIN_TICKS));
			}
		}
	}

	private static void checkPendingSurge(ServerLevel level, ServerPlayer player, int now) {
		SurgeWarning warning = PENDING_SURGE.get(player.getUUID());
		if (warning == null || now < warning.checkAtTick()) return;
		PENDING_SURGE.remove(player.getUUID());
		if (player.isCreative() || player.isSpectator() || !player.isAlive()) return;

		double dx = (warning.lampX() + 0.5) - player.getX();
		double dz = (warning.lampZ() + 0.5) - player.getZ();
		if (dx * dx + dz * dz <= SURGE_STRAIN_RADIUS * SURGE_STRAIN_RADIUS) {
			// Lingering beneath a surging lamp causes brief auditory disorientation.
			player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 60, 0, true, false, true));
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0, true, false, true));
			level.playSound(null, player.getX(), player.getY() + 1.5, player.getZ(),
					ModSounds.FLUORESCENT_HUM, SoundSource.AMBIENT, 0.35F, 1.72F);
		}
	}

	private static void checkPendingEcho(ServerLevel level, ServerPlayer player, RandomSource random, int now) {
		PendingEcho echo = PENDING_ECHO.get(player.getUUID());
		if (echo == null || now < echo.playAtTick()) return;
		PENDING_ECHO.remove(player.getUUID());

		Vec3 offset = offsetRelativeToLook(player, true, 7.0 + random.nextDouble() * 3.0);
		BlockPos pos = BlockPos.containing(player.getX() + offset.x, Level0ChunkGenerator.FLOOR_Y + 2, player.getZ() + offset.z);
		level.playSound(null, pos, echo.scratch() ? ModSounds.WALL_SCRATCH : ModSounds.DISTANT_WHISPER,
				SoundSource.AMBIENT, 0.22F, 0.80F + random.nextFloat() * 0.14F);
	}

	private static void playLampHum(ServerLevel level, ServerPlayer player, RandomSource random, int now) {
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

		Maze.Zone zone = Maze.zoneAt(seed, px, pz);
		if (zone == Maze.Zone.DARKENED) {
			if (bestDistSq != Double.MAX_VALUE && Math.sqrt(bestDistSq) <= 24.0 && random.nextBoolean()) {
				level.playSound(null, bestX + 0.5, Level0ChunkGenerator.CEILING_Y - 0.5, bestZ + 0.5,
						ModSounds.FLUORESCENT_HUM, SoundSource.AMBIENT, 0.28F, 0.94F + random.nextFloat() * 0.06F);
				return;
			}
			// Phantom shifting hum inside darkened zones: leads you on, then jumps behind you.
			boolean behind = DARK_DECOY_BEHIND.getOrDefault(player.getUUID(), false);
			DARK_DECOY_BEHIND.put(player.getUUID(), !behind);
			Vec3 decoy = offsetRelativeToLook(player, behind, 11.0 + random.nextDouble() * 4.0);
			level.playSound(null, player.getX() + decoy.x, Level0ChunkGenerator.CEILING_Y - 0.5, player.getZ() + decoy.z,
					ModSounds.FLUORESCENT_HUM, SoundSource.AMBIENT, 0.34F, 0.90F + random.nextFloat() * 0.08F);
			return;
		}

		if (bestDistSq == Double.MAX_VALUE) return;

		// Occasional loud volume surge as described in Level 0 lore.
		boolean surge = random.nextInt(7) == 0;
		float volume = surge ? 0.92F : 0.42F;
		float basePitch = zone == Maze.Zone.RED_ROOM ? 0.84F : 0.97F;
		float pitch = surge ? basePitch + 0.09F : basePitch + random.nextFloat() * 0.06F;

		level.playSound(null, bestX + 0.5, Level0ChunkGenerator.CEILING_Y - 0.5, bestZ + 0.5,
				ModSounds.FLUORESCENT_HUM, SoundSource.AMBIENT, volume, pitch);
		if (surge && bestDistSq <= SURGE_STRAIN_RADIUS * SURGE_STRAIN_RADIUS) {
			PENDING_SURGE.put(player.getUUID(), new SurgeWarning(bestX, bestZ, now + SURGE_STRAIN_DELAY_TICKS));
		}
	}

	private static void playEerieSound(ServerLevel level, ServerPlayer player, RandomSource random, int now) {
		long seed = Level0ChunkGenerator.mazeSeed(level.getChunkSource().randomState());
		boolean scratch = random.nextFloat() < 0.80F;

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
		if (random.nextFloat() < (scratch ? 0.45F : 0.25F)) {
			PENDING_ECHO.put(player.getUUID(), new PendingEcho(scratch, now + ECHO_DELAY_TICKS));
		}
	}

	private static Vec3 offsetRelativeToLook(ServerPlayer player, boolean behind, double distance) {
		Vec3 look = player.getLookAngle();
		double len = Math.hypot(look.x, look.z);
		double lx = len > 1.0E-4 ? look.x / len : 0.0;
		double lz = len > 1.0E-4 ? look.z / len : 1.0;
		double sign = behind ? -1.0 : 1.0;
		return new Vec3(lx * sign * distance, 0.0, lz * sign * distance);
	}

	private record SurgeWarning(int lampX, int lampZ, int checkAtTick) {
	}

	private record PendingEcho(boolean scratch, int playAtTick) {
	}

	private static void ensureManilaMartialMaster(ServerLevel level, ManilaRoomPlan.Room room) {
		int y = Level0ChunkGenerator.FLOOR_Y + 1;
		AABB interiorBox = new AABB(
				room.baseX() + 3, y - 1, room.baseZ() + 3,
				room.baseX() + ManilaRoomPlan.SPAN_BLOCKS - 3, y + 4, room.baseZ() + ManilaRoomPlan.SPAN_BLOCKS - 3);
		if (!level.getEntitiesOfClass(Person.class, interiorBox,
				p -> ShopRole.of(p) == ShopRole.MARTIAL_MASTER).isEmpty()) {
			return;
		}
		BlockPos preferred = new BlockPos(room.centerX() - 3, y, room.centerZ());
		if (canStandVendor(level, preferred)) {
			Badges.spawnVendor(level, preferred.getX() + 0.5, preferred.getY(), preferred.getZ() + 0.5);
			return;
		}
		for (int dx = 4; dx <= ManilaRoomPlan.SPAN_BLOCKS - 5; dx++) {
			for (int dz = 4; dz <= ManilaRoomPlan.SPAN_BLOCKS - 5; dz++) {
				BlockPos candidate = new BlockPos(room.baseX() + dx, y, room.baseZ() + dz);
				if (canStandVendor(level, candidate)) {
					Badges.spawnVendor(level, candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5);
					return;
				}
			}
		}
	}

	private static boolean canStandVendor(ServerLevel level, BlockPos pos) {
		return level.hasChunkAt(pos)
				&& !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()
				&& level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
				&& level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
	}
}
