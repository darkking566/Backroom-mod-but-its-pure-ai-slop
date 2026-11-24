package dev.noclip.backrooms;

import java.util.Arrays;

/**
 * Lets a client game test run only part of itself, so a change to one level does not cost the whole run.
 * NOCLIP_TEST=level0p1 (comma list of level0, level1, level2, level0p1) picks which levels' tests run;
 * NOCLIP_ONLY=blackout,doors picks sections inside a level's test that has sections. Unset (or blank) runs everything.
 */
public final class TestFilter {
	private TestFilter() {
	}

	public static boolean level(String name) {
		return listed(System.getenv("NOCLIP_TEST"), name);
	}

	public static boolean section(String name) {
		return listed(System.getenv("NOCLIP_ONLY"), name);
	}

	private static boolean listed(String value, String name) {
		return value == null || value.isBlank() || Arrays.asList(value.split("\s*,\s*")).contains(name);
	}
}
