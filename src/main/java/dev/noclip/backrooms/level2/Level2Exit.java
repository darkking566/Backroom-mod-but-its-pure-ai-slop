package dev.noclip.backrooms.level2;

/**
 * The ways out of Level 2, after the Wikidot Level 2 article ("Abandoned Utility Halls"): each kind of door in the
 * tunnel walls leads to one level. The standard ways back up to 1 and on to 3 and 4 are plain steel doors that
 * earlier wanderers marked with spray paint; the rest are odd doors that have no business being down here.
 *
 * As in Level 1, the lower the level a door leads to, the harder it is to find. Levels that do not exist yet
 * are still placed; their doors say so and stay shut (see core.LevelExits).
 */
public enum Level2Exit {
	// Ordered by level, so also from hardest to easiest to find.
	MARKED_1("1", 2, Place.WALL),
	MARKED_3("3", 2, Place.WALL),
	MARKED_4("4", 2, Place.WALL),
	/** Plywood doors. */
	PLYWOOD("31", 2, Place.WALL),
	/** Oversized floor-to-ceiling doors engraved "70". */
	ENGRAVED_GIANT("70", 4, Place.WALL),
	/** In the unreasonably hot stretches: a glowing boiler hatch. */
	BOILER_HATCH("127", 2, Place.HOT),
	/** Aged wooden doors. */
	AGED_WOOD("137", 2, Place.WALL),
	/** A plain metal door. */
	METAL("141", 2, Place.WALL),
	/** Blue doors. */
	BLUE("175", 2, Place.WALL),
	/** Oversized floor-to-ceiling doors in rainbow paint. */
	RAINBOW_GIANT("283", 4, Place.WALL),
	/** A door with a Macchina blade stuck in it, at the end of a run of pipes with blue pulsing lights. */
	BLADE("811", 2, Place.WALL),
	/** Wooden doors with a broken doorknob. */
	BROKEN_KNOB("916", 2, Place.WALL),
	/** Pink doors. */
	PINK("997", 2, Place.WALL),
	/** Elevator-like doors. */
	ELEVATOR("998", 2, Place.WALL);

	enum Place {
		/** Any straight stretch of tunnel. */
		WALL,
		/** Only inside a hot zone. */
		HOT
	}

	public final String level;
	/** Blocks tall, from the floor up. */
	final int height;
	final Place place;
	/** The chance that one 80x80 region holds one of these. */
	final double chance;

	Level2Exit(String level, int height, Place place) {
		this.level = level;
		this.height = height;
		this.place = place;
		this.chance = findability(Double.parseDouble(level));
	}

	/** The same rule as Level 1: the chance per region grows with the logarithm of the level number. */
	static double findability(double level) {
		return 0.06 + 0.18 * Math.log10(1 + level);
	}
}
