package dev.noclip.backrooms.level3;

import dev.noclip.backrooms.level3.Level3Plan.Hall;

/**
 * What stands inside Level 3's halls, after the Wikidot article's Rooms section and its photos: generator rooms
 * thick with switchgear around the generator, brass boilers over puddles of sludge, assembly lines of conveyor
 * belts under hoists, the sanctum's pillars around an angel on a pedestal, a car in a room, and a Base Gamma
 * outpost. Positions are inside the hall's walls, from (0, 0); h is the height over the floor, 1 to HEIGHT.
 */
final class Level3Halls {
	/** Headroom in a hall. */
	static final int HEIGHT = 6;

	enum Thing {
		AIR, LIGHT, FLICKERING_LIGHT, BULB, BRIGHT_LIGHT, BLUE_LIGHT, DEAD_LIGHT, CABLE_TRAY, SLUDGE_PIPE, WIRE, SLUDGE,
		GENERATOR, SWITCHGEAR, BOILER, BOILER_VALVE, CONVEYOR, CRATE, PRESS, CHAIN, HOOK,
		PILLAR_BASE, PILLAR, PILLAR_TOP, PEDESTAL, ANGEL, CAR, COT, SUPPLY_CRATE, TABLE,
		CAMERA, JERRY_MURAL, UEC_APPARATUS, SALVAGE_BENCH, WORKSHOP_BENCH, ARMORY_CRATE, VAULT_CHEST, LECTERN, BARS
	}

	/** One block's worth: what, facing which way (plan direction), and which part of something bigger. */
	record Piece(Thing thing, int dir, int part) {
		static final Piece AIR = new Piece(Thing.AIR, 0, 0);

		static Piece of(Thing thing) {
			return new Piece(thing, 0, 0);
		}
	}

	private Level3Halls() {
	}

	static int width(Hall hall) {
		return hall.w() * Level3Plan.CELL - 2;
	}

	static int depth(Hall hall) {
		return hall.h() * Level3Plan.CELL - 2;
	}

	/** x, z are world coordinates, for chance; gap is whether this column is near a way in, which stays clear. */
	static Piece at(long seed, Hall hall, int u, int v, int h, int x, int z, boolean gap) {
		int w = width(hall), d = depth(hall);
		return switch (hall.kind()) {
			case GENERATOR -> generator(seed, u, v, h, w, d, x, z, gap);
			case BOILER -> boiler(seed, u, v, h, w, d, x, z, gap);
			case ASSEMBLY -> assembly(seed, u, v, h, w, d, x, z, gap);
			case SANCTUM -> sanctum(seed, u, v, h, w, d, x, z, gap);
			case CAR -> car(u, v, h, w, d);
			case BASE -> base(seed, u, v, h, w, d, x, z, gap);
			case BNTG_STORAGE -> bntgStorage(u, v, h, w, d, gap);
			case BLUE_SALVATION -> blueSalvation(seed, u, v, h, w, d, x, z, gap);
			case SCYLLA_COLONY -> scyllaColony(u, v, h, w, d, gap);
			// Gamma is laid out by GammaLayout; the generator never asks here.
			case GAMMA -> Piece.AIR;
		};
	}

	private static Piece ceiling(int u, int v, Thing light) {
		return u % 4 == 1 && v % 4 == 3 ? Piece.of(light) : Piece.AIR;
	}

	private static Piece generator(long seed, int u, int v, int h, int w, int d, int x, int z, boolean gap) {
		boolean core = u >= w / 2 - 1 && u <= w / 2 && v >= d / 2 - 1 && v <= d / 2;
		if (core) return h <= 2 ? new Piece(Thing.GENERATOR, 0, h - 1) : h == HEIGHT ? Piece.of(Thing.CABLE_TRAY) : Piece.AIR;
		if ((v == 0 || v == d - 1) && u % 2 == 1 && u < w - 1 && !gap) {
			return h <= 2 ? new Piece(Thing.SWITCHGEAR, v == 0 ? Level3Plan.SOUTH : Level3Plan.NORTH, h - 1) : h == HEIGHT ? Piece.of(Thing.CABLE_TRAY) : Piece.AIR;
		}
		if (h == HEIGHT) return v % 3 == 1 ? Piece.of(Thing.CABLE_TRAY) : ceiling(u, v, Level3Layout.roll(seed, x, 0, z) < 25 ? Thing.FLICKERING_LIGHT : Thing.LIGHT);
		boolean aroundCore = u >= w / 2 - 2 && u <= w / 2 + 1 && v >= d / 2 - 2 && v <= d / 2 + 1;
		if (h == 1 && aroundCore && Level3Layout.roll(seed, x, 1, z) < 22) return Piece.of(Thing.WIRE);
		return Piece.AIR;
	}

	private static Piece boiler(long seed, int u, int v, int h, int w, int d, int x, int z, boolean gap) {
		boolean first = u >= 2 && u <= 3 && v >= 2 && v <= 3, second = u >= w - 4 && u <= w - 3 && v >= d - 4 && v <= d - 3;
		if (first || second) {
			if (h <= 2) return new Piece(Thing.BOILER, 0, h - 1);
			boolean valve = u == (first ? 2 : w - 4) && v == (first ? 2 : d - 4);
			if (h == 3 && valve) return Piece.of(Thing.BOILER_VALVE);
			return h == HEIGHT ? Piece.of(Thing.SLUDGE_PIPE) : Piece.AIR;
		}
		if (h == HEIGHT) return v == d / 2 ? Piece.of(Thing.SLUDGE_PIPE) : ceiling(u, v, Thing.BULB);
		if (h == 1 && Level3Layout.roll(seed, x, 2, z) < 16 && !gap) return Piece.of(Thing.SLUDGE);
		return Piece.AIR;
	}

	private static Piece assembly(long seed, int u, int v, int h, int w, int d, int x, int z, boolean gap) {
		boolean belt = (v == 3 || v == 8 || v == 12) && u >= 2 && u <= w - 3;
		boolean end = (v == 3 || v == 8 || v == 12) && (u == 1 || u == w - 2);
		if (belt && !gap) {
			if (h == 1) return new Piece(Thing.CONVEYOR, Level3Plan.EAST, 0);
			if (h == 2 && u % 3 == 0) return Piece.of(Thing.CRATE);
		}
		if (end && !gap && h <= 2) return new Piece(Thing.PRESS, u == 1 ? Level3Plan.EAST : Level3Plan.WEST, h - 1);
		boolean hoist = (v == 5 || v == 10) && (u == 4 || u == 8 || u == 12);
		if (hoist && h >= 4) return Piece.of(h == 4 ? Thing.HOOK : Thing.CHAIN);
		if (h == HEIGHT) return u % 4 == 2 && v % 4 == 1 ? Piece.of(Thing.BRIGHT_LIGHT) : Piece.AIR;
		return Piece.AIR;
	}

	private static Piece sanctum(long seed, int u, int v, int h, int w, int d, int x, int z, boolean gap) {
		int cu = w / 2 - 1, cv = d / 2 - 1;
		if (u == cu && v == cv) {
			return switch (h) {
				case 1 -> Piece.of(Thing.PEDESTAL);
				case 2 -> new Piece(Thing.ANGEL, Level3Plan.SOUTH, 0);
				case 3 -> new Piece(Thing.ANGEL, Level3Plan.SOUTH, 1);
				default -> Piece.AIR;
			};
		}
		// Two rows of pillars down the sides, like a nave.
		boolean pillar = (u == 3 || u == w - 4) && (v == 2 || v == d - 3 || v == d / 2 - 3 || v == d / 2 + 2) && !gap;
		if (pillar) return Piece.of(h == 1 ? Thing.PILLAR_BASE : h == HEIGHT ? Thing.PILLAR_TOP : Thing.PILLAR);
		if (h == HEIGHT && (u == 1 || u == w - 2) && (v == 1 || v == d - 2)) return Piece.of(Thing.BULB);
		return Piece.AIR;
	}

	/** A car, two blocks wide and three long, in the middle of the room, its nose to the room's short wall. */
	private static Piece car(int u, int v, int h, int w, int d) {
		boolean alongZ = d > w;
		int across = alongZ ? u - (w / 2 - 1) : v - (d / 2 - 1);
		int along = alongZ ? v - (d / 2 - 1) : u - (w / 2 - 1);
		if (across < 0 || across > 1 || along < 0 || along > 2) return h == 4 && across == 0 && along == -2 ? Piece.of(Thing.BULB) : Piece.AIR;
		int facing = alongZ ? Level3Plan.NORTH : Level3Plan.WEST;
		// Parts: 0-5 the body, front to back and left to right; 6-7 the cabin over the middle row.
		if (h == 1) return new Piece(Thing.CAR, facing, along * 2 + across);
		if (h == 2 && along == 1) return new Piece(Thing.CAR, facing, 6 + across);
		return Piece.AIR;
	}

	private static Piece base(long seed, int u, int v, int h, int w, int d, int x, int z, boolean gap) {
		if (h == HEIGHT) return u % 4 == 1 && v % 4 == 1 ? Piece.of(Thing.BRIGHT_LIGHT) : Piece.AIR;
		if (h == 4 && !gap && ((u == 0 && v == 1) || (u == w - 1 && v == d - 2))) {
			return new Piece(Thing.CAMERA, u == 0 ? Level3Plan.EAST : Level3Plan.WEST, 0);
		}
		if (gap) return Piece.AIR;
		if (h <= 2 && (u == 2 || u == w - 3) && (v == 1 || v == d - 2)) return Piece.of(Thing.BARS);
		if (h != 1) return Piece.AIR;
		if (u == w - 1 && v % 3 == 1 && v < d - 1) return new Piece(Thing.COT, Level3Plan.WEST, 0);
		if (u <= 2 && v >= d - 3) return Piece.of(Thing.SUPPLY_CRATE);
		if (u == 0 && v == 1) return new Piece(Thing.SALVAGE_BENCH, Level3Plan.EAST, 0);
		if (u == 0 && v == 3) return new Piece(Thing.WORKSHOP_BENCH, Level3Plan.EAST, 0);
		if (u >= w / 2 - 1 && u <= w / 2 && v == d / 2 + 2) return Piece.of(Thing.TABLE);
		return Piece.AIR;
	}

	private static Piece bntgStorage(int u, int v, int h, int w, int d, boolean gap) {
		if (h == HEIGHT) return u % 4 == 1 && v % 3 == 1 ? Piece.of(Thing.BRIGHT_LIGHT) : Piece.AIR;
		if (gap) return Piece.AIR;
		if ((v == 0 || v == d - 1) && u >= 1 && u <= w - 2 && u % 2 == 1) {
			if (h == 1) return Piece.of(u % 4 == 1 ? Thing.SUPPLY_CRATE : Thing.CRATE);
			if (h == 2 && u % 4 == 3) return Piece.of(Thing.CRATE);
		}
		if (h == 1 && u >= w / 2 - 1 && u <= w / 2 && v == d / 2) return Piece.of(Thing.TABLE);
		if (h == 1 && u == w - 2 && v == d / 2) return new Piece(Thing.LECTERN, Level3Plan.WEST, 0);
		return Piece.AIR;
	}

	private static Piece blueSalvation(long seed, int u, int v, int h, int w, int d, int x, int z, boolean gap) {
		if (h == HEIGHT) return v % 3 == 1 ? Piece.of(Thing.CABLE_TRAY) : ceiling(u, v, Thing.BLUE_LIGHT);
		if (h == 2 && !gap) {
			if (v == 0 && u % 2 == 1) return new Piece(Thing.JERRY_MURAL, Level3Plan.SOUTH, 0);
			if (v == d - 1 && u % 2 == 1) return new Piece(Thing.JERRY_MURAL, Level3Plan.NORTH, 0);
			if (u == 0 && v % 2 == 1) return new Piece(Thing.JERRY_MURAL, Level3Plan.EAST, 0);
			if (u == w - 1 && v % 2 == 1) return new Piece(Thing.JERRY_MURAL, Level3Plan.WEST, 0);
		}
		if (gap || h != 1) return Piece.AIR;
		if (u >= w / 2 - 1 && u <= w / 2 && v >= d / 2 - 1 && v <= d / 2) return new Piece(Thing.GENERATOR, 0, 0);
		if (u == 1 && v == 1) return Piece.of(Thing.TABLE);
		if (u == w - 2 && v == d - 2) return Piece.of(Thing.CRATE);
		if (Level3Layout.roll(seed, x, 4, z) < 12) return Piece.of(Thing.WIRE);
		return Piece.AIR;
	}

	private static Piece scyllaColony(int u, int v, int h, int w, int d, boolean gap) {
		if (h == HEIGHT) {
			if (u == 1 && v == 1) return Piece.of(Thing.BULB);
			return ceiling(u, v, Thing.DEAD_LIGHT);
		}
		if (gap) return Piece.AIR;
		if (h <= 2 && u == w / 2 && (v == 1 || v == d - 2)) return Piece.of(Thing.BARS);
		if (h != 1) return Piece.AIR;
		if ((u == 2 || u == w - 3) && v == d / 2) return new Piece(Thing.UEC_APPARATUS, u == 2 ? Level3Plan.EAST : Level3Plan.WEST, 0);
		if (u == w - 2 && v == 1) return new Piece(Thing.ARMORY_CRATE, Level3Plan.WEST, 0);
		if (u == w - 2 && v == d - 2) return new Piece(Thing.VAULT_CHEST, Level3Plan.WEST, 0);
		return Piece.AIR;
	}
}
