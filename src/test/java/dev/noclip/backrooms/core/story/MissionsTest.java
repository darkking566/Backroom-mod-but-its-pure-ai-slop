package dev.noclip.backrooms.core.story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class MissionsTest {
	@Test
	void sampleObjectivesProducesTwoOrThreeDistinctGoalsAndLevel0HasNoCombat() {
		for (String level : List.of("0", "1", "2", "3", "4")) {
			for (long seed = 0; seed < 12; seed++) {
				List<Missions.ObjectiveSpec> list = Missions.sampleObjectives(level, seed);
				assertTrue(list.size() >= 2 && list.size() <= 3,
						"Level " + level + " expected 2..3 objectives, got " + list.size());
				Set<String> seen = new HashSet<>();
				for (Missions.ObjectiveSpec spec : list) {
					assertTrue(seen.add(spec.kind()), "Duplicate objective " + spec.kind());
					assertTrue(spec.goal() >= 1);
					if ("0".equals(level)) {
						assertFalse(spec.kind().contains("bounty"), "Level 0 exploration must be non-combat");
					}
				}
			}
		}
	}

	@Test
	void formatCompassDetectsNearbyAndDirection() {
		assertEquals("NEAR", Missions.formatCompass(10.0, 10.0, 0.0F, 22, 15));
		String ahead = Missions.formatCompass(0.0, 0.0, 0.0F, 0, 60);
		assertTrue(ahead.startsWith("↑"), "Expected straight ahead (+Z at yaw 0), got: " + ahead);
		assertTrue(ahead.contains("60") || ahead.contains("61"), "Expected ~60m distance, got: " + ahead);

		String behind = Missions.formatCompass(0.0, 0.0, 180.0F, 0, 50);
		assertTrue(behind.startsWith("↓"), "Expected behind when facing north (-Z) toward +Z, got: " + behind);
	}

	@Test
	void bountyUnlockThresholdsRespectProgression() {
		assertFalse(Missions.isBountyUnlocked("bounty_hounds", 0, List.of("0")));
		assertTrue(Missions.isBountyUnlocked("bounty_hounds", 1, List.of("0", "1")));
		assertFalse(Missions.isBountyUnlocked("bounty_deep_boss", 1, List.of("0", "1")));
		assertTrue(Missions.isBountyUnlocked("bounty_deep_boss", 3, List.of("0", "1", "2")));
	}
}
