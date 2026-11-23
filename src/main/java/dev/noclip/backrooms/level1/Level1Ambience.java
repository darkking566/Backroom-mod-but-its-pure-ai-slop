package dev.noclip.backrooms.level1;

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
import net.minecraft.world.level.block.state.BlockState;

/**
 * Sound in Level 1: the nearest working light hums; "random clangs echoed through the pipes on the ceiling";
 * now and then something rumbles far off in the concrete.
 */
final class Level1Ambience {
	private static final int HUM_INTERVAL = 260;
	private static final Map<UUID, Integer> NEXT_HUM = new HashMap<>();
	private static final Map<UUID, Integer> NEXT_CLANG = new HashMap<>();
	private static final Map<UUID, Integer> NEXT_RUMBLE = new HashMap<>();

	private Level1Ambience() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Level1Ambience::tick);
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			if (player.level().dimension() != Level1.LEVEL_1) {
				NEXT_HUM.remove(id);
				NEXT_CLANG.remove(id);
				NEXT_RUMBLE.remove(id);
				continue;
			}
			ServerLevel level = player.level();
			RandomSource random = level.getRandom();

			if (now >= NEXT_HUM.getOrDefault(id, now)) {
				hum(level, player, random);
				NEXT_HUM.put(id, now + HUM_INTERVAL);
			}
			if (now >= NEXT_CLANG.computeIfAbsent(id, k -> now + 200 + random.nextInt(400))) {
				double angle = random.nextDouble() * Math.PI * 2, distance = 8 + random.nextDouble() * 14;
				level.playSound(null, player.getX() + Math.cos(angle) * distance, Level1ChunkGenerator.CEILING_Y - 1,
						player.getZ() + Math.sin(angle) * distance, Level1Sounds.PIPE_CLANG, SoundSource.AMBIENT,
						0.5F + random.nextFloat() * 0.4F, 0.55F + random.nextFloat() * 0.25F);
				NEXT_CLANG.put(id, now + 400 + random.nextInt(700));
			}
			if (now >= NEXT_RUMBLE.computeIfAbsent(id, k -> now + 900 + random.nextInt(900))) {
				double angle = random.nextDouble() * Math.PI * 2;
				level.playSound(null, player.getX() + Math.cos(angle) * 20, player.getY(), player.getZ() + Math.sin(angle) * 20,
						Level1Sounds.RUMBLE, SoundSource.AMBIENT, 0.7F, 0.5F + random.nextFloat() * 0.2F);
				NEXT_RUMBLE.put(id, now + 1200 + random.nextInt(1500));
			}
		}
	}

	/** The nearest lit hall light or bulb within a few blocks hums; in the dark there is nothing. */
	private static void hum(ServerLevel level, ServerPlayer player, RandomSource random) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int px = player.getBlockX(), pz = player.getBlockZ();
		for (int x = px - 12; x <= px + 12; x++) {
			for (int z = pz - 12; z <= pz + 12; z++) {
				boolean hallLamp = Math.floorMod(x, Level1Layout.BAY_X) == 5 && Math.floorMod(z, 8) == 4;
				boolean bulb = Math.floorMod(x, 4) == 2 && Math.floorMod(z, 4) == 2;
				if (!hallLamp && !bulb) continue;
				int y = hallLamp ? Level1ChunkGenerator.CEILING_Y - 1 : Level1ChunkGenerator.LOW_CEILING_Y - 1;
				BlockState state = level.getBlockState(pos.set(x, y, z));
				if (!(state.getBlock() instanceof Level1LampBlock) || !state.getValue(Level1LampBlock.LIT)) continue;
				double distance = player.distanceToSqr(x + 0.5, y + 0.5, z + 0.5);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = pos.immutable();
				}
			}
		}
		if (best == null) return;
		boolean surge = random.nextInt(8) == 0;
		level.playSound(null, best, Level1Sounds.LAMP_HUM, SoundSource.AMBIENT,
				surge ? 0.7F : 0.3F, surge ? 1.0F : 0.88F + random.nextFloat() * 0.05F);
	}
}
