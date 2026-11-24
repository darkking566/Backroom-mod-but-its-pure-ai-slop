package dev.noclip.backrooms.level2;

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

/** The sounds of the tunnels: buzzing lights near you, and now and then a knock in the pipes somewhere off. */
final class Level2Ambience {
	private static final int HUM_INTERVAL = 240;
	private static final Map<UUID, Integer> NEXT_HUM = new HashMap<>();
	private static final Map<UUID, Integer> NEXT_KNOCK = new HashMap<>();

	private Level2Ambience() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Level2Ambience::tick);
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			if (player.level().dimension() != Level2.LEVEL_2) {
				NEXT_HUM.remove(id);
				NEXT_KNOCK.remove(id);
				continue;
			}
			ServerLevel level = player.level();
			RandomSource random = level.getRandom();
			if (now >= NEXT_HUM.getOrDefault(id, now)) {
				hum(level, player, random);
				NEXT_HUM.put(id, now + HUM_INTERVAL);
			}
			if (now >= NEXT_KNOCK.computeIfAbsent(id, k -> now + 160 + random.nextInt(300))) {
				double angle = random.nextDouble() * Math.PI * 2, distance = 6 + random.nextDouble() * 14;
				level.playSound(null, player.getX() + Math.cos(angle) * distance, Level2ChunkGenerator.FLOOR_Y + 3,
						player.getZ() + Math.sin(angle) * distance, Level2Sounds.PIPE_KNOCK, SoundSource.AMBIENT,
						0.5F + random.nextFloat() * 0.4F, 0.5F + random.nextFloat() * 0.3F);
				NEXT_KNOCK.put(id, now + 300 + random.nextInt(600));
			}
		}
	}

	/** The nearest working light within a few blocks buzzes; in a dead stretch there is nothing. */
	private static void hum(ServerLevel level, ServerPlayer player, RandomSource random) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int px = player.getBlockX(), pz = player.getBlockZ(), y = Level2ChunkGenerator.TOP;
		for (int x = px - 10; x <= px + 10; x++) {
			for (int z = pz - 10; z <= pz + 10; z++) {
				BlockState state = level.getBlockState(pos.set(x, y, z));
				if (!(state.getBlock() instanceof Level2LightBlock)) continue;
				double distance = player.distanceToSqr(x + 0.5, y + 0.5, z + 0.5);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = pos.immutable();
				}
			}
		}
		if (best == null) return;
		boolean surge = random.nextInt(6) == 0;
		level.playSound(null, best, Level2Sounds.HUM, SoundSource.AMBIENT, surge ? 0.6F : 0.3F, surge ? 1.0F : 0.85F + random.nextFloat() * 0.05F);
	}
}
