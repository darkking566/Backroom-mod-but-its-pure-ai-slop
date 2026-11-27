package dev.noclip.backrooms.core.floors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.noclip.backrooms.core.guild.Contracts;
import dev.noclip.backrooms.core.locker.Safebox;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FloorDifficultyTest {
	@Test
	void horizontalBandsAndVerticalFloorScaleMatchDesign() {
		FloorDifficulty.Scale base = FloorDifficulty.compute(1, 0.0, 0);
		assertEquals(0, base.band());
		assertEquals(1.0, base.hpMult(), 1e-6);
		assertEquals(1.0, base.damageMult(), 1e-6);
		assertEquals(0.0, base.lootBonus(), 1e-6);

		FloorDifficulty.Scale b1 = FloorDifficulty.compute(1, 400.0, 0);
		assertEquals(1, b1.band());
		assertEquals(1.35, b1.hpMult(), 1e-6);
		assertEquals(1.20, b1.damageMult(), 1e-6);

		FloorDifficulty.Scale b2 = FloorDifficulty.compute(1, 900.0, 0);
		assertEquals(2, b2.band());
		assertEquals(1.85, b2.hpMult(), 1e-6);
		assertEquals(1.50, b2.damageMult(), 1e-6);

		FloorDifficulty.Scale b3 = FloorDifficulty.compute(1, 1800.0, 0);
		assertEquals(3, b3.band());
		assertEquals(2.50, b3.hpMult(), 1e-6);
		assertEquals(1.90, b3.damageMult(), 1e-6);

		FloorDifficulty.Scale deeper = FloorDifficulty.compute(4, 400.0, 0);
		assertTrue(deeper.hpMult() > b1.hpMult());
		assertTrue(deeper.damageMult() > b1.damageMult());
		assertTrue(deeper.lootBonus() > b1.lootBonus());
	}

	@Test
	void pingPongAllowsSingleBacktrackAndPenalizesRapidABAB() {
		// 0 -> 1 -> 0 is a normal backtrack (must remain allowed, e.g. FloorsClientTest)
		List<FloorDifficulty.Transition> singleBack = List.of(
				new FloorDifficulty.Transition("0", 100),
				new FloorDifficulty.Transition("1", 200));
		assertFalse(FloorDifficulty.isPingPong(singleBack, "0", 300));

		// 0 -> 1 -> 0 -> 1 inside window is ping-pong farming
		List<FloorDifficulty.Transition> bouncing = List.of(
				new FloorDifficulty.Transition("0", 100),
				new FloorDifficulty.Transition("1", 200),
				new FloorDifficulty.Transition("0", 300));
		assertTrue(FloorDifficulty.isPingPong(bouncing, "1", 400));

		// Going to a third distinct level clears the bounce pattern
		assertFalse(FloorDifficulty.isPingPong(bouncing, "2", 400));

		// Outside the 3-minute window it is no longer treated as rapid farming
		assertFalse(FloorDifficulty.isPingPong(bouncing, "1", 100 + FloorDifficulty.PING_PONG_WINDOW + 10));

		FloorDifficulty.Scale penalized = FloorDifficulty.compute(3, 800.0, 2);
		assertTrue(penalized.hpMult() > FloorDifficulty.compute(3, 800.0, 0).hpMult());
		assertTrue(penalized.lootBonus() < 0.0);
	}

	@Test
	void safeboxUpgradeCostsFollowProgressionAndCapAtSix() {
		assertEquals(300L, Safebox.upgradeCost(2));
		assertEquals(600L, Safebox.upgradeCost(3));
		assertEquals(1000L, Safebox.upgradeCost(4));
		assertEquals(1600L, Safebox.upgradeCost(5));
		assertEquals(-1L, Safebox.upgradeCost(6));
	}

	@Test
	void contractsAntiDeadlockBlocksMissingLevels() {
		Set<String> onlyShallow = Set.of("0", "1");
		assertTrue(Contracts.Contract.SURVEY.completableWith(onlyShallow));
		assertTrue(Contracts.Contract.HUNT_HOUNDS.completableWith(onlyShallow));
		assertFalse(Contracts.Contract.COURIER_012.completableWith(onlyShallow));
		assertFalse(Contracts.Contract.READINGS.completableWith(Set.of("0", "1", "2")));
		assertTrue(Contracts.Contract.COURIER_012.completableWith(Set.of("0", "1", "2")));
	}
}
