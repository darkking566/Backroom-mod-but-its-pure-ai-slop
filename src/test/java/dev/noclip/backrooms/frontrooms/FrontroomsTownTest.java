package dev.noclip.backrooms.frontrooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.noclip.backrooms.frontrooms.FrontroomsTown.Kind;
import dev.noclip.backrooms.frontrooms.FrontroomsTown.Piece;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The staff town (FrontroomsTown): its objects stand where they can, and everything in it can be walked to. */
final class FrontroomsTownTest {
	private static final int G = FrontroomsTown.G;

	private static boolean clear(Piece piece) {
		return piece == null || piece.kind() == Kind.AIR || piece.kind() == Kind.DOOR;
	}

	/** Whether someone can stand at (x, z) on the town's ground: two blocks of room, no object in the way. */
	private static boolean walkable(int x, int z) {
		for (int y = G + 1; y <= G + 2; y++) {
			Piece piece = FrontroomsTown.piece(x, y, z);
			if (piece != null && (piece.kind() == Kind.PROP || piece.kind() == Kind.PROP_PART)) {
				String mount = FrontroomsTown.type(FrontroomsTown.places().get(piece.kind() == Kind.PROP ? piece.variant() : piece.variant()).id()).mount();
				if (!mount.equals("wall") && !mount.equals("ceiling")) return false;
				continue;
			}
			if (!clear(piece)) return false;
		}
		return true;
	}

	private static Set<Long> walk(int x, int z) {
		Set<Long> seen = new HashSet<>();
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		queue.add(new int[] {x, z});
		seen.add(key(x, z));
		while (!queue.isEmpty()) {
			int[] at = queue.poll();
			for (int[] step : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				int nx = at[0] + step[0], nz = at[1] + step[1];
				if (!FrontroomsTown.inside(nx, nz) || seen.contains(key(nx, nz)) || !walkable(nx, nz)) continue;
				seen.add(key(nx, nz));
				queue.add(new int[] {nx, nz});
			}
		}
		return seen;
	}

	private static long key(int x, int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}

	@Test
	void objectsStandInTheOpenOnSomething() {
		for (FrontroomsTown.Place place : FrontroomsTown.places()) {
			String mount = FrontroomsTown.type(place.id()).mount();
			for (int[] cell : FrontroomsTown.cells(place)) {
				assertTrue(clear(FrontroomsTown.bare(cell[0], cell[1], cell[2])), place.id() + " is built into something at " + cell[0] + ", " + cell[1] + ", " + cell[2]);
			}
			if (mount.equals("ceiling")) {
				Piece above = FrontroomsTown.bare(place.x(), place.y() + 1, place.z());
				assertNotNull(above, place.id() + " hangs from nothing");
			} else if (!mount.equals("wall")) {
				Piece below = FrontroomsTown.bare(place.x(), place.y() - 1, place.z());
				assertTrue(place.y() - 1 == G || below != null && !clear(below), place.id() + " floats at " + place.x() + ", " + place.y() + ", " + place.z());
			}
		}
	}

	@Test
	void everyHouseTheStoreAndThePicnicAreReachedFromTheStreet() {
		Set<Long> reached = walk(-60, 80);
		for (int x0 : FrontroomsTown.LOTS) {
			assertTrue(reached.contains(key(x0 + 4, FrontroomsTown.FRONT)), "the door of the house at " + x0 + " (north) is out of reach");
			assertTrue(reached.contains(key(x0 + 4, FrontroomsTown.MIRROR - FrontroomsTown.FRONT)), "the door of the house at " + x0 + " (south) is out of reach");
			assertTrue(reached.contains(key(x0 + 6, 65)), "inside the house at " + x0 + " is out of reach");
			assertTrue(reached.contains(key(x0 + 6, FrontroomsTown.MIRROR - 65)), "inside the south house at " + x0 + " is out of reach");
			assertEquals(Kind.DOOR, FrontroomsTown.piece(x0 + 4, G + 1, FrontroomsTown.FRONT).kind());
		}
		assertTrue(reached.contains(key(FrontroomsTown.SHOPKEEPER[0], FrontroomsTown.SHOPKEEPER[1] + 2)), "the store's counter is out of reach");
		for (int[] spot : FrontroomsTown.PICNIC) assertTrue(walkable(spot[0], spot[1]), "someone at the picnic stands in something");
	}

	@Test
	void theTownsObjectsAreTheToolsList() throws IOException {
		String list = Files.readString(Path.of("tools/film/town_list.py"));
		Matcher m = Pattern.compile("\\(\"(town_[a-z_]+)\", \\((\\d+), (\\d+), (\\d+)\\), \"([a-z]+)\"").matcher(list);
		int count = 0;
		while (m.find()) {
			FrontroomsTown.Type type = FrontroomsTown.type(m.group(1));
			assertNotNull(type, m.group(1) + " is in the tool's list but not in the town");
			assertEquals(m.group(2) + " " + m.group(3) + " " + m.group(4) + " " + m.group(5),
					type.width() + " " + type.height() + " " + type.depth() + " " + type.mount(), m.group(1) + " differs from the tool's list");
			count++;
		}
		assertEquals(9, count, "the tool's list of town objects");
	}
}
