package dev.noclip.backrooms.level1;

/**
 * The two settlements players' notes place in Level 1, each filling the middle of a hall district:
 * the M.E.G. outpost "First Light" in a parking hall, and the colonists' camp "Blink" in a warehouse.
 * A ring of concrete barriers with a gap in the middle of every side encloses the compound; everything
 * inside stands apart from everything else, so the floor between stays one connected space.
 * Coordinates here are local to the district (0..63).
 */
final class BasePlan {
	static final int MIN_X = 14, MAX_X = 49, MIN_Z = 16, MAX_Z = 47;
	private static final int GAP_FROM = 30, GAP_TO = 32;

	enum Piece {
		/** Barriers along the ring; X and Z are the directions they run. */
		BARRIER_X, BARRIER_Z,
		/** A barrier with the settlement's sign on top, beside each way in. */
		SIGN_X, SIGN_Z,
		FLOODLIGHT, COT, LOCKER, DESK, COMPUTER_DESK,
		FIRE, BEDROLL, TENT_WALL, TENT_FLOOR, TENT_BEDROLL,
		COUNTER_TRADE, COUNTER_TOM, CLINIC_BED, SIGNUP_BOOTH
	}

	private static final int[][] FLOODLIGHTS = {{16, 18}, {47, 18}, {16, 45}, {47, 45}, {24, 36}, {40, 26}};
	private static final int[][] FIRES = {{24, 26}, {40, 38}};
	private static final int[][] BEDROLLS = {{22, 26}, {26, 26}, {24, 24}, {24, 28}, {38, 38}, {42, 38}, {40, 36}, {40, 40}};
	private static final int[][] TENTS = {{18, 19}, {43, 42}};
	private static final int[][] STASHES = {{34, 20}, {36, 20}};

	private BasePlan() {
	}

	static boolean inside(int lx, int lz) {
		return lx >= MIN_X && lx <= MAX_X && lz >= MIN_Z && lz <= MAX_Z;
	}

	/** What stands at (lx, lz) of a district holding this settlement, or null for open floor. */
	static Piece piece(Level1Layout.Base base, int lx, int lz) {
		if (!inside(lx, lz)) return null;
		boolean side = lx == MIN_X || lx == MAX_X, end = lz == MIN_Z || lz == MAX_Z;
		if (side || end) {
			int along = end ? lx : lz;
			if (along >= GAP_FROM && along <= GAP_TO) return null;
			boolean sign = along == GAP_FROM - 1 || along == GAP_TO + 1;
			return end ? (sign ? Piece.SIGN_X : Piece.BARRIER_X) : (sign ? Piece.SIGN_Z : Piece.BARRIER_Z);
		}
		return switch (base) {
			case FIRST_LIGHT -> outpost(lx, lz);
			case BLINK -> camp(lx, lz);
			case TRADERS_KEEP -> keep(lx, lz);
			case HIPPOCRATES -> clinic(lx, lz);
			case TOMS -> diner(lx, lz);
			case SIGNUP -> signup(lx, lz);
			case CORNUCOPIA -> fort(lx, lz);
			case NONE -> null;
		};
	}

	/** First Light: floodlights that never go out, two rows of cots, lockers of supplies, a command desk. */
	private static Piece outpost(int lx, int lz) {
		if (at(FLOODLIGHTS, lx, lz)) return Piece.FLOODLIGHT;
		if ((lz == 20 || lz == 23) && lx >= 18 && lx <= 26 && lx % 2 == 0) return Piece.COT;
		if (lz == 44 && lx >= 34 && lx <= 44 && lx % 2 == 0) return Piece.LOCKER;
		if (lz == 23 && lx == 38) return Piece.COMPUTER_DESK;
		if (lz == 23 && lx == 40) return Piece.DESK;
		return null;
	}

	/** The Trader's Keep: three barter counters, a vault of lockers, a desk with a computer. */
	private static Piece keep(int lx, int lz) {
		if (at(FLOODLIGHTS, lx, lz)) return Piece.FLOODLIGHT;
		if (lz == 24 && lx >= 22 && lx <= 26 && lx % 2 == 0) return Piece.COUNTER_TRADE;
		if (lz == 44 && lx >= 34 && lx <= 44 && lx % 2 == 0) return Piece.LOCKER;
		if (lz == 23 && lx == 38) return Piece.COMPUTER_DESK;
		if (lz == 23 && lx == 40) return Piece.DESK;
		return null;
	}

	/** Hippocrates-1: a row of clinic beds, tables, a small store of medicine. */
	private static Piece clinic(int lx, int lz) {
		if (at(FLOODLIGHTS, lx, lz)) return Piece.FLOODLIGHT;
		if (lz == 22 && lx >= 20 && lx <= 28 && lx % 2 == 0) return Piece.CLINIC_BED;
		if (lz == 26 && (lx == 20 || lx == 24 || lx == 28)) return Piece.DESK;
		if (lz == 44 && (lx == 34 || lx == 36)) return Piece.LOCKER;
		return null;
	}

	/** Tom's Restaurant: tables for the diners, a counter along the back, a stove. */
	private static Piece diner(int lx, int lz) {
		if (at(FLOODLIGHTS, lx, lz)) return Piece.FLOODLIGHT;
		if ((lz == 22 || lz == 26) && (lx == 20 || lx == 24 || lx == 28)) return Piece.DESK;
		if (lz == 22 && lx >= 38 && lx <= 42 && lx % 2 == 0) return Piece.COUNTER_TOM;
		if (lx == 44 && lz == 40) return Piece.FIRE;
		return null;
	}

	/** A Speednoclipper sign-up point: a booth with a desk on each side, two tents. */
	private static Piece signup(int lx, int lz) {
		if (lz == 24 && lx == 31) return Piece.SIGNUP_BOOTH;
		if (lz == 24 && (lx == 29 || lx == 33)) return Piece.DESK;
		return tents(lx, lz);
	}

	/** Cornucopia: tents, fires, a stash, floodlights on the corners. */
	private static Piece fort(int lx, int lz) {
		if (at(FLOODLIGHTS, lx, lz) && lx != 24 && lx != 40) return Piece.FLOODLIGHT;
		if (at(FIRES, lx, lz)) return Piece.FIRE;
		if (at(STASHES, lx, lz)) return Piece.LOCKER;
		return tents(lx, lz);
	}

	private static Piece tents(int lx, int lz) {
		for (int[] tent : TENTS) {
			int u = lx - tent[0], v = lz - tent[1];
			if (u < 0 || u > 2 || v < 0 || v > 2) continue;
			if (v == 0 || u != 1) return Piece.TENT_WALL;
			return Piece.TENT_FLOOR;
		}
		return null;
	}

	/** Blink: fires in drums with bedrolls round them, two tarp tents, a stash. */
	private static Piece camp(int lx, int lz) {
		if (at(FIRES, lx, lz)) return Piece.FIRE;
		if (at(BEDROLLS, lx, lz)) return Piece.BEDROLL;
		if (at(STASHES, lx, lz)) return Piece.LOCKER;
		for (int[] tent : TENTS) {
			int u = lx - tent[0], v = lz - tent[1];
			if (u < 0 || u > 2 || v < 0 || v > 2) continue;
			// Open to the south: a back wall, two side walls, a bedroll inside and the doorway.
			if (v == 0 || u != 1) return Piece.TENT_WALL;
			return v == 1 ? Piece.TENT_BEDROLL : Piece.TENT_FLOOR;
		}
		return null;
	}

	private static boolean at(int[][] spots, int lx, int lz) {
		for (int[] spot : spots) {
			if (spot[0] == lx && spot[1] == lz) return true;
		}
		return false;
	}
}
