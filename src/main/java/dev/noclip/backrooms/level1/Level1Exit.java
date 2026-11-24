package dev.noclip.backrooms.level1;

/**
 * The ways out of the parking structure to other levels, after the Level 1 exits players have charted:
 * each kind of thing in the garage leads to one level.
 *
 * The lower the level an exit leads to, the harder it is to find: rarer, and less conspicuous.
 * The stairs back up to Level 0 are the rarest; a cracked pillar into 960 turns up in most parking halls.
 * Levels that do not exist yet are still placed; their exits say so and stay shut (see LevelExits).
 */
public enum Level1Exit {
	// Ordered by level, so also from hardest to easiest to find.
	STAIRS_UP("0", Shape.STAIRS_UP),
	CELLAR_HATCH("1.01", Shape.HATCH),
	DOOR_FRAME("1.1", Shape.FRAME),
	CONCRETE_DOOR("1.2", Shape.CORE_DOOR),
	ROOT_TWO("\u221a2", Shape.CORE_DOOR),
	INVERTED_WALL("1.5", Shape.CORE_PAINTING),
	STAIRS_DOWN("2", Shape.STAIRS_DOWN),
	WHITE_DOOR("4.3", Shape.CORE_DOOR),
	ATTIC_HATCH("19", Shape.CORE_PAINTING),
	GLASS_DOOR("22", Shape.CORE_DOOR),
	REINFORCED_DOOR("54", Shape.CORE_DOOR),
	OUTDOOR_GAP("128", Shape.CORE_PAINTING),
	PAINTING("143", Shape.CORE_PAINTING),
	SYMBOL_DOOR("154", Shape.CORE_DOOR),
	FROZEN_DOOR("159", Shape.CORE_DOOR),
	OUTLETS("188", Shape.CORE_OUTLETS),
	AUTOMATIC_DOOR("197", Shape.CORE_DOOR),
	BUNKER_DOOR("218", Shape.CORE_DOOR),
	PICKAXE_DOOR("389", Shape.CORE_DOOR),
	COLD_DOOR("740", Shape.CORE_DOOR),
	WALL_VENT("800", Shape.CORE_OUTLETS),
	CRACKED_PILLAR("960", Shape.PILLAR),
	ELEVATOR_DOOR("998", Shape.CORE_DOOR),
	WHITE_WOOD_DOOR("998.2", Shape.CORE_DOOR);

	/** How an exit sits in a parking bay; see HallPlan for the footprints. */
	enum Shape {
		/** A concrete service room in the middle of a bay, with the exit in one of its faces. */
		CORE_DOOR, CORE_PAINTING, CORE_OUTLETS,
		/** One column of the bay's pillar, cracked open. */
		PILLAR,
		/** A hatch flush with the floor at the edge of the aisle. */
		HATCH,
		/** A door frame standing on its own in the aisle. */
		FRAME,
		/** Stairs down through a hole in the floor into a dark passage. */
		STAIRS_DOWN,
		/** Stairs up through a hole in the ceiling. */
		STAIRS_UP;

		boolean core() {
			return this == CORE_DOOR || this == CORE_PAINTING || this == CORE_OUTLETS;
		}

		/** Warehouses have racks where the service rooms and stairwells would go. */
		boolean fitsWarehouse() {
			return this == PILLAR || this == HATCH || this == FRAME;
		}
	}

	public final String level;
	final Shape shape;
	/** The chance that one parking hall district holds one of these. */
	final double chance;

	Level1Exit(String level, Shape shape) {
		this.level = level;
		this.shape = shape;
		this.chance = findability(number(level));
	}

	/** The number of a level, which for the square-root-of-two level is its value. */
	public static double number(String level) {
		return level.startsWith("\u221a") ? Math.sqrt(Double.parseDouble(level.substring(1))) : Double.parseDouble(level);
	}

	/** Lower levels are harder to find: the chance per district grows with the logarithm of the level number. */
	static double findability(double level) {
		return 0.03 + 0.09 * Math.log10(1 + level);
	}
}
