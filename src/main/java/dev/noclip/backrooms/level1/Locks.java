package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.level1.Level1Layout.Kind;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;

/** Now and then a lock turns up hanging in the doorway of a room in the corridors, as if it had always been there. */
final class Locks {
	private static final int FIRST_DELAY = 60 * 20, DELAY = 120 * 20, DELAY_SPREAD = 120 * 20, RANGE = 40;
	private static final Map<UUID, Integer> NEXT = new HashMap<>();

	private Locks() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Locks::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> NEXT.clear());
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.getLevel(Level1.LEVEL_1);
		int now = server.getTickCount();
		if (level == null || now % 20 != 0) return;
		RandomSource random = level.getRandom();
		long seed = Level1ChunkGenerator.planSeed(level.getChunkSource().randomState());
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || Level1Layout.kindAt(seed, player.getBlockX(), player.getBlockZ()) != Kind.CORRIDORS) continue;
			int next = NEXT.computeIfAbsent(player.getUUID(), id -> now + FIRST_DELAY + random.nextInt(DELAY_SPREAD));
			if (now < next) continue;
			NEXT.put(player.getUUID(), now + DELAY + random.nextInt(DELAY_SPREAD));
			hangNear(level, player, seed);
		}
	}

	/** Hangs a lock in a doorway within range of the player; returns where, or null. */
	static BlockPos hangNear(ServerLevel level, ServerPlayer player, long seed) {
		int dx0 = Math.floorDiv(player.getBlockX(), Level1Layout.DISTRICT), dz0 = Math.floorDiv(player.getBlockZ(), Level1Layout.DISTRICT);
		for (int dx = dx0 - 1; dx <= dx0 + 1; dx++) {
			for (int dz = dz0 - 1; dz <= dz0 + 1; dz++) {
				List<CorridorPlan.Doorway> doorways = Level1Layout.doorways(seed, dx, dz);
				if (doorways.isEmpty()) continue;
				int start = level.getRandom().nextInt(doorways.size());
				for (int i = 0; i < doorways.size(); i++) {
					CorridorPlan.Doorway doorway = doorways.get((start + i) % doorways.size());
					BlockPos at = new BlockPos(dx * Level1Layout.DISTRICT + doorway.lockX(), Level1ChunkGenerator.LOW_CEILING_Y - 1, dz * Level1Layout.DISTRICT + doorway.lockZ());
					if (!at.closerThan(player.blockPosition(), RANGE) || !level.isLoaded(at) || !level.getBlockState(at).isAir()) continue;
					// Hung across the doorway: turned when the doorway runs along Z.
					boolean alongZ = doorway.tiles().length > 1 && doorway.tiles()[0][0] == doorway.tiles()[1][0];
					level.setBlock(at, Level1Blocks.NATURAL_LOCK.defaultBlockState()
							.setValue(OldComputerBlock.FACING, alongZ ? net.minecraft.core.Direction.EAST : net.minecraft.core.Direction.NORTH), Block.UPDATE_ALL);
					return at;
				}
			}
		}
		return null;
	}
}
