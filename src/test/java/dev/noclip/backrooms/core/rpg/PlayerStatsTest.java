package dev.noclip.backrooms.core.rpg;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** The horror-direction rules (docs/HORROR_ESCAPE_PLAN.md): levels make you steadier, not stronger. */
final class PlayerStatsTest {
	@Test
	void initialStatsStartAtLevelOneWithFullSanity() {
		PlayerStats stats = PlayerStats.initial();
		assertEquals(1, stats.level());
		assertEquals(0, stats.xp());
		assertEquals(PlayerStats.INITIAL_STAT_POINTS, stats.statPoints());
		assertEquals(50.0F, stats.maxSanity(), 1.0E-4F);
		assertEquals(50.0F, stats.sanity(), 1.0E-4F);
		assertEquals(0.0, stats.maxHealthBonus(), 1.0E-4);
		assertFalse(stats.isHallucinating());
	}

	@Test
	void hallucinationTriggersStrictlyBelowThirtyPercent() {
		PlayerStats atThirty = PlayerStats.initial().withSanity(15.0F);
		PlayerStats belowThirty = PlayerStats.initial().withSanity(14.9F);
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
	void healthAndSanityGrowEveryFiveLevelsNotWithPoints() {
		PlayerStats four = PlayerStats.initial().addXp(509); // 510 XP reaches level 5
		assertEquals(4, four.level());
		assertEquals(0.0, four.maxHealthBonus(), 1.0E-4);
		assertEquals(50.0F, four.maxSanity(), 1.0E-4F);

		PlayerStats five = four.addXp(1);
		assertEquals(5, five.level());
		assertEquals(2.0, five.maxHealthBonus(), 1.0E-4);
		assertEquals(60.0F, five.maxSanity(), 1.0E-4F);

		PlayerStats ten = PlayerStats.initial().addXp(2160);
		assertEquals(10, ten.level());
		assertEquals(4.0, ten.maxHealthBonus(), 1.0E-4);
		assertEquals(70.0F, ten.maxSanity(), 1.0E-4F);
	}

	@Test
	void allocatingStatsSpendsPointAndOnlySpeedsUpRecovery() {
		PlayerStats stats = PlayerStats.initial()
				.allocate(PlayerStats.StatKind.STRENGTH)
				.allocate(PlayerStats.StatKind.VITALITY)
				.allocate(PlayerStats.StatKind.SANITY);
		assertEquals(0, stats.statPoints());
		assertEquals(1, stats.strength());
		assertEquals(1, stats.vitality());
		assertEquals(1, stats.sanityStat());
		assertEquals(50.0F, stats.maxSanity(), 1.0E-4F);
		assertEquals(0.0, stats.maxHealthBonus(), 1.0E-4);
		assertEquals(0.2, stats.attackDamageBonus(), 1.0E-4);
		assertEquals(0.02F, stats.healthRegenPerSec(), 1.0E-4F);
		assertEquals(1.075F, stats.sanityRegenMultiplier(), 1.0E-4F);
		assertEquals(0.015F, stats.passiveSanityRegenPerSec(), 1.0E-4F);
		assertEquals(0.02F, stats.sanityDrainResistance(), 1.0E-4F);

		// Spending with 0 points left is a safe no-op.
		assertEquals(stats, stats.allocate(PlayerStats.StatKind.AGILITY));
	}

	@Test
	void pointsHaveDiminishingReturnsAndStrengthIsCapped() {
		PlayerStats heavy = new PlayerStats(40, 0, 0, 100, 0, 100, 100, 50.0F);
		assertEquals(3.0, heavy.attackDamageBonus(), 1.0E-4);
		assertTrue(heavy.healthRegenPerSec() < 0.4F);
		assertTrue(heavy.sanityDrainResistance() < 0.4F);
		assertTrue(heavy.sanityRegenMultiplier() < 2.5F);
		PlayerStats ten = new PlayerStats(40, 0, 0, 0, 0, 10, 0, 50.0F);
		PlayerStats twenty = new PlayerStats(40, 0, 0, 0, 0, 20, 0, 50.0F);
		assertTrue(twenty.healthRegenPerSec() - ten.healthRegenPerSec() < ten.healthRegenPerSec(),
				"the second ten points add less than the first ten");
	}
}
