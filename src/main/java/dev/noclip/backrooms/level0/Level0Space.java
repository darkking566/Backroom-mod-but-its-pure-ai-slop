package dev.noclip.backrooms.level0;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.player.Player;

/**
 * Level 0 as a torus: a finite Stage whose edges you cannot see.
 *
 * The world repeats one 300 x 300 Stage (Maze.STAGE_BLOCKS) in every direction: within each REGION of 5 x 5 Stages,
 * every Stage is a copy of the region's centre Stage, because the generator asks the plan about template(x), template(z)
 * instead of x, z. StageLoop keeps players inside that centre Stage, moving them exactly one period (300 blocks) when
 * they cross an edge, so the copy they were walking into and the place they land are the same blocks: nothing jumps.
 * With the player in the centre, two whole Stages (600 blocks) of copies lie past every edge, beyond the longest view
 * distance (32 chunks, 512 blocks). Trips land tens of thousands of blocks apart (core.LevelExits.anchor), in different
 * regions, so each trip still gets a Stage of its own.
 *
 * Inside a centre Stage template(x) == x, so everything that asks the plan about where a player stands keeps working
 * unchanged; only code that looks across an edge needs template() (the arrival search, Edge Shift).
 */
public final class Level0Space {
	/** One Stage: the period of the repeat. */
	public static final int PERIOD = Maze.STAGE_BLOCKS;
	/** Stages per side of a region; its centre Stage is the one shown everywhere in it. */
	static final int REGION = 5;

	/** How far each player in Level 0 has been carried round the torus (blocks): distances count as walked. */
	private static final Map<UUID, int[]> CARRIED = new ConcurrentHashMap<>();

	private Level0Space() {
	}

	/**
	 * Where the player would be had Level 0 no edges: their position plus every Stage they have walked across since
	 * they came in. Walk east over the edge and back to where you started: that is 300 blocks east (a mapping
	 * beacon set down there counts as far from the landing).
	 */
	public static int walkedX(Player player, int x) {
		int[] carried = CARRIED.get(player.getUUID());
		return carried == null ? x : x + carried[0];
	}

	public static int walkedZ(Player player, int z) {
		int[] carried = CARRIED.get(player.getUUID());
		return carried == null ? z : z + carried[1];
	}

	/** StageLoop moved the player by (dx, dz). */
	static void moved(UUID player, int dx, int dz) {
		CARRIED.compute(player, (id, c) -> c == null ? new int[] {-dx, -dz} : new int[] {c[0] - dx, c[1] - dz});
	}

	/** Forgets everyone not in Level 0 (they start again when they come in). */
	static void keepOnly(java.util.Set<UUID> inLevel0) {
		CARRIED.keySet().retainAll(inLevel0);
	}

	/** What Level 0's generator puts at a block (before anything changes it): for other levels' code. */
	public static net.minecraft.world.level.block.state.BlockState planned(net.minecraft.server.level.ServerLevel level0, int x, int y, int z) {
		return Level0ChunkGenerator.worldBlockAt(Level0ChunkGenerator.mazeSeed(level0.getChunkSource().randomState()), x, y, z);
	}

	/** The coordinate in the region's centre Stage that this coordinate is a copy of. */
	public static int template(int coord) {
		int stage = Math.floorDiv(coord, PERIOD);
		int centre = Math.floorDiv(stage + REGION / 2, REGION) * REGION;
		return centre * PERIOD + Math.floorMod(coord, PERIOD);
	}

	/** Whether the coordinate lies in a centre Stage (the original, not a copy). */
	public static boolean original(int coord) {
		return template(coord) == coord;
	}

	/** Whether a maze cell, and the cells `margin` around it, lie in the same centre Stage. */
	static boolean originalCell(int cell, int margin) {
		int lo = (cell - margin) * Maze.CELL, hi = (cell + margin + 1) * Maze.CELL - 1;
		return original(lo) && original(hi) && Math.floorDiv(lo, PERIOD) == Math.floorDiv(hi, PERIOD);
	}
}
