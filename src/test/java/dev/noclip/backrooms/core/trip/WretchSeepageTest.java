package dev.noclip.backrooms.core.trip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class WretchSeepageTest {
	@Test
	void stageThresholdsCoverZeroThroughFour() {
		assertEquals(0, new PullState(0.0F, 0L, true, 0).stage());
		assertEquals(0, new PullState(24.9F, 0L, true, 0).stage());
		assertEquals(1, new PullState(25.0F, 0L, true, 0).stage());
		assertEquals(2, new PullState(50.0F, 0L, true, 0).stage());
		assertEquals(3, new PullState(75.0F, 0L, true, 0).stage());
		assertEquals(4, new PullState(95.0F, 0L, true, 0).stage());
		assertEquals(4, new PullState(100.0F, 0L, true, 0).stage());
	}

	@Test
	void escapingGrantsTwoFullDaysOfSafety() {
		long now = 10_000L;
		PullState escaped = PullState.NONE.markEscaped(now);
		assertTrue(escaped.marked());
		assertEquals(0.0F, escaped.pull());
		assertEquals(now + 48_000L, escaped.graceUntil());
	}

	@Test
	void tier1BuildsToleranceAndShortensReliefWhileTier2And3RecoverIt() {
		assertEquals(48_000L, PullState.tier1ReliefTicks(0));
		assertEquals(36_000L, PullState.tier1ReliefTicks(1));
		assertEquals(24_000L, PullState.tier1ReliefTicks(2));
		assertEquals(18_000L, PullState.tier1ReliefTicks(3));
		assertEquals(12_000L, PullState.tier1ReliefTicks(4));
		assertEquals(12_000L, PullState.tier1ReliefTicks(5));

		PullState s = new PullState(80.0F, 0L, true, 0);
		s = s.applyTier1Stabilizer(1000L);
		assertEquals(0.0F, s.pull());
		assertEquals(1, s.tolerance());
		assertEquals(1000L + 48_000L, s.graceUntil());

		s = s.withPull(60.0F).applyTier1Stabilizer(60_000L);
		assertEquals(2, s.tolerance());
		assertEquals(60_000L + 36_000L, s.graceUntil());

		// Tier 2 Serum grants 6 days and reduces tolerance by 1
		s = s.withPull(70.0F).applyTier2Serum(100_000L);
		assertEquals(0.0F, s.pull());
		assertEquals(1, s.tolerance());
		assertEquals(100_000L + PullState.SERUM_T2_TICKS, s.graceUntil());
		assertTrue(s.marked());

		// Tier 3 Anchor Core grants 12 days, resets tolerance to 0, and clears mark
		s = s.withPull(95.0F).applyTier3Core(300_000L);
		assertEquals(0.0F, s.pull());
		assertEquals(0, s.tolerance());
		assertFalse(s.marked());
		assertEquals(300_000L + PullState.CORE_T3_TICKS, s.graceUntil());
	}
}
