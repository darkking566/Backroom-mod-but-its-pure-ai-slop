package dev.noclip.backrooms.core.quota;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The numbers of the M.E.G. quota cycle (docs/project/1-gameplay/README.md). */
final class QuotaMathTest {
	private static final long DAY = QuotaMath.DAY;

	@Test
	void daysCountHomeTimeAndStopAtTheDeadline() {
		assertEquals(1, QuotaMath.day(0));
		assertEquals(1, QuotaMath.day(DAY - 1));
		assertEquals(2, QuotaMath.day(DAY));
		assertEquals(5, QuotaMath.day(4 * DAY));
		assertEquals(5, QuotaMath.day(9 * DAY), "past the deadline it stays on the last day");
		assertEquals(5, QuotaMath.daysLeft(0));
		assertEquals(1, QuotaMath.daysLeft(4 * DAY + 10));
		assertFalse(QuotaMath.pastDeadline(5 * DAY - 1));
		assertTrue(QuotaMath.pastDeadline(5 * DAY));
	}

	@Test
	void rejectionWaitsTwoDaysThenRisesToTheWorstAtTheDeadline() {
		assertEquals(0.0F, QuotaMath.rejection(0));
		assertEquals(0.0F, QuotaMath.rejection(2 * DAY), "days 1-2 are safe");
		assertEquals(100.0F / 3, QuotaMath.rejection(3 * DAY), 0.01F);
		assertEquals(100.0F, QuotaMath.rejection(5 * DAY), 0.01F);
		assertEquals(100.0F, QuotaMath.rejection(8 * DAY), 0.01F);
	}

	@Test
	void quotaGrowsThirtyPercentACycleAndWithTheTeam() {
		assertEquals(100, QuotaMath.quota(0, 1));
		assertEquals(130, QuotaMath.quota(1, 1));
		assertEquals(169, QuotaMath.quota(2, 1));
		assertEquals(160, QuotaMath.quota(0, 2));
		assertEquals(220, QuotaMath.quota(0, 3));
		assertEquals(280, QuotaMath.quota(0, 4));
	}

	@Test
	void requiredItemsGrowWithTheTeamAndTheCycle() {
		assertEquals(1, QuotaMath.itemCount(1));
		assertEquals(1, QuotaMath.itemCount(2));
		assertEquals(2, QuotaMath.itemCount(3));
		assertEquals(2, QuotaMath.itemCount(4));
		assertEquals(1, QuotaMath.itemKinds(1));
		assertEquals(1, QuotaMath.itemKinds(2));
		assertEquals(2, QuotaMath.itemKinds(3));
	}

	@Test
	void debtCompoundsTwentyPercentEveryDeadline() {
		assertEquals(0, QuotaMath.debtAfterDeadline(0));
		assertEquals(120, QuotaMath.debtAfterDeadline(100));
		assertEquals(144, QuotaMath.debtAfterDeadline(QuotaMath.debtAfterDeadline(100)));
		assertEquals(2, QuotaMath.debtAfterDeadline(1), "rounded up");
	}
}
