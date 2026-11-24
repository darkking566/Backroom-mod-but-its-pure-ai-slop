package dev.noclip.backrooms.core.rpg;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class PlayerStatsTest {
	@Test
	void initialStatsStartAtLevelOneWithFullSanity() {
		PlayerStats stats = PlayerStats.initial();
		assertEquals(1, stats.level());
		assertEquals(0, stats.xp());
		assertEquals(PlayerStats.INITIAL_STAT_POINTS, stats.statPoints());
		assertEquals(100.0F, stats.maxSanity(), 1.0E-4F);
		assertEquals(100.0F, stats.sanity(), 1.0E-4F);
		assertFalse(stats.isHallucinating());
	}

	@Test
	void hallucinationTriggersStrictlyBelowThirtyPercent() {
		PlayerStats atThirty = PlayerStats.initial().withSanity(30.0F);
		PlayerStats belowThirty = PlayerStats.initial().withSanity(29.9F);
		assertFalse(atThirty.isHallucinating());
		assertTrue(belowThirty.isHallucinating());
	}

	@Test
	void levelingUpGrantsStatPointsAndCarriesRemainderXp() {
		PlayerStats stats = PlayerStats.initial().addXp(180); // 60 (Lv1->2) + 105 (Lv2->3) = 165, rem 15
		assertEquals(3, stats.level());
		assertEquals(15, stats.xp());
		assertEquals(PlayerStats.INITIAL_STAT_POINTS + 2 * PlayerStats.POINTS_PER_LEVEL, stats.statPoints());
	}

	@Test
	void allocatingStatsSpendsPointAndRaisesBonuses() {
		PlayerStats stats = PlayerStats.initial()
				.allocate(PlayerStats.StatKind.STRENGTH)
				.allocate(PlayerStats.StatKind.VITALITY)
				.allocate(PlayerStats.StatKind.SANITY);
		assertEquals(0, stats.statPoints());
		assertEquals(1, stats.strength());
		assertEquals(1, stats.vitality());
		assertEquals(1, stats.sanityStat());
		assertEquals(110.0F, stats.maxSanity(), 1.0E-4F);
		assertEquals(110.0F, stats.sanity(), 1.0E-4F);
		assertEquals(2.0, stats.maxHealthBonus(), 1.0E-4);

		// Spending with 0 points left is a safe no-op.
		assertEquals(stats, stats.allocate(PlayerStats.StatKind.AGILITY));
	}
}
