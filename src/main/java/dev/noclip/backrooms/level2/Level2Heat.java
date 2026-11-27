package dev.noclip.backrooms.level2;

import dev.noclip.backrooms.core.LevelExits;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Environmental anomalies in Level 2:
 * - Unreasonably hot stretches (hunger & weakness, leading to Level 127)
 * - Abnormally cold stretches (slowness & frostbite)
 * - Burst pipes leaking putrid gas (nausea & poison, with smoke particles)
 * - Rare spatial loop in bare office-style dead-end corridors ("returning to the same hall")
 */
final class Level2Heat {
	/** Exhaustion per second in the heat: about a hunger point every ten seconds. */
	static final float EXHAUSTION = 0.4F;
	private static final int WEAKENS_AFTER = 30;
	private static final int HEAT_EXIT_AFTER = 40;
	private static final int POISON_AFTER = 10;
	private static final int LOOP_COOLDOWN_SECONDS = 45;
	private static final int LOOP_EXIT_TRIGGERS = 3;
	private static final int HOUSE_EXIT_SECONDS = 4;
	/** Konami walk sequence (N, N, S, S, W, E, W, E) between Level 2 tunnel cells -> The Hub. */
	private static final int[] KONAMI = {
			Level2Plan.NORTH, Level2Plan.NORTH, Level2Plan.SOUTH, Level2Plan.SOUTH,
			Level2Plan.WEST, Level2Plan.EAST, Level2Plan.WEST, Level2Plan.EAST
	};
	private static final Map<UUID, Integer> HOT_SECONDS = new HashMap<>();
	private static final Map<UUID, Integer> COLD_SECONDS = new HashMap<>();
	private static final Map<UUID, Integer> GAS_SECONDS = new HashMap<>();
	private static final Map<UUID, Integer> LOOP_COOLDOWN = new HashMap<>();
	private static final Map<UUID, Integer> LOOP_COUNT = new HashMap<>();
	private static final Map<UUID, Integer> HOUSE_SECONDS = new HashMap<>();
	private static final Map<UUID, long[]> LAST_CELL = new HashMap<>();
	private static final Map<UUID, Integer> KONAMI_STEP = new HashMap<>();

	private Level2Heat() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Level2Heat::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			HOT_SECONDS.clear();
			COLD_SECONDS.clear();
			GAS_SECONDS.clear();
			LOOP_COOLDOWN.clear();
			LOOP_COUNT.clear();
			HOUSE_SECONDS.clear();
			LAST_CELL.clear();
			KONAMI_STEP.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) return;
		ServerLevel level = server.getLevel(Level2.LEVEL_2);
		if (level == null) return;
		long seed = Level2ChunkGenerator.planSeed(level.getChunkSource().randomState());
		for (ServerPlayer player : level.players()) {
			if (player.isCreative() || player.isSpectator()) {
				clearPlayer(player.getUUID());
				continue;
			}
			int x = player.getBlockX(), z = player.getBlockZ();
			tickHeat(seed, player, x, z);
			tickCold(level, seed, player, x, z);
			tickGas(level, seed, player, x, z);
			tickOfficeLoop(level, seed, player, x, z);
			tickHouseRoom(seed, player, x, z);
			tickKonamiRoute(level, player, x, z);
		}
	}

	private static void clearPlayer(UUID id) {
		HOT_SECONDS.remove(id);
		COLD_SECONDS.remove(id);
		GAS_SECONDS.remove(id);
		HOUSE_SECONDS.remove(id);
		LAST_CELL.remove(id);
		KONAMI_STEP.remove(id);
	}

	private static void tickHeat(long seed, ServerPlayer player, int x, int z) {
		if (!Level2Layout.hot(seed, x, z)) {
			HOT_SECONDS.remove(player.getUUID());
			return;
		}
		int seconds = HOT_SECONDS.merge(player.getUUID(), 1, Integer::sum);
		if (seconds == 1) player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level2.heat"));
		player.causeFoodExhaustion(EXHAUSTION);
		if (seconds == WEAKENS_AFTER) {
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level2.heat_exit"));
		}
		if (seconds >= WEAKENS_AFTER) {
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, true, false, true));
		}
		if (seconds >= HEAT_EXIT_AFTER && (seconds - HEAT_EXIT_AFTER) % 5 == 0) {
			LevelExits.use(player, "127");
		}
	}

	private static void tickCold(ServerLevel level, long seed, ServerPlayer player, int x, int z) {
		if (!Level2Layout.cold(seed, x, z)) {
			COLD_SECONDS.remove(player.getUUID());
			return;
		}
		int seconds = COLD_SECONDS.merge(player.getUUID(), 1, Integer::sum);
		if (seconds == 1) player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level2.cold"));
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0, true, false, true));
		player.setTicksFrozen(Math.min(player.getTicksRequiredToFreeze() + 40, player.getTicksFrozen() + 25));
		level.sendParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 1.1, player.getZ(), 6, 2.0, 0.7, 2.0, 0.01);
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.9, player.getZ(), 2, 1.4, 0.4, 1.4, 0.005);
	}

	private static void tickGas(ServerLevel level, long seed, ServerPlayer player, int x, int z) {
		if (!Level2Layout.gas(seed, x, z)) {
			GAS_SECONDS.remove(player.getUUID());
			return;
		}
		int seconds = GAS_SECONDS.merge(player.getUUID(), 1, Integer::sum);
		if (seconds == 1) player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level2.gas"));
		player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 80, 0, true, false, true));
		if (seconds >= POISON_AFTER) {
			player.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0, true, false, true));
		}
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, player.getX(), player.getY() + 1.2, player.getZ(), 4, 1.8, 0.6, 1.8, 0.01);
	}

	private static void tickOfficeLoop(ServerLevel level, long seed, ServerPlayer player, int x, int z) {
		UUID id = player.getUUID();
		int cd = LOOP_COOLDOWN.getOrDefault(id, 0);
		if (cd > 0) {
			LOOP_COOLDOWN.put(id, cd - 1);
			return;
		}
		if (!Level2Layout.isOfficePatch(seed, x, z)) return;
		if (player.getDeltaMovement().horizontalDistanceSqr() < 0.002) return;
		if (Level2Layout.hot(seed, x, z) || Level2Layout.cold(seed, x, z) || Level2Layout.gas(seed, x, z)) return;
		Level2Plan plan = Level2Layout.plan(seed, x, z);
		int lx = Math.floorMod(x, Level2Plan.SIZE), lz = Math.floorMod(z, Level2Plan.SIZE);
		int cell = Level2Plan.cell(lx / Level2Plan.CELL, lz / Level2Plan.CELL);
		if (plan.roomOf[cell] >= 0 || plan.openings(cell) != 1) return;
		// Only loop if there is no exit/window/door in this dead-end cell.
		for (int d = 0; d < 4; d++) {
			if (plan.feature(cell, d) != Level2Plan.Feature.NONE) return;
		}
		int openDir = Integer.numberOfTrailingZeros(plan.open[cell] & 0xF);
		int tx = x + Level2Plan.DX[openDir] * 10, tz = z + Level2Plan.DZ[openDir] * 10;
		if (Level2Layout.tile(seed, tx, tz).kind() != Level2Layout.Kind.FLOOR) return;
		LOOP_COOLDOWN.put(id, LOOP_COOLDOWN_SECONDS);
		int count = LOOP_COUNT.merge(id, 1, Integer::sum);
		if (count >= LOOP_EXIT_TRIGGERS) {
			LOOP_COUNT.remove(id);
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level2.loop_exit"));
			level.playSound(null, x, player.getY(), z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.AMBIENT, 0.6F, 0.5F);
			LevelExits.use(player, "1");
			return;
		}
		player.teleportTo(level, tx + 0.5, player.getY(), tz + 0.5, java.util.Set.of(), player.getYRot(), player.getXRot(), false);
		player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level2.loop"));
		level.playSound(null, tx, player.getY(), tz, SoundEvents.ENDERMAN_TELEPORT, SoundSource.AMBIENT, 0.35F, 0.7F);
	}

	/** Rare empty rooms that feel eerily residential lead to Level 64 if you linger inside. */
	private static void tickHouseRoom(long seed, ServerPlayer player, int x, int z) {
		if (!isResidentialAnomalyRoom(seed, x, z)) {
			HOUSE_SECONDS.remove(player.getUUID());
			return;
		}
		int seconds = HOUSE_SECONDS.merge(player.getUUID(), 1, Integer::sum);
		if (seconds == 1) player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level2.house_room"));
		if (seconds >= HOUSE_EXIT_SECONDS && (seconds - HOUSE_EXIT_SECONDS) % 5 == 0) {
			LevelExits.use(player, "64");
		}
	}

	static boolean isResidentialAnomalyRoom(long seed, int x, int z) {
		Level2Plan plan = Level2Layout.plan(seed, x, z);
		int lx = Math.floorMod(x, Level2Plan.SIZE), lz = Math.floorMod(z, Level2Plan.SIZE);
		int r = plan.roomOf[Level2Plan.cell(lx / Level2Plan.CELL, lz / Level2Plan.CELL)];
		if (r < 0) return false;
		Level2Plan.Room room = plan.rooms.get(r);
		if (room.kind() != Level2Plan.RoomKind.EMPTY) return false;
		int rx = Math.floorDiv(x, Level2Plan.SIZE), rz = Math.floorDiv(z, Level2Plan.SIZE);
		return Math.floorMod(Level2Layout.hash(seed, rx * 31 + room.i(), rz * 31 + room.j(), 264), 16) == 0;
	}

	/** Walking the Konami cell sequence (N, N, S, S, W, E, W, E) in Level 2 opens the way to The Hub. */
	private static void tickKonamiRoute(ServerLevel level, ServerPlayer player, int x, int z) {
		int gx = Math.floorDiv(x, Level2Plan.CELL), gz = Math.floorDiv(z, Level2Plan.CELL);
		UUID id = player.getUUID();
		long[] prev = LAST_CELL.get(id);
		if (prev == null) {
			LAST_CELL.put(id, new long[] {gx, gz});
			return;
		}
		int dx = (int) (gx - prev[0]), dz = (int) (gz - prev[1]);
		if (dx == 0 && dz == 0) return;
		LAST_CELL.put(id, new long[] {gx, gz});
		int dir = Math.abs(dx) + Math.abs(dz) != 1 ? -1
				: dz == -1 ? Level2Plan.NORTH : dx == 1 ? Level2Plan.EAST : dz == 1 ? Level2Plan.SOUTH : Level2Plan.WEST;
		int step = KONAMI_STEP.getOrDefault(id, 0);
		if (dir == KONAMI[step]) {
			step++;
		} else if (dir == KONAMI[0]) {
			step = 1;
		} else {
			step = 0;
		}
		if (step >= KONAMI.length) {
			KONAMI_STEP.remove(id);
			level.playSound(null, x, player.getY(), z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 0.9F, 0.85F);
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level2.konami_hub"));
			LevelExits.use(player, "hub");
		} else {
			KONAMI_STEP.put(id, step);
		}
	}

	static boolean feelingIt(ServerPlayer player) {
		return HOT_SECONDS.containsKey(player.getUUID());
	}

	static boolean feelingCold(ServerPlayer player) {
		return COLD_SECONDS.containsKey(player.getUUID());
	}

	static boolean feelingGas(ServerPlayer player) {
		return GAS_SECONDS.containsKey(player.getUUID());
	}
}
