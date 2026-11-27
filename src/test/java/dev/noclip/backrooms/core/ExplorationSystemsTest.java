package dev.noclip.backrooms.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.noclip.backrooms.core.locker.RelayService;
import dev.noclip.backrooms.core.quest.Survivors;
import dev.noclip.backrooms.core.trade.Appraisal;
import dev.noclip.backrooms.props.ChestLockTier;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExplorationSystemsTest {
	@Test
	void levelGateEvaluatesHackerKeycardAndFlagOrder() {
		LevelGates.Gate gate = LevelGates.Gate.of(
				"11", 3, "noclip_backrooms:level_keycard", false, "power:level_11", "");

		assertTrue(LevelGates.evaluate(null, false, 0, false, Set.of()).allowed());
		assertTrue(LevelGates.evaluate(gate, true, 0, false, Set.of()).allowed());

		var noHacker = LevelGates.evaluate(gate, false, 2, true, Set.of("power:level_11"));
		assertFalse(noHacker.allowed());
		assertEquals("message.noclip_backrooms.gate.need_hacker", noHacker.reasonKey());

		var noCard = LevelGates.evaluate(gate, false, 3, false, Set.of("power:level_11"));
		assertFalse(noCard.allowed());
		assertEquals("message.noclip_backrooms.gate.need_item", noCard.reasonKey());

		var noFlag = LevelGates.evaluate(gate, false, 3, true, Set.of());
		assertFalse(noFlag.allowed());
		assertEquals("message.noclip_backrooms.gate.need_flag", noFlag.reasonKey());

		assertTrue(LevelGates.evaluate(gate, false, 3, true, Set.of("power:level_11")).allowed());
	}

	@Test
	void tieredChestLockIdsAndShortcutRepairFormulasWork() {
		assertEquals(ChestLockTier.ANOMALY_VAULT, ChestLockTier.byId("anomaly_vault"));
		assertEquals(ChestLockTier.BOSS_TROPHY, ChestLockTier.byId("boss_trophy"));
		assertEquals(ChestLockTier.ARMORY, ChestLockTier.byId("armory"));

		long unmapped = RelayService.shortcutCost("3", false);
		long mapped = RelayService.shortcutCost("3", true);
		assertEquals(260L, unmapped);
		assertEquals(182L, mapped);

		assertEquals(0L, RelayService.repairCost(0, 500, 0));
		long baseRepair = RelayService.repairCost(250, 500, 0);
		long engRepair = RelayService.repairCost(250, 500, 10);
		assertEquals(30L, baseRepair);
		assertEquals(21L, engRepair);
	}

	@Test
	void geodeAppraisalJackpotScalesWithLuckAndSurvivorsAdvanceDeterministically() {
		double base = Appraisal.jackpotChance(0);
		double mid = Appraisal.jackpotChance(8);
		double high = Appraisal.jackpotChance(50);
		assertEquals(0.12, base, 1e-6);
		assertTrue(mid > base);
		assertTrue(high >= mid && high <= 0.40 + 1e-6);

		assertEquals(Survivors.Stage.SEEKING_ITEM, Survivors.nextStage(Survivors.Stage.STRANDED));
		assertEquals(Survivors.Stage.ESCORTING, Survivors.nextStage(Survivors.Stage.SEEKING_ITEM));
		assertEquals(Survivors.Stage.RESCUED, Survivors.nextStage(Survivors.Stage.ESCORTING));

		assertEquals("north", Survivors.compassDirection(0, -15));
		assertEquals("east", Survivors.compassDirection(15, 0));
		assertEquals("south_west", Survivors.compassDirection(-12, 12));
		assertEquals("nearby", Survivors.compassDirection(1, -1));
	}
}
