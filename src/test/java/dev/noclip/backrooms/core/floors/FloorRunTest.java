package dev.noclip.backrooms.core.floors;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FloorRunTest {
	@Test
	void everyExitIsAFloorAndGoingBackCountsToo() {
		// The example in docs/企劃書/1-遊戲玩法/README.md: Level 0, Level 1, Level 2, back to Level 0.
		FloorRun run = FloorRun.NONE.start("0").enter("1").enter("2").enter("0");
		assertEquals(4, run.floor());
		assertEquals(3, run.distinct());
		assertEquals(4, run.best());
	}

	@Test
	void backInTheOverworldTheTripEndsButTheRecordStays() {
		FloorRun run = FloorRun.NONE.start("0").enter("1").enter("0").end();
		assertEquals(0, run.floor());
		assertEquals(0, run.distinct());
		assertEquals(3, run.best());
		FloorRun again = run.start("0");
		assertEquals(1, again.floor());
		assertEquals(3, again.best());
	}
}
