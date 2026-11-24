package dev.noclip.backrooms.level3;

/**
 * The ways out of Level 3, after the Wikidot Level 3 article ("Electrical Station"), Entrances and Exits:
 * elevators lead to Level 4 or Level 5 at random; a door in Base Gamma leads back to Level 11; and now and then a
 * room with a car in it turns up, and getting in the car leads to Level 69.
 *
 * As everywhere, the lower the level, the harder the way there is to find.
 */
public enum Level3Exit {
	// Ordered by level, so also from hardest to easiest to find.
	ELEVATOR(new String[] {"4", "5"}),
	BASE_GAMMA_DOOR(new String[] {"11"}),
	CAR(new String[] {"69"});

	/** Where it leads; an elevator picks one at random each ride. */
	final String[] levels;
	/** The chance that one 96x96 region holds one of these. */
	final double chance;

	Level3Exit(String[] levels) {
		this.levels = levels;
		this.chance = 0.06 + 0.18 * Math.log10(1 + Double.parseDouble(levels[0]));
	}
}
