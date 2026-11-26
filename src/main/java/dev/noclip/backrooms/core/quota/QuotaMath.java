package dev.noclip.backrooms.core.quota;

/**
 * The numbers of the M.E.G. quota cycle (docs/project/1-gameplay/README.md), kept apart from Minecraft so they can be
 * unit-tested. Time is "home time": ticks the team has spent in the Overworld; the Backrooms have no time.
 */
public final class QuotaMath {
	/** One day of home time, in ticks. */
	public static final long DAY = 24_000L;
	/** A cycle lasts this many days. */
	public static final int CYCLE_DAYS = 5;
	/** The body holds up for this many days; the Wretch rejection sets in after. */
	public static final int SAFE_DAYS = 2;
	/** The first cycle's quota, and how much it grows with every cycle paid. */
	public static final long FIRST_QUOTA = 100;
	public static final double GROWTH = 1.3;
	/** Every player after the first adds this much to the quota. */
	public static final double PER_EXTRA_PLAYER = 0.6;
	/** Unpaid quota turns into debt, and debt grows by this much every deadline it is not paid. */
	public static final double INTEREST = 1.2;
	/** A required item that was never handed in counts as this many points of debt. */
	public static final long MISSING_ITEM_POINTS = 30;
	/** Debt paid from the account costs this many credits a point. */
	public static final long CREDITS_PER_DEBT_POINT = 2;

	private QuotaMath() {
	}

	/** The day of the cycle (1-based) after this much home time; past the deadline it stays on the last day. */
	public static int day(long homeTicks) {
		return (int) Math.min(CYCLE_DAYS, Math.max(0, homeTicks) / DAY + 1);
	}

	/** Whole days left before the deadline, counting the current one. */
	public static int daysLeft(long homeTicks) {
		return Math.max(0, CYCLE_DAYS - (int) (Math.max(0, homeTicks) / DAY));
	}

	public static boolean pastDeadline(long homeTicks) {
		return homeTicks >= CYCLE_DAYS * DAY;
	}

	/** How bad the rejection is (0..100): nothing on days 1-2, rising through days 3-5 to the worst at the deadline. */
	public static float rejection(long homeTicks) {
		long since = homeTicks - SAFE_DAYS * DAY;
		if (since <= 0) return 0.0F;
		return (float) Math.min(100.0, 100.0 * since / ((CYCLE_DAYS - SAFE_DAYS) * DAY));
	}

	/** The quota of a cycle: it grows with every cycle the team has paid (not with ones it defaulted on). */
	public static long quota(int cyclesPaid, int players) {
		double base = FIRST_QUOTA * Math.pow(GROWTH, Math.max(0, cyclesPaid));
		return Math.round(base * teamFactor(players));
	}

	public static double teamFactor(int players) {
		return 1.0 + PER_EXTRA_PLAYER * Math.max(0, players - 1);
	}

	/** How many of each required item the team has to hand in: one more for every two players. */
	public static int itemCount(int players) {
		return Math.max(1, (players + 1) / 2);
	}

	/** How many kinds of item a cycle requires. */
	public static int itemKinds(int cycle) {
		return cycle >= 3 ? 2 : 1;
	}

	/** Debt after a deadline passes with {@code unpaid} points still owed (old debt included), 20% on top, rounded up. */
	public static long debtAfterDeadline(long unpaid) {
		return unpaid <= 0 ? 0 : (long) Math.ceil(unpaid * INTEREST - 1e-9);
	}
}
