package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.level0.Level0;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The way from Level 0 to Level 1: "survive long enough to find a flickering wall to throw yourself through."
 *
 * After a few minutes in Level 0, a stretch of wall somewhere out of sight starts to flicker and crackle.
 * Walking into it drops the player into Level 1. Each player has at most one; it goes back to plain wall
 * once used, once its player wanders far off or leaves, and when the server stops. If Level 0 reshapes
 * itself over it, it opens again somewhere else. Red Room walls never flicker: those rooms are meant to trap.
 */
final class FlickeringWalls {
	/** Level 0 wall blocks that may start to flicker; listed in data, so this needs nothing from Level 0's code. */
	private static final TagKey<Block> LEVEL_0_WALLS = TagKey.create(Registries.BLOCK, Level1.id("level_0_walls"));
	private static final TagKey<Block> MANILA_ROOM_BLOCKS = TagKey.create(Registries.BLOCK, Level1.id("manila_room_blocks"));
	// The way into the parking structure is meant to be one of the hardest to find: a long wait, and it opens well away.
	private static final int FIRST_DELAY = 90 * 20, DELAY_SPREAD = 90 * 20;
	private static final int NEAREST = 16, FARTHEST = 40;
	private static final int RETRY_DELAY = 10 * 20, REOPEN_DELAY = 60 * 20, MOVED_DELAY = 20 * 20;
	private static final int GIVE_UP_DISTANCE = 80;
	private static final int CRACKLE_INTERVAL = 100;
	/** Loud enough to carry about 32 blocks through the maze. */
	private static final float CRACKLE_VOLUME = 2.0F;

	private record Wall(BlockPos bottom, BlockState lower, BlockState upper) {
	}

	private static final Map<UUID, Integer> TIME_INSIDE = new HashMap<>();
	private static final Map<UUID, Integer> DUE = new HashMap<>();
	private static final Map<UUID, Wall> WALLS = new HashMap<>();

	private FlickeringWalls() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(FlickeringWalls::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(FlickeringWalls::closeAll);
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		ServerLevel level0 = server.getLevel(Level0.LEVEL_0);
		if (level0 == null) return;

		// Walls of players who left the server.
		for (Iterator<Map.Entry<UUID, Wall>> it = WALLS.entrySet().iterator(); it.hasNext(); ) {
			var entry = it.next();
			if (server.getPlayerList().getPlayer(entry.getKey()) == null) {
				close(level0, entry.getValue());
				it.remove();
			}
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			Wall wall = WALLS.get(id);
			if (player.level() != level0 || player.isSpectator()) {
				if (wall != null) close(level0, WALLS.remove(id));
				TIME_INSIDE.remove(id);
				DUE.remove(id);
				continue;
			}

			if (wall != null) {
				if (level0.isLoaded(wall.bottom()) && !level0.getBlockState(wall.bottom()).is(Level1Blocks.FLICKERING_WALLPAPER)) {
					// Level 0 reshaped itself over it (Edge Shift), or it was broken: it turns up somewhere else soon.
					close(level0, WALLS.remove(id));
					DUE.put(id, TIME_INSIDE.getOrDefault(id, 0) + MOVED_DELAY);
				} else if (!player.blockPosition().closerThan(wall.bottom(), GIVE_UP_DISTANCE)) {
					close(level0, WALLS.remove(id));
					DUE.put(id, TIME_INSIDE.getOrDefault(id, 0) + REOPEN_DELAY);
				} else if (now % CRACKLE_INTERVAL == 0) {
					level0.playSound(null, wall.bottom().above(), Level1Sounds.WALL_FLICKER, SoundSource.AMBIENT,
							CRACKLE_VOLUME, 0.8F + level0.getRandom().nextFloat() * 0.4F);
				}
				continue;
			}

			if (now % 20 != 0) continue;
			int time = TIME_INSIDE.merge(id, 20, Integer::sum);
			int due = DUE.computeIfAbsent(id, key -> FIRST_DELAY + level0.getRandom().nextInt(DELAY_SPREAD));
			if (level0.getBlockState(player.blockPosition().below()).is(MANILA_ROOM_BLOCKS)) {
				due = Math.min(due, time + 40);
				DUE.put(id, due);
			}
			if (time >= due && open(player) == null) DUE.put(id, time + RETRY_DELAY);
		}
	}

	/** Makes a wall flicker somewhere near the player and out of their sight; returns its bottom block, or null. */
	static BlockPos open(ServerPlayer player) {
		ServerLevel level = player.level();
		if (level.dimension() != Level0.LEVEL_0 || !player.onGround() || WALLS.containsKey(player.getUUID())) return null;
		RandomSource random = level.getRandom();
		Vec3 look = player.getLookAngle().multiply(1, 0, 1).normalize();
		int feet = player.getBlockY();

		for (int attempt = 0; attempt < 80; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double distance = NEAREST + random.nextDouble() * (FARTHEST - NEAREST);
			double ax = Math.cos(angle), az = Math.sin(angle);
			// Behind or beside the player, never right in front of them.
			if (ax * look.x + az * look.z > 0.3) continue;
			BlockPos bottom = BlockPos.containing(player.getX() + ax * distance, feet, player.getZ() + az * distance);
			if (!level.isLoaded(bottom)) continue;
			// Level 0 repeats its centre Stage and keeps players in it (level0.Level0Space): a wall in a copy past a
			// seam could be seen but never reached.
			if (!dev.noclip.backrooms.level0.Level0Space.original(bottom.getX())
					|| !dev.noclip.backrooms.level0.Level0Space.original(bottom.getZ())) continue;
			BlockState lower = level.getBlockState(bottom), upper = level.getBlockState(bottom.above());
			if (!lower.is(LEVEL_0_WALLS) || !upper.is(LEVEL_0_WALLS) || !facesOpenFloor(level, bottom)) continue;

			WALLS.put(player.getUUID(), new Wall(bottom, lower, upper));
			BlockState flicker = Level1Blocks.FLICKERING_WALLPAPER.defaultBlockState();
			level.setBlock(bottom, flicker, Block.UPDATE_ALL);
			level.setBlock(bottom.above(), flicker, Block.UPDATE_ALL);
			level.playSound(null, bottom.above(), Level1Sounds.WALL_FLICKER, SoundSource.AMBIENT, 1.0F, 1.0F);
			return bottom;
		}
		return null;
	}

	/** A player is inside a flickering wall. */
	static void throughWall(ServerPlayer player, BlockPos pos) {
		MinecraftServer server = player.level().getServer();
		// Not in the middle of the movement tick; this runs every tick while inside, so check again when it runs.
		server.schedule(new TickTask(server.getTickCount(), () -> {
			if (player.level().dimension() != Level0.LEVEL_0) return;
			ServerLevel level0 = player.level();
			if (!Level1Travel.enter(player)) return;
			UUID id = player.getUUID();
			Wall own = WALLS.remove(id);
			if (own != null) close(level0, own);
			TIME_INSIDE.remove(id);
			DUE.remove(id);
		}));
	}

	/** Two blocks of open floor in front, so the wall is in a room or corridor and not at the back of a niche. */
	private static boolean facesOpenFloor(ServerLevel level, BlockPos bottom) {
		for (Direction side : Direction.Plane.HORIZONTAL) {
			boolean clear = true;
			for (int step = 1; step <= 2 && clear; step++) {
				BlockPos front = bottom.relative(side, step);
				clear = level.getBlockState(front).isAir() && level.getBlockState(front.above()).isAir()
						&& !level.getBlockState(front.below()).getCollisionShape(level, front.below()).isEmpty();
			}
			if (clear) return true;
		}
		return false;
	}

	private static void close(ServerLevel level, Wall wall) {
		if (level.getBlockState(wall.bottom()).is(Level1Blocks.FLICKERING_WALLPAPER)) {
			level.setBlock(wall.bottom(), wall.lower(), Block.UPDATE_ALL);
		}
		if (level.getBlockState(wall.bottom().above()).is(Level1Blocks.FLICKERING_WALLPAPER)) {
			level.setBlock(wall.bottom().above(), wall.upper(), Block.UPDATE_ALL);
		}
	}

	private static void closeAll(MinecraftServer server) {
		ServerLevel level0 = server.getLevel(Level0.LEVEL_0);
		if (level0 != null) WALLS.values().forEach(wall -> close(level0, wall));
		WALLS.clear();
		TIME_INSIDE.clear();
		DUE.clear();
	}
}
