package dev.noclip.backrooms.level0;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * The route of the first trip's cable (docs/企劃書/1-遊戲玩法/README.md, "第一趟：跟著纜線走"): from where a newcomer lands
 * to a wall 120 to 150 blocks away, along the corridors, keeping to their middle. Where the glowing door goes, where
 * the writing on the walls goes, and where the researchers' things lie are worked out here too.
 *
 * Pure: it only asks {@link Ground} about columns, so it is tested on made-up mazes.
 */
public final class GuidePlan {
	/** How far from the landing the door is, in blocks (straight across, not along the cable). */
	static final int MIN_DISTANCE = 120, MAX_DISTANCE = 150;
	/** How far the search may go from the landing; the cable winds, so further than MAX_DISTANCE. */
	static final int RADIUS = 190;
	/** Blocks of cable between two pieces of writing, and between two cable reels. */
	static final int WRITING_EVERY = 7, REEL_EVERY = 40;
	/** No writing this near the door (it would run over it), but the last line, set this far along its wall. */
	static final int CLEAR_OF_DOOR = 3;
	/** A step next to a wall costs this much more: the cable keeps to the middle of a corridor. */
	private static final int WALL_COST = 4;
	private static final int[][] SIDES = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

	/** What the maze has at a column of Level 0. */
	public interface Ground {
		/** Floor underfoot and two blocks of air above it. */
		boolean walkable(int x, int z);

		/** A wall: solid from the floor to above eye height (something to write on, something to set a door against). */
		boolean wall(int x, int z);
	}

	/** A spot on a wall: the wall block, and the side of it that faces the cable ({@link #SIDES} index). */
	public record Face(int x, int z, int side) {
	}

	/**
	 * A route. {@code path} runs from the landing to the block in front of the door; the door stands at
	 * ({@code doorX}, {@code doorZ}) with its back to the wall on side {@code doorBack} and its face toward the cable.
	 */
	public record Plan(List<int[]> path, int doorX, int doorZ, int doorBack, List<Face> writing, List<int[]> reels) {
	}

	private GuidePlan() {
	}

	/** The route from (startX, startZ), or null if no wall that far away can be reached. */
	public static Plan plan(Ground ground, int startX, int startZ) {
		int size = 2 * RADIUS + 1;
		int[] cost = new int[size * size];
		int[] from = new int[size * size];
		byte[] known = new byte[size * size];
		Arrays.fill(cost, Integer.MAX_VALUE);
		int start = index(RADIUS, RADIUS, size);
		if (!walkable(ground, known, startX, startZ, RADIUS, RADIUS, size)) return null;
		cost[start] = 0;
		from[start] = -1;
		PriorityQueue<long[]> open = new PriorityQueue<>((a, b) -> Long.compare(a[0], b[0]));
		open.add(new long[] {0, start});
		while (!open.isEmpty()) {
			long[] next = open.poll();
			int at = (int) next[1];
			if (next[0] != cost[at]) continue;
			int lx = at % size, lz = at / size;
			int x = startX + lx - RADIUS, z = startZ + lz - RADIUS;
			double distance = Math.hypot(lx - RADIUS, lz - RADIUS);
			if (distance >= MIN_DISTANCE && distance <= MAX_DISTANCE && from[at] >= 0) {
				int back = doorBack(ground, x, z, from[at] % size - lx, from[at] / size - lz);
				if (back >= 0) return finish(ground, startX, startZ, size, from, from[at], x, z, back);
			}
			for (int[] side : SIDES) {
				int nx = lx + side[0], nz = lz + side[1];
				if (nx < 0 || nz < 0 || nx >= size || nz >= size) continue;
				if (!walkable(ground, known, x + side[0], z + side[1], nx, nz, size)) continue;
				int to = index(nx, nz, size);
				int step = 1 + (nearWall(ground, known, x + side[0], z + side[1], nx, nz, size) ? WALL_COST : 0);
				if (cost[at] + step < cost[to]) {
					cost[to] = cost[at] + step;
					from[to] = at;
					open.add(new long[] {cost[to], to});
				}
			}
		}
		return null;
	}

	/**
	 * Which side of (x, z) has a wall for the door's back, or -1. The way the cable comes in may not be the wall, and
	 * the door is not set beside an opening of the wall (both neighbours along the wall must be wall too), so it fills
	 * a stretch of plain wall and is seen from down the corridor.
	 */
	private static int doorBack(Ground ground, int x, int z, int cameDx, int cameDz) {
		for (int s = 0; s < SIDES.length; s++) {
			int[] side = SIDES[s];
			if (side[0] == cameDx && side[1] == cameDz) continue;
			int wx = x + side[0], wz = z + side[1];
			if (!ground.wall(wx, wz)) continue;
			int ax = side[1], az = side[0];
			if (ground.wall(wx + ax, wz + az) && ground.wall(wx - ax, wz - az)
					&& ground.walkable(x - side[0], z - side[1])) return s;
		}
		return -1;
	}

	private static Plan finish(Ground ground, int startX, int startZ, int size, int[] from, int last, int doorX, int doorZ, int back) {
		List<int[]> path = new ArrayList<>();
		for (int at = last; at >= 0; at = from[at]) {
			path.add(new int[] {startX + at % size - RADIUS, startZ + at / size - RADIUS});
		}
		java.util.Collections.reverse(path);
		Set<Long> onPath = new HashSet<>();
		for (int[] p : path) onPath.add(key(p[0], p[1]));
		onPath.add(key(doorX, doorZ));

		List<Face> writing = new ArrayList<>();
		Set<Long> used = new HashSet<>();
		int sinceWriting = WRITING_EVERY;
		List<int[]> reels = new ArrayList<>();
		int sinceReel = 0;
		for (int i = 0; i < path.size(); i++) {
			int[] p = path.get(i);
			sinceWriting++;
			sinceReel++;
			if (sinceWriting >= WRITING_EVERY) {
				Face face = wallBeside(ground, p, used);
				if (face != null && Math.max(Math.abs(face.x() - doorX), Math.abs(face.z() - doorZ)) <= CLEAR_OF_DOOR) face = null;
				if (face != null) {
					writing.add(face);
					used.add(key(face.x(), face.z()));
					sinceWriting = 0;
				}
			}
			if (sinceReel >= REEL_EVERY && i > 0 && i < path.size() - 4) {
				int[] reel = floorBeside(ground, p, onPath);
				if (reel != null) {
					reels.add(reel);
					onPath.add(key(reel[0], reel[1]));
					sinceReel = 0;
				}
			}
		}
		// The last words are on the wall the door stands against, a little to one side of it (open floor in front).
		int[] wallSide = SIDES[back];
		int ax = wallSide[1], az = wallSide[0];
		last:
		for (int along = CLEAR_OF_DOOR; along <= CLEAR_OF_DOOR + 1; along++) {
			for (int sign : new int[] {1, -1}) {
				int wx = doorX + wallSide[0] + sign * along * ax, wz = doorZ + wallSide[1] + sign * along * az;
				if (ground.wall(wx, wz) && ground.walkable(wx - wallSide[0], wz - wallSide[1]) && used.add(key(wx, wz))) {
					writing.add(new Face(wx, wz, (back + 2) % 4));
					break last;
				}
			}
		}
		return new Plan(List.copyOf(path), doorX, doorZ, back, List.copyOf(writing), List.copyOf(reels));
	}

	/** How far to either side of the cable writing may be (the cable keeps to the middle of corridors 3 to 5 wide). */
	static final int WRITING_REACH = 4;

	/** The nearest wall straight out from a step of the cable, facing it across open floor, that has no writing yet. */
	private static Face wallBeside(Ground ground, int[] p, Set<Long> used) {
		Face best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (int s = 0; s < SIDES.length; s++) {
			for (int d = 1; d <= WRITING_REACH; d++) {
				int wx = p[0] + SIDES[s][0] * d, wz = p[1] + SIDES[s][1] * d;
				if (ground.wall(wx, wz)) {
					if (d < bestDistance && !used.contains(key(wx, wz))) {
						best = new Face(wx, wz, (s + 2) % 4);
						bestDistance = d;
					}
					break;
				}
				if (!ground.walkable(wx, wz)) break;
			}
		}
		return best;
	}

	/** Open floor next to a step of the cable, off it, for a reel. */
	private static int[] floorBeside(Ground ground, int[] p, Set<Long> taken) {
		for (int[] side : SIDES) {
			int fx = p[0] + side[0], fz = p[1] + side[1];
			if (ground.walkable(fx, fz) && !taken.contains(key(fx, fz))) return new int[] {fx, fz};
		}
		return null;
	}

	private static boolean walkable(Ground ground, byte[] known, int x, int z, int lx, int lz, int size) {
		int i = index(lx, lz, size);
		if (known[i] == 0) known[i] = (byte) (ground.walkable(x, z) ? 1 : 2);
		return known[i] == 1;
	}

	private static boolean nearWall(Ground ground, byte[] known, int x, int z, int lx, int lz, int size) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) continue;
				int nx = lx + dx, nz = lz + dz;
				if (nx < 0 || nz < 0 || nx >= size || nz >= size) return true;
				if (!walkable(ground, known, x + dx, z + dz, nx, nz, size)) return true;
			}
		}
		return false;
	}

	private static int index(int lx, int lz, int size) {
		return lz * size + lx;
	}

	private static long key(int x, int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}

	/** How many lines of each kind of writing there are (the language files' graffiti.noclip_backrooms.kind.n). */
	static final int GUIDE_LINES = 10, THING_LINES = 10, LOST_LINES = 13, TEAM_LINES = 5, HINT_LINES = 4;

	/**
	 * What is written on each of {@code count} walls, from the landing to the door: first "get out", "follow the cable"
	 * and "something lives here"; then the lost and frightened ones; then the researchers'; then the ones that show the
	 * way, "almost there" the last of them; and by the door, the ones that make you wonder about the institute.
	 * No line twice.
	 */
	static List<String> lines(int count, java.util.Random random) {
		List<String> lost = new ArrayList<>();
		for (int i = 0; i < LOST_LINES; i++) lost.add("lost." + i);
		for (int i = 1; i < THING_LINES; i++) lost.add("thing." + i);
		java.util.Collections.shuffle(lost, random);
		List<String> guide = new ArrayList<>();
		for (int i = 2; i < GUIDE_LINES - 1; i++) guide.add("guide." + i);
		java.util.Collections.shuffle(guide, random);
		List<String> hints = new ArrayList<>();
		for (int i = 0; i < HINT_LINES; i++) hints.add("hint." + i);
		java.util.Collections.shuffle(hints, random);

		List<String> opening = List.of("guide.0", "guide.1", "thing.0");
		if (count <= opening.size()) return List.copyOf(opening.subList(0, Math.max(0, count)));
		int rest = count - opening.size();
		int hint = Math.min(HINT_LINES, Math.max(1, rest / 10));
		int ways = Math.min(guide.size() + 1, Math.max(1, rest / 4));
		int team = Math.min(TEAM_LINES, Math.max(0, rest / 5));
		int fear = Math.min(lost.size(), Math.max(0, rest - hint - ways - team));
		// More walls than frightened lines: the rest get more of the doubts.
		hint = Math.min(HINT_LINES, hint + Math.max(0, rest - hint - ways - team - fear));
		List<String> out = new ArrayList<>(opening);
		out.addAll(lost.subList(0, fear));
		for (int i = 0; i < team; i++) out.add("team." + i);
		out.addAll(guide.subList(0, ways - 1));
		out.add("guide." + (GUIDE_LINES - 1));
		out.addAll(hints.subList(0, hint));
		return List.copyOf(out.subList(0, Math.min(count, out.size())));
	}

	/** The direction from a block toward its neighbour on side {@code s}, as {dx, dz}. */
	static int[] side(int s) {
		return SIDES[s].clone();
	}
}
