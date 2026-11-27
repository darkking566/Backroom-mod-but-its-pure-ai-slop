package dev.noclip.backrooms.props;

/** Lock tier for tiered Backrooms loot chests (pure enum usable in unit tests without Minecraft block bootstrap). */
public enum ChestLockTier {
	NONE("none"),
	ARMORY("armory"),
	ANOMALY_VAULT("anomaly_vault"),
	BOSS_TROPHY("boss_trophy");

	private final String id;

	ChestLockTier(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public static ChestLockTier byId(String id) {
		if (id != null) {
			for (ChestLockTier tier : values()) {
				if (tier.id.equals(id)) return tier;
			}
		}
		return NONE;
	}
}
