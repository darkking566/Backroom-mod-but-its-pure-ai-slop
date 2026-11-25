package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.level1.Level1Layout.Base;
import dev.noclip.backrooms.level1.Level1Layout.Ceiling;
import dev.noclip.backrooms.level1.Level1Layout.Kind;
import dev.noclip.backrooms.level1.Level1Layout.Tile;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Multi-layer vertical architecture for Level 1 hall districts: soaring vaulted halls with suspended skywalks,
 * 3-to-4-deck stacked parking garages with open lightwells, deep sunken basins with overlooking balconies,
 * and multi-tier underground maintenance tunnels.
 *
 * All geometry is a pure function of (seed, x, y, z) and preserves the main floor (y = 32) connectivity,
 * district borders, settlements, exits and props.
 */
public final class Level1Elevation {
	private static final int D = Level1Layout.DISTRICT;
	private static final int FLOOR = Level1ChunkGenerator.FLOOR_Y;
	private static final int PROFILE_SALT = 41;
	private static final int VARIANT_SALT = 43;

	/** All Y levels where blackout-controlled hall lamps may appear across all vertical profiles and landmarks. */
	static final int[] LAMP_YS = {18, 25, 29, 37, 39, 41, 43, 45, 47, 49, 53, 55};

	public enum Profile {
		/** Classic 5-block parking/warehouse ceiling (y = 38). */
		STANDARD,
		/** Soaring 16-to-22-block vaulted hall with multi-tier cross-beams and suspended skywalk bridges. */
		VAULTED,
		/** 3-to-4 stacked walkable parking decks (B1, 1F, 2F, optional 3F) with open central lightwells and stairs. */
		MULTI_DECK,
		/** Deep sunken central basin (y = 24 or y = 18) under a towering ceiling (y = 48), with overlooking balconies. */
		SUNKEN_BASIN,
		/** High main hall (y = 42) over a two-tier underground drainage and pipe-maintenance tunnel network. */
		SUBLEVEL_TUNNELS
	}

	private Level1Elevation() {
	}

	/** Which vertical profile a 64x64 district uses. Origin, corridors and settlements stay STANDARD. */
	public static Profile profile(long seed, int dx, int dz) {
		if (dx == 0 && dz == 0) return Profile.STANDARD;
		Kind kind = Level1Layout.kind(seed, dx, dz);
		if (kind == Kind.CORRIDORS) return Profile.STANDARD;
		if (Level1Layout.base(seed, dx, dz) != Base.NONE) return Profile.STANDARD;
		int roll = Math.floorMod(Level1Layout.hash(seed, dx, dz, PROFILE_SALT), 100);
		if (roll < 22) return Profile.STANDARD;
		if (roll < 42) return Profile.VAULTED;
		if (roll < 64) return Profile.MULTI_DECK;
		if (roll < 82) return Profile.SUNKEN_BASIN;
		return Profile.SUBLEVEL_TUNNELS;
	}

	/** Per-district variant bit (0 or 1): controls extra height/depth tiers. */
	static boolean extraTier(long seed, int dx, int dz) {
		return Math.floorMod(Level1Layout.hash(seed, dx, dz, VARIANT_SALT), 2) == 1;
	}

	/** Highest open ceiling Y for this district. */
	public static int ceilingY(long seed, int dx, int dz) {
		Level1Landmarks.Landmark lm = Level1Landmarks.landmark(seed, dx, dz);
		if (lm != Level1Landmarks.Landmark.NONE) return Level1Landmarks.ceilingY(lm);
		Profile p = profile(seed, dx, dz);
		boolean extra = extraTier(seed, dx, dz);
		return switch (p) {
			case STANDARD -> Level1ChunkGenerator.CEILING_Y;
			case VAULTED -> extra ? 54 : 48;
			case MULTI_DECK -> extra ? 54 : 46;
			case SUNKEN_BASIN -> 48;
			case SUBLEVEL_TUNNELS -> 42;
		};
	}

	/**
	 * Returns an override block for a non-standard vertical profile, or null to fall back to the standard generator.
	 * Exits, props and settlements are handled before this method is called.
	 */
	static BlockState block(long seed, Tile tile, Ceiling ceiling, int x, int y, int z) {
		if (y <= 0 || y >= Level1ChunkGenerator.HEIGHT - 1) return null;
		int dx = Math.floorDiv(x, D), dz = Math.floorDiv(z, D);
		Profile p = profile(seed, dx, dz);
		if (p == Profile.STANDARD) return null;
		// Corridor doorways (and the green sign over them) keep their standard shape in any hall.
		if (tile == Tile.OPENING || tile == Tile.OPENING_SIGN) return null;

		int lx = Math.floorMod(x, D), lz = Math.floorMod(z, D);
		boolean extra = extraTier(seed, dx, dz);
		boolean nearFeature = isNearExitOrProp(seed, dx, dz, x, z);

		return switch (p) {
			case VAULTED -> vaulted(seed, tile, ceiling, x, y, z, lx, lz, extra, nearFeature);
			case MULTI_DECK -> multiDeck(seed, tile, ceiling, dx, dz, x, y, z, lx, lz, extra, nearFeature);
			case SUNKEN_BASIN -> sunkenBasin(seed, tile, ceiling, x, y, z, lx, lz, extra, nearFeature);
			case SUBLEVEL_TUNNELS -> sublevelTunnels(seed, tile, ceiling, dx, dz, x, y, z, lx, lz, extra, nearFeature);
			case STANDARD -> null;
		};
	}

	/**
	 * Soaring 16-to-22-block vaulted hall with multi-tier cross-beams, hanging lights/pipes at the top,
	 * suspended skywalks at y=40 (and y=47 when extra=true), and pillar-safe staircases rising from y=32.
	 */
	private static BlockState vaulted(long seed, Tile tile, Ceiling ceiling, int x, int y, int z, int lx, int lz,
			boolean extra, boolean nearFeature) {
		int topCeil = extra ? 54 : 48;
		if (y < FLOOR) return null;
		if (y > topCeil) return solidConcrete();
		if (y == topCeil) return state(Level1Blocks.GARAGE_CEILING);

		// Keep district border walls / corridor perimeters solid above the standard doorway height.
		if (tile == Tile.HALL_WALL || tile == Tile.PERIMETER) return state(Level1Blocks.GARAGE_WALL);
		if (tile == Tile.PILLAR) {
			if (y == FLOOR) return null;
			return pillarAt(seed, x, y, z, FLOOR);
		}
		// Keep protected bays (exits/props) clear of skywalks and staircases.
		if (nearFeature && y <= FLOOR + 10) {
			if (y <= Level1ChunkGenerator.CEILING_Y) return null;
			return air();
		}

		// Staircase 1: rises from main floor (32) to first skywalk (40) along +Z at bx in [4, 5], lz in [19..26].
		int bx = Math.floorMod(x, Level1Layout.BAY_X);
		boolean inStair1X = lx >= 24 && lx <= 39 && (bx == 4 || bx == 5) && !shelfInRun(seed, x, z, lz, 19, 28);
		if (inStair1X && lz >= 19 && lz <= 26) {
			int stepY = FLOOR + 1 + (lz - 19); // 33..40
			if (y == stepY) return stairs(Direction.SOUTH);
			if (y > FLOOR && y < stepY) return state(Level1Blocks.GARAGE_WALL);
			if (y > stepY && y <= stepY + 3) return air();
		}
		// Landing from the top step onto the skywalk: floor, no railing, headroom.
		if (inStair1X && (lz == 27 || lz == 28)) {
			if (y == 40) return state(Level1Blocks.GARAGE_FLOOR);
			if (y > 40 && y <= 43) return air();
		}

		// First skywalk tier at y = 40: a giant cross spanning the hall.
		boolean onSky1 = (lx >= 28 && lx <= 35 && lz >= 4 && lz <= 59)
				|| (lz >= 28 && lz <= 35 && lx >= 4 && lx <= 59);
		boolean onSky1Edge = onSky1 && !((lx >= 29 && lx <= 34 && lz >= 5 && lz <= 58)
				|| (lz >= 29 && lz <= 34 && lx >= 5 && lx <= 58));
		boolean stair2Foot = extra && lx >= 28 && lx <= 35 && (bx == 4 || bx == 5) && lz == 36;
		if (onSky1) {
			if (y == 40) return state(Level1Blocks.GARAGE_FLOOR);
			if (y == 41 && onSky1Edge && !stair2Foot) {
				boolean alongX = lz == 28 || lz == 35 || lz == 4 || lz == 59;
				return barrier(alongX ? Direction.Axis.X : Direction.Axis.Z);
			}
			// Underside lamps lighting the main floor below the skywalk.
			if (y == 39 && ceiling == Ceiling.LAMP) return state(Level1Blocks.GARAGE_LIGHT);
		}

		// Second skywalk tier at y = 47 when extra=true, with staircase from y=40 -> y=47.
		if (extra) {
			boolean inStair2X = lx >= 28 && lx <= 35 && (bx == 4 || bx == 5);
			if (inStair2X && lz >= 37 && lz <= 43) {
				int stepY = 41 + (lz - 37); // 41..47
				if (y == stepY) return stairs(Direction.SOUTH);
				if (y > 40 && y < stepY) return state(Level1Blocks.GARAGE_WALL);
				if (y > stepY && y <= stepY + 3) return air();
			}
			if (inStair2X && (lz == 44 || lz == 45)) {
				if (y == 47) return state(Level1Blocks.GARAGE_FLOOR);
				if (y > 47 && y <= 50) return air();
			}
			boolean onSky2 = (lx >= 10 && lx <= 53 && lz >= 44 && lz <= 49)
					|| (lz >= 10 && lz <= 53 && lx >= 12 && lx <= 17);
			boolean onSky2Edge = onSky2 && !((lx >= 11 && lx <= 52 && lz >= 45 && lz <= 48)
					|| (lz >= 11 && lz <= 52 && lx >= 13 && lx <= 16));
			if (onSky2) {
				if (y == 47) return state(Level1Blocks.GARAGE_FLOOR);
				if (y == 48 && onSky2Edge) {
					boolean alongX = lz == 44 || lz == 49 || lz == 10 || lz == 53;
					return barrier(alongX ? Direction.Axis.X : Direction.Axis.Z);
				}
			}
		}

		// Main floor (y = 32) and warehouse shelves on y = 33..34 keep standard behavior.
		if (y == FLOOR) return null;
		if (tile == Tile.SHELF && y <= FLOOR + 2) return null;

		// Multi-tier structural cross-beams every 6 blocks along pillar lines (bz < 2).
		int bz = Math.floorMod(z, Level1Layout.BAY_Z);
		if (bz < 2 && (y == 38 || y == 44 || (extra && y == 50))) {
			return state(Level1Blocks.GARAGE_CEILING);
		}

		// Top ceiling fixtures (lamps, pipes, beams).
		if (y == topCeil - 1) return hallFixture(ceiling);
		return air();
	}

	/**
	 * 3-to-4 stacked full parking decks (B1 at y=24, 1F at y=32, 2F at y=40, optional 3F at y=48),
	 * with a dramatic central open lightwell/atrium so you can see all levels stacked vertically,
	 * and pillar-safe concrete staircases connecting every deck.
	 */
	private static BlockState multiDeck(long seed, Tile tile, Ceiling ceiling, int dx, int dz, int x, int y, int z,
			int lx, int lz, boolean extra, boolean nearFeature) {
		int topCeil = extra ? 54 : 46;
		int b1Floor = 24;
		if (y < b1Floor) return solidConcrete();
		if (y > topCeil) return solidConcrete();
		if (y == topCeil) return state(Level1Blocks.GARAGE_CEILING);

		// Retain a 2-block border wall on B1 and upper decks unless the adjacent district is also MULTI_DECK.
		boolean onUpperOrLower = y < FLOOR || y >= Level1ChunkGenerator.CEILING_Y;
		if (onUpperOrLower && isClosedDeckEdge(seed, dx, dz, lx, lz, Profile.MULTI_DECK)) {
			return state(Level1Blocks.GARAGE_WALL);
		}

		// Pillars run uninterrupted from B1 (24) all the way to the top ceiling.
		if (tile == Tile.PILLAR) {
			if (y == FLOOR) return null;
			int deckBase = y < FLOOR ? b1Floor : (y < 40 ? FLOOR : (y < 48 ? 40 : 48));
			return pillarAt(seed, x, y, z, deckBase);
		}
		if (tile == Tile.HALL_WALL || tile == Tile.PERIMETER) {
			return y >= FLOOR ? state(Level1Blocks.GARAGE_WALL) : state(Level1Blocks.GARAGE_WALL);
		}

		// Central open atrium/lightwell (lx = 22..41, lz = 22..41) cuts through 2F (and 3F) with barriers,
		// letting you look straight up across 3-4 decks! Keep 1F walkable so layout connectivity is untouched.
		boolean inAtrium = lx >= 23 && lx <= 40 && lz >= 23 && lz <= 40;
		boolean onAtriumRim = lx >= 22 && lx <= 41 && lz >= 22 && lz <= 41 && !inAtrium;

		// Keep exit/prop bays untouched around 1F.
		if (nearFeature && y >= FLOOR - 6 && y <= FLOOR + 10) {
			if (y >= FLOOR && y <= Level1ChunkGenerator.CEILING_Y) return null;
		}

		// Pillar-safe staircases in two opposite bays:
		// Stair A (B1 y=24 <-> 1F y=32): lx in [10..20], bx in [4, 5], lz in [3..10]
		// Stair B (1F y=32 <-> 2F y=40): lx in [44..54], bx in [4, 5], lz in [3..10]
		// Stair C (2F y=40 <-> 3F y=48): lx in [10..20], bx in [4, 5], lz in [51..58]
		int bx = Math.floorMod(x, Level1Layout.BAY_X);
		boolean isStairColA = lx >= 10 && lx <= 20 && (bx == 4 || bx == 5) && !nearFeature;
		boolean isStairColB = lx >= 44 && lx <= 54 && (bx == 4 || bx == 5) && !nearFeature;

		// Stair A: B1 (24) up to 1F (32) heading +Z
		if (isStairColA && lz >= 3 && lz <= 10 && y >= b1Floor && y <= FLOOR + 3) {
			int stepY = b1Floor + 1 + (lz - 3); // 25..32
			if (y == stepY) return stairs(Direction.SOUTH);
			if (y > b1Floor && y < stepY) return state(Level1Blocks.GARAGE_WALL);
			if (y > stepY && y <= Math.max(FLOOR + 3, stepY + 3)) return air();
		}
		// Keep a clear landing at the bottom of Stair A on B1
		if (isStairColA && lz == 2 && y > b1Floor && y < FLOOR) return air();

		// Stair B: 1F (32) up to 2F (40) heading +Z
		if (isStairColB && lz >= 3 && lz <= 10 && y >= FLOOR && y <= 43) {
			if (y == FLOOR) return null;
			int stepY = FLOOR + 1 + (lz - 3); // 33..40
			if (y == stepY) return stairs(Direction.SOUTH);
			if (y > FLOOR && y < stepY) return state(Level1Blocks.GARAGE_WALL);
			if (y > stepY && y <= 43) return air();
		}

		// Stair C: 2F (40) up to 3F (48) heading +Z (when extra=true)
		if (extra && isStairColA && lz >= 51 && lz <= 58 && y >= 40 && y <= 51) {
			int stepY = 41 + (lz - 51); // 41..48
			if (y == stepY) return stairs(Direction.SOUTH);
			if (y > 40 && y < stepY) return state(Level1Blocks.GARAGE_WALL);
			if (y > stepY && y <= 51) return air();
		}

		// B1 deck (y = 24..31): floor at 24, ceiling slab at 30/31 (under 1F floor at 32)
		if (y < FLOOR) {
			if (y == b1Floor) return deckFloor(tile, true);
			if (y == 31) return state(Level1Blocks.GARAGE_CEILING);
			if (y == 29) return hallFixture(ceiling); // lamps & pipes under B1 ceiling
			return air();
		}

		// 1F deck (y = 32..39)
		if (y < 40) {
			if (y == FLOOR) return null;
			if (tile == Tile.SHELF && y <= FLOOR + 2) return null;
			if (y == 38 && !inAtrium) return state(Level1Blocks.GARAGE_CEILING);
			if (y == 37 && !inAtrium) return hallFixture(ceiling);
			if (y == 39 && !inAtrium) return solidConcrete();
			return air();
		}

		// 2F deck (y = 40..47)
		if (y < 48 || !extra) {
			if (inAtrium) {
				if (y == topCeil - 1) return hallFixture(ceiling);
				return air();
			}
			if (y == 40) return deckFloor(tile, false);
			if (y == 41 && onAtriumRim) {
				boolean alongX = lz == 22 || lz == 41;
				return barrier(alongX ? Direction.Axis.X : Direction.Axis.Z);
			}
			if (!extra) {
				if (y == 45) return hallFixture(ceiling);
				return air();
			}
			if (y == 47) return solidConcrete();
			if (y == 46) return state(Level1Blocks.GARAGE_CEILING);
			if (y == 45) return hallFixture(ceiling);
			return air();
		}

		// 3F deck (y = 48..53) when extra=true
		if (inAtrium) {
			if (y == topCeil - 1) return hallFixture(ceiling);
			return air();
		}
		if (y == 48) return deckFloor(tile, false);
		if (y == 49 && onAtriumRim) {
			boolean alongX = lz == 22 || lz == 41;
			return barrier(alongX ? Direction.Axis.X : Direction.Axis.Z);
		}
		if (y == 53) return hallFixture(ceiling);
		return air();
	}

	/**
	 * Deep sunken central concrete basin (y = 24 or y = 18) under a soaring y = 48 ceiling,
	 * surrounded by an overlooking y = 32 balcony with Jersey barriers and wide descending stairs on 4 sides.
	 */
	private static BlockState sunkenBasin(long seed, Tile tile, Ceiling ceiling, int x, int y, int z, int lx, int lz,
			boolean extra, boolean nearFeature) {
		int basinY = extra ? 18 : 24;
		int topCeil = 48;
		int drop = FLOOR - basinY; // 8 or 14 blocks deep!
		if (y < basinY) return solidConcrete();
		if (y > topCeil) return solidConcrete();
		if (y == topCeil) return state(Level1Blocks.GARAGE_CEILING);

		if (tile == Tile.HALL_WALL || tile == Tile.PERIMETER) {
			return y >= FLOOR ? state(Level1Blocks.GARAGE_WALL) : solidConcrete();
		}
		if (tile == Tile.PILLAR) {
			return pillarAt(seed, x, y, z, inBasin(lx, lz) ? basinY : FLOOR);
		}

		// Outer balcony ring stays at y = 32; central pit (lx = 12..51, lz = 12..51) drops to basinY.
		boolean pit = inBasin(lx, lz) && !nearFeature;
		boolean rim = lx >= 11 && lx <= 52 && lz >= 11 && lz <= 52 && !inBasin(lx, lz);

		// Wide grand staircases on the North (lz = 12..12+drop-1) and South (lz = 51-drop+1..51)
		// along lx in [29..34], stepping smoothly between FLOOR (32) and basinY!
		boolean stairBandX = lx >= 29 && lx <= 34;
		boolean northStair = pit && stairBandX && lz >= 12 && lz < 12 + drop;
		boolean southStair = pit && stairBandX && lz <= 51 && lz > 51 - drop;
		if (northStair) {
			int stepY = FLOOR - 1 - (lz - 12); // descends from 31 down to basinY
			if (y == stepY) return stairs(Direction.NORTH);
			if (y < stepY) return state(Level1Blocks.GARAGE_WALL);
			return y == topCeil - 1 ? hallFixture(ceiling) : air();
		}
		if (southStair) {
			int stepY = FLOOR - 1 - (51 - lz);
			if (y == stepY) return stairs(Direction.SOUTH);
			if (y < stepY) return state(Level1Blocks.GARAGE_WALL);
			return y == topCeil - 1 ? hallFixture(ceiling) : air();
		}

		if (pit) {
			if (y == basinY) {
				// Damp and flooded concrete at the bottom of the deep basin.
				int wet = Math.floorMod(Level1Layout.hash(seed, x, z, 53), 10);
				if (wet < 3) return state(Level1Blocks.PUDDLED_GARAGE_FLOOR);
				if (wet < 6) return state(Level1Blocks.DAMP_GARAGE_FLOOR);
				return state(Level1Blocks.GARAGE_FLOOR);
			}
			// Hanging caged work lamps along pillar beams at y = 38 to cast moody light into the pit.
			int bz = Math.floorMod(z, Level1Layout.BAY_Z);
			if (bz < 2 && y == 38) return state(Level1Blocks.GARAGE_CEILING);
			if (y == topCeil - 1) return hallFixture(ceiling);
			return air();
		}

		// Balcony / outer ring
		if (y < FLOOR) return solidConcrete();
		if (y == FLOOR) return null;
		if (tile == Tile.SHELF && y <= FLOOR + 2) return null;

		// Balcony railings along the basin rim (leaving gaps at the north/south stair entrances).
		if (rim && y == FLOOR + 1 && !stairBandX) {
			boolean alongX = lz == 11 || lz == 52;
			return barrier(alongX ? Direction.Axis.X : Direction.Axis.Z);
		}
		if (y == topCeil - 1) return hallFixture(ceiling);
		return air();
	}

	/**
	 * High main hall (y = 42) over a two-tier underground maintenance tunnel network:
	 * Upper sub-level at y = 22 (ceiling y = 26) and optional deep drainage sump at y = 14 (ceiling y = 19),
	 * connected to the main floor by pillar-safe descending stairwells.
	 */
	private static BlockState sublevelTunnels(long seed, Tile tile, Ceiling ceiling, int dx, int dz, int x, int y, int z,
			int lx, int lz, boolean extra, boolean nearFeature) {
		int topCeil = 42;
		int sub1Floor = 22, sub1Ceil = 26;
		int sub2Floor = 14, sub2Ceil = 19;
		int minY = extra ? sub2Floor : sub1Floor;

		if (y < minY) return solidConcrete();
		if (y > topCeil) return solidConcrete();
		if (y == topCeil) return state(Level1Blocks.GARAGE_CEILING);

		// Main hall (y >= 32): high 10-block ceiling, normal floor & shelves.
		if (y >= FLOOR) {
			if (tile == Tile.HALL_WALL || tile == Tile.PERIMETER) return state(Level1Blocks.GARAGE_WALL);
			if (tile == Tile.PILLAR) return y == FLOOR ? null : pillarAt(seed, x, y, z, FLOOR);

			// Descending stairwell opening from 1F (32) down to Sub1 (22) at lx in [12..20], bx in [4, 5], lz in [18..27].
			int bx = Math.floorMod(x, Level1Layout.BAY_X);
			if (!nearFeature && lx >= 12 && lx <= 20 && (bx == 4 || bx == 5) && lz >= 18 && lz <= 27) {
				if (y == FLOOR) return air();
			}
			if (y == FLOOR) return null;
			if (tile == Tile.SHELF && y <= FLOOR + 2) return null;
			if (y == topCeil - 1) return hallFixture(ceiling);
			return air();
		}

		// Below main floor (y < 32):
		if (isClosedDeckEdge(seed, dx, dz, lx, lz, Profile.SUBLEVEL_TUNNELS)) {
			return solidConcrete();
		}

		// Stairwell 1: 1F (32) down to Sub1 (22) along +Z at lx in [12..20], bx in [4, 5], lz in [18..27]
		int bx = Math.floorMod(x, Level1Layout.BAY_X);
		if (!nearFeature && lx >= 12 && lx <= 20 && (bx == 4 || bx == 5) && lz >= 18 && lz <= 27 && y >= sub1Floor) {
			int stepY = FLOOR - 1 - (lz - 18); // 31 down to 22
			if (y == stepY) return stairs(Direction.NORTH);
			if (y > stepY && y <= Math.min(FLOOR - 1, stepY + 4)) return air();
			if (y < stepY) return state(Level1Blocks.GARAGE_WALL);
		}

		// Stairwell 2: Sub1 (22) down to Sub2 (14) along +Z at lx in [42..50], bx in [4, 5], lz in [34..41]
		if (extra && lx >= 42 && lx <= 50 && (bx == 4 || bx == 5) && lz >= 34 && lz <= 41 && y >= sub2Floor && y <= sub1Floor + 3) {
			int stepY = sub1Floor - 1 - (lz - 34); // 21 down to 14
			if (y == stepY) return stairs(Direction.NORTH);
			if (y > stepY && y <= Math.min(sub1Ceil - 1, stepY + 4)) return air();
			if (y < stepY) return state(Level1Blocks.GARAGE_WALL);
		}

		// Sub-level 1 tunnel grid (y = 22..26): 5-block-wide crisscrossing maintenance corridors every 16 blocks.
		boolean inSub1Tunnel = (lx >= 10 && lx <= 54 && (lz % 16 >= 6 && lz % 16 <= 10))
				|| (lz >= 10 && lz <= 54 && (lx % 16 >= 6 && lx % 16 <= 10))
				|| (lx >= 12 && lx <= 20 && lz >= 17 && lz <= 29)
				|| (extra && lx >= 41 && lx <= 51 && lz >= 33 && lz <= 43);
		if (y >= sub1Floor && y <= sub1Ceil) {
			if (!inSub1Tunnel) return solidConcrete();
			if (y == sub1Floor) return deckFloor(tile, true);
			if (y == sub1Ceil) return state(Level1Blocks.GARAGE_CEILING);
			if (y == sub1Ceil - 1) {
				if (ceiling == Ceiling.LAMP || ceiling == Ceiling.DEAD_LAMP) return hallFixture(ceiling);
				if (lz % 16 == 8) return state(Level1Blocks.CEILING_PIPE);
			}
			return air();
		}

		// Sub-level 2 deep drainage chamber (y = 14..19) when extra=true
		if (extra && y >= sub2Floor && y <= sub2Ceil) {
			boolean inSub2 = (lx >= 18 && lx <= 48 && lz >= 18 && lz <= 48)
					|| (lx >= 41 && lx <= 51 && lz >= 33 && lz <= 43);
			if (!inSub2) return solidConcrete();
			if (tile == Tile.PILLAR) return pillarAt(seed, x, y, z, sub2Floor);
			if (y == sub2Floor) {
				int wet = Math.floorMod(Level1Layout.hash(seed, x, z, 59), 10);
				return state(wet < 5 ? Level1Blocks.PUDDLED_GARAGE_FLOOR : Level1Blocks.DAMP_GARAGE_FLOOR);
			}
			if (y == sub2Ceil) return state(Level1Blocks.GARAGE_CEILING);
			if (y == sub2Ceil - 1) {
				if (ceiling == Ceiling.LAMP || ceiling == Ceiling.DEAD_LAMP) return hallFixture(ceiling);
				if (Math.floorMod(z, Level1Layout.BAY_Z) == 8) return state(Level1Blocks.LEAKING_CEILING_PIPE);
			}
			return air();
		}

		return solidConcrete();
	}

	/** Returns false where the main y=32 floor has been carved away (sunken basins, landmarks, sublevel stairs). */
	static boolean hasMainFloor(long seed, int x, int z) {
		int dx = Math.floorDiv(x, D), dz = Math.floorDiv(z, D);
		if (Level1Landmarks.landmark(seed, dx, dz) != Level1Landmarks.Landmark.NONE) return false;
		Profile p = profile(seed, dx, dz);
		int lx = Math.floorMod(x, D), lz = Math.floorMod(z, D);
		if (p == Profile.SUNKEN_BASIN && inBasin(lx, lz)) return false;
		if (p == Profile.SUBLEVEL_TUNNELS && lx >= 12 && lx <= 20 && lz >= 18 && lz <= 27) return false;
		Level1SectorArchitecture.Variant v = Level1SectorArchitecture.variant(seed, dx, dz);
		if (v == Level1SectorArchitecture.Variant.TAILCHASE_EXCAVATION_PITS
				&& ((lx >= 12 && lx <= 26 && lz >= 14 && lz <= 28) || (lx >= 36 && lx <= 50 && lz >= 34 && lz <= 48))) {
			return false;
		}
		if (v == Level1SectorArchitecture.Variant.GARDEN_SUNKEN_POND && Math.hypot(lx - 32, lz - 32) <= 18) {
			return false;
		}
		return true;
	}

	private static boolean inBasin(int lx, int lz) {
		return lx >= 12 && lx <= 51 && lz >= 12 && lz <= 51;
	}

	/** Whether a shelf stands anywhere along a staircase's run, which would cut it in two. */
	private static boolean shelfInRun(long seed, int x, int z, int lz, int from, int to) {
		for (int l = from; l <= to; l++) {
			if (isWarehouseShelf(seed, x, z - lz + l)) return true;
		}
		return false;
	}

	private static boolean isWarehouseShelf(long seed, int x, int z) {
		return Level1Layout.kindAt(seed, x, z) == Kind.WAREHOUSE && Level1Layout.tile(seed, x, z) == Tile.SHELF;
	}

	private static boolean isClosedDeckEdge(long seed, int dx, int dz, int lx, int lz, Profile same) {
		if (lx < 2 && profile(seed, dx - 1, dz) != same) return true;
		if (lx >= D - 2 && profile(seed, dx + 1, dz) != same) return true;
		if (lz < 2 && profile(seed, dx, dz - 1) != same) return true;
		if (lz >= D - 2 && profile(seed, dx, dz + 1) != same) return true;
		return false;
	}

	/** Keeps exits and hall props surrounded by a safe concrete buffer on the main floor. */
	static boolean isNearExitOrProp(long seed, int dx, int dz, int x, int z) {
		HallPlan plan = HallPlan.of(seed, dx, dz);
		for (HallPlan.Placed placed : plan.placed()) {
			int[] spot = placed.spot();
			if (Math.abs(x - spot[0]) <= 5 && Math.abs(z - spot[1]) <= 6) return true;
		}
		for (HallPlan.PlacedProp prop : plan.props()) {
			int[] spot = prop.spot();
			if (Math.abs(x - spot[0]) <= 5 && Math.abs(z - spot[1]) <= 6) return true;
		}
		return false;
	}

	private static BlockState pillarAt(long seed, int x, int y, int z, int deckFloor) {
		if (y == deckFloor + 1) return state(Level1Blocks.GARAGE_PILLAR_BASE);
		if (y == deckFloor + 2 && Level1Layout.kindAt(seed, x, z) == Kind.PARKING) {
			int bx = Math.floorMod(x, Level1Layout.BAY_X), bz = Math.floorMod(z, Level1Layout.BAY_Z);
			if (bx != bz) return state(Level1Blocks.GARAGE_PILLAR_MARKED);
		}
		return state(Level1Blocks.GARAGE_PILLAR);
	}

	private static BlockState deckFloor(Tile tile, boolean damp) {
		if (damp) {
			return state(tile == Tile.PUDDLE ? Level1Blocks.PUDDLED_GARAGE_FLOOR : Level1Blocks.DAMP_GARAGE_FLOOR);
		}
		return state(tile == Tile.LINE ? Level1Blocks.GARAGE_FLOOR_LINE : Level1Blocks.GARAGE_FLOOR);
	}

	private static BlockState hallFixture(Ceiling ceiling) {
		return switch (ceiling) {
			case BEAM -> state(Level1Blocks.GARAGE_CEILING);
			case LAMP -> state(Level1Blocks.GARAGE_LIGHT);
			case DEAD_LAMP -> state(Level1Blocks.BROKEN_GARAGE_LIGHT);
			case PIPE -> state(Level1Blocks.CEILING_PIPE);
			case LEAK -> state(Level1Blocks.LEAKING_CEILING_PIPE);
			default -> air();
		};
	}

	private static BlockState barrier(Direction.Axis axis) {
		return Level1Blocks.JERSEY_BARRIER.defaultBlockState().setValue(AxisShapedBlock.AXIS, axis);
	}

	private static BlockState stairs(Direction facing) {
		return Level1Blocks.CONCRETE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing);
	}

	private static BlockState solidConcrete() {
		// Level 1's own concrete, not vanilla's flat grey: cliff walls and tunnel walls show it (and sectors restyle it).
		return state(Level1Blocks.GARAGE_WALL);
	}

	private static BlockState state(Block block) {
		return block.defaultBlockState();
	}

	private static BlockState air() {
		return Blocks.AIR.defaultBlockState();
	}
}
