package dev.noclip.backrooms.level4;

/**
 * Ways out of Level 4 ("Abandoned Office", Backrooms Chinese Wiki + sublevel/branch links):
 * - Permanent exits: elevator back to Level 3, and office stairwells to Level 5 & Level 6 near M.E.G. Base Omega.
 * - Vanishing wild exits: stairwells and anomalous doors scattered in the outer office halls that disappear if you
 *   look away for too long ("once you lose sight of an exit, they may vanish unexpectedly").
 */
public enum Level4Exit {
	ELEVATOR_3("3", false, 0.22),
	OMEGA_STAIRS_5("5", false, 0.0),
	OMEGA_STAIRS_6("6", false, 0.0),
	STAIRS_5("5", true, 0.20),
	STAIRS_6("6", true, 0.20),
	BASEMENT_71("71", true, 0.08),
	WHITE_DOOR_4P3("4.3", true, 0.16),
	CITY_DOOR_11("11", true, 0.12),
	HATCH_2("2", true, 0.15),
	MIRROR_13("13", true, 0.10);

	public final String level;
	public final boolean vanishing;
	/** Per-region spawn chance for wild/non-base exits. */
	public final double chance;

	Level4Exit(String level, boolean vanishing, double chance) {
		this.level = level;
		this.vanishing = vanishing;
		this.chance = chance;
	}

	/** Whether this exit builds a multi-block walkable cabin, stairwell, or basement staircase. */
	boolean hasStructure() {
		return this == ELEVATOR_3 || this == OMEGA_STAIRS_5 || this == OMEGA_STAIRS_6
				|| this == STAIRS_5 || this == STAIRS_6 || this == BASEMENT_71;
	}
}
