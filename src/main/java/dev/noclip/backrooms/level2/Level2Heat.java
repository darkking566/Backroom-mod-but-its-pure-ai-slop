package dev.noclip.backrooms.level2;

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
	private static final int POISON_AFTER = 10;
	private static final int LOOP_COOLDOWN_SECONDS = 45;
	private static final Map<UUID, Integer> HOT_SECONDS = new HashMap<>();
	private static final Map<UUID, Integer> COLD_SECONDS = new HashMap<>();
	private static final Map<UUID, Integer> GAS_SECONDS = new HashMap<>();
	private static final Map<UUID, Integer> LOOP_COOLDOWN = new HashMap<>();

	private Level2Heat() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Level2Heat::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			HOT_SECONDS.clear();
			COLD_SECONDS.clear();
			GAS_SECONDS.clear();
			LOOP_COOLDOWN.clear();
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
		}
	}

	private static void clearPlayer(UUID id) {
		HOT_SECONDS.remove(id);
		COLD_SECONDS.remove(id);
		GAS_SECONDS.remove(id);
	}

	private static void tickHeat(long seed, ServerPlayer player, int x, int z) {
		if (!Level2Layout.hot(seed, x, z)) {
			HOT_SECONDS.remove(player.getUUID());
			return;
		}
		int seconds = HOT_SECONDS.merge(player.getUUID(), 1, Integer::sum);
		if (seconds == 1) player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level2.heat"));
		player.causeFoodExhaustion(EXHAUSTION);
		if (seconds >= WEAKENS_AFTER) {
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, true, false, true));
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
		player.teleportTo(level, tx + 0.5, player.getY(), tz + 0.5, java.util.Set.of(), player.getYRot(), player.getXRot(), false);
		player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level2.loop"));
		level.playSound(null, tx, player.getY(), tz, SoundEvents.ENDERMAN_TELEPORT, SoundSource.AMBIENT, 0.35F, 0.7F);
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
