package dev.noclip.backrooms.monsters;

/**
 * How tough the Backrooms' creatures are, in one place. The game turned to horror on 2026-11-25
 * (docs/企劃書/1-遊戲玩法/README.md): a stone sword used to kill a hound in six swings; now most creatures take about two
 * and a half times the hits and bosses three times. Their damage is unchanged, but the player has half the health in
 * the Backrooms, so each bite counts twice as much.
 */
public final class Tuning {
	public static final double HEALTH = 2.5;
	public static final double BOSS_HEALTH = 3.0;

	private Tuning() {
	}

	/** A creature's max health from the number it was designed with. */
	public static double hp(double base) {
		return base * HEALTH;
	}

	public static double bossHp(double base) {
		return base * BOSS_HEALTH;
	}
}
