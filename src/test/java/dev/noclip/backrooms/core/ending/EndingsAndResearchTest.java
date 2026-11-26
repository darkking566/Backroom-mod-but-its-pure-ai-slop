package dev.noclip.backrooms.core.ending;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.noclip.backrooms.core.locker.Research;
import dev.noclip.backrooms.crafting.BackroomsRecipeRegistry;
import dev.noclip.backrooms.crafting.StationRecipe;
import dev.noclip.backrooms.crafting.StationType;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

final class EndingsAndResearchTest {
	@Test
	void researchTiersGateSpiderwebAndCureRecipesCleanly() {
		BackroomsRecipeRegistry.reload();

		for (StationRecipe r : BackroomsRecipeRegistry.byStation(StationType.SALVAGE)) {
			assertNull(Research.requiredTier(r), "Salvage recipes must remain unlocked: " + r.id());
		}
		for (StationRecipe r : BackroomsRecipeRegistry.byStation(StationType.WORKSHOP)) {
			assertNull(Research.requiredTier(r), "5x5 Workshop recipes must remain unlocked: " + r.id());
		}
		for (StationRecipe r : BackroomsRecipeRegistry.byStation(StationType.DISTILLER)) {
			assertNull(Research.requiredTier(r), "3x3 Distiller recipes must remain unlocked: " + r.id());
		}
		for (StationRecipe r : BackroomsRecipeRegistry.byStation(StationType.SPIDERWEB)) {
			assertEquals(Research.Tier.YELLOW, Research.requiredTier(r), "7x7 Spiderweb requires Yellow Pack: " + r.id());
		}

		StationRecipe cure = BackroomsRecipeRegistry.byId(Identifier.parse("noclip_backrooms:ultimate_wretch_cure"));
		assertTrue(cure != null, "Missing 9x9 ultimate_wretch_cure recipe");
		assertEquals(Research.Tier.PURPLE, Research.requiredTier(cure), "Wretch Cure requires Purple Science Pack");

		int grayCount = 0;
		for (StationRecipe r : BackroomsRecipeRegistry.byStation(StationType.ULTIMATE_SPIDERWEB)) {
			if (r.equals(cure)) continue;
			assertEquals(Research.Tier.GRAY, Research.requiredTier(r), "Other 9x9 recipes require Gray Pack: " + r.id());
			grayCount++;
		}
		assertTrue(grayCount >= 14, "Expected at least 14 gray-gated 9x9 recipes, got " + grayCount);
	}

	@Test
	void endingStateBitmaskAndFreedomLogic() {
		Endings.State none = Endings.State.NONE;
		assertFalse(none.cured());
		assertFalse(none.freed());
		assertFalse(none.truthKnown());
		assertEquals(0, none.filesCount());

		int mask = 0;
		for (int i = 0; i < Endings.TOTAL_ARCHIVE_FILES - 1; i++) {
			mask |= (1 << i);
			Endings.State partial = new Endings.State(false, "", mask);
			assertEquals(i + 1, partial.filesCount());
			assertFalse(partial.truthKnown());
		}
		mask |= (1 << (Endings.TOTAL_ARCHIVE_FILES - 1));
		Endings.State full = new Endings.State(true, Endings.ENDING_CONTRACT, mask);
		assertEquals(Endings.TOTAL_ARCHIVE_FILES, full.filesCount());
		assertTrue(full.truthKnown());
		assertTrue(full.cured());
		assertTrue(full.freed());
	}
}
