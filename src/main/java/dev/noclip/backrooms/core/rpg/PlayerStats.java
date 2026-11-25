package dev.noclip.backrooms.core.rpg;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

/**
 * Persistent per-player Backrooms RPG progression & sanity state.
 * Stored via Fabric Data Attachment API (persisted in NBT, copied on death, synced only to the owning client).
 */
public record PlayerStats(
		int level,
		int xp,
		int statPoints,
		int strength,
		int agility,
		int vitality,
		int sanityStat,
		float sanity
) {
	/*
	 * Horror direction (docs/HORROR_ESCAPE_PLAN.md, 2026-11-25): levels make you steadier, not stronger. Max health and
	 * max sanity grow every five levels on their own; points only speed up recovery (with diminishing returns), and
	 * strength adds little. In the Backrooms the player's health is cut in half (PlayerRpg).
	 */
	public static final float BASE_MAX_SANITY = 50.0F;
	public static final int LEVELS_PER_STEP = 5;
	public static final float SANITY_PER_STEP = 10.0F;
	public static final double HEALTH_PER_STEP = 2.0;
	public static final float HALLUCINATION_THRESHOLD = 0.30F;
	public static final int POINTS_PER_LEVEL = 3;
	public static final int INITIAL_STAT_POINTS = 3;

	public static final Codec<PlayerStats> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("level", 1).forGetter(PlayerStats::level),
			Codec.INT.optionalFieldOf("xp", 0).forGetter(PlayerStats::xp),
			Codec.INT.optionalFieldOf("stat_points", INITIAL_STAT_POINTS).forGetter(PlayerStats::statPoints),
			Codec.INT.optionalFieldOf("strength", 0).forGetter(PlayerStats::strength),
			Codec.INT.optionalFieldOf("agility", 0).forGetter(PlayerStats::agility),
			Codec.INT.optionalFieldOf("vitality", 0).forGetter(PlayerStats::vitality),
			Codec.INT.optionalFieldOf("sanity_stat", 0).forGetter(PlayerStats::sanityStat),
			Codec.FLOAT.optionalFieldOf("sanity", BASE_MAX_SANITY).forGetter(PlayerStats::sanity)
	).apply(instance, PlayerStats::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, PlayerStats> STREAM_CODEC = StreamCodec.of(
			(buf, stats) -> {
				buf.writeVarInt(stats.level);
				buf.writeVarInt(stats.xp);
				buf.writeVarInt(stats.statPoints);
				buf.writeVarInt(stats.strength);
				buf.writeVarInt(stats.agility);
				buf.writeVarInt(stats.vitality);
				buf.writeVarInt(stats.sanityStat);
				buf.writeFloat(stats.sanity);
			},
			buf -> new PlayerStats(
					buf.readVarInt(),
					buf.readVarInt(),
					buf.readVarInt(),
					buf.readVarInt(),
					buf.readVarInt(),
					buf.readVarInt(),
					buf.readVarInt(),
					buf.readFloat()
			)
	);

	public PlayerStats {
		level = Math.max(1, level);
		xp = Math.max(0, xp);
		statPoints = Math.max(0, statPoints);
		strength = Math.max(0, strength);
		agility = Math.max(0, agility);
		vitality = Math.max(0, vitality);
		sanityStat = Math.max(0, sanityStat);
		float max = BASE_MAX_SANITY + SANITY_PER_STEP * (level / LEVELS_PER_STEP);
		sanity = Mth.clamp(sanity, 0.0F, max);
	}

	public static PlayerStats initial() {
		return new PlayerStats(1, 0, INITIAL_STAT_POINTS, 0, 0, 0, 0, BASE_MAX_SANITY);
	}

	/** How many five-level steps the player has climbed (level 5 is the first). */
	public int steps() {
		return level / LEVELS_PER_STEP;
	}

	public float maxSanity() {
		return BASE_MAX_SANITY + SANITY_PER_STEP * steps();
	}

	public float sanityRatio() {
		float max = maxSanity();
		return max <= 0.0F ? 0.0F : Mth.clamp(sanity / max, 0.0F, 1.0F);
	}

	/** Below 30% sanity triggers pure client-side hallucinations (phantom stalkers & teammate disguises). */
	public boolean isHallucinating() {
		return sanityRatio() < HALLUCINATION_THRESHOLD;
	}

	public int xpNeeded() {
		return 60 + (level - 1) * 45;
	}

	/** Strength: +0.2 damage a point, at most +3. */
	public double attackDamageBonus() {
		return Math.min(3.0, strength * 0.2);
	}

	public double moveSpeedBonus() {
		return agility * 0.025;
	}

	public double attackSpeedBonus() {
		return agility * 0.04;
	}

	/** Extra max health from levels: one heart every five levels (vitality no longer adds any). */
	public double maxHealthBonus() {
		return HEALTH_PER_STEP * steps();
	}

	/** 0 at no points, rising towards 1: each point adds less than the one before. */
	private static float curve(int points) {
		return 1.0F - (float) Math.pow(0.95, points);
	}

	/** Health regenerated per second from Vitality, once the player has not been hurt for a while (PlayerRpg). */
	public float healthRegenPerSec() {
		return 0.4F * curve(vitality);
	}

	/** Fraction of sanity drain negated by the Sanity stat, at most 40%. */
	public float sanityDrainResistance() {
		return 0.4F * curve(sanityStat);
	}

	/** Multiplier for natural sanity recovery, at most 2.5 times. */
	public float sanityRegenMultiplier() {
		return 1.0F + 1.5F * curve(sanityStat);
	}

	/** Extra sanity recovery per second in light with no monsters near, at most 0.3. */
	public float passiveSanityRegenPerSec() {
		return 0.3F * curve(sanityStat);
	}

	public PlayerStats withSanity(float value) {
		return new PlayerStats(level, xp, statPoints, strength, agility, vitality, sanityStat, value);
	}

	public PlayerStats addSanity(float delta) {
		return withSanity(sanity + delta);
	}

	public PlayerStats addStatPoints(int points) {
		return new PlayerStats(level, xp, statPoints + Math.max(0, points), strength, agility, vitality, sanityStat, sanity);
	}

	public PlayerStats addXp(int amount) {
		if (amount <= 0) return this;
		int newLevel = level;
		int newXp = xp + amount;
		int newPoints = statPoints;
		float newSanity = sanity;
		while (newXp >= 60 + (newLevel - 1) * 45) {
			newXp -= 60 + (newLevel - 1) * 45;
			newLevel++;
			newPoints += POINTS_PER_LEVEL;
			newSanity += 10.0F;
		}
		return new PlayerStats(newLevel, newXp, newPoints, strength, agility, vitality, sanityStat, newSanity);
	}

	public PlayerStats allocate(StatKind kind) {
		if (statPoints <= 0) return this;
		return switch (kind) {
			case STRENGTH -> new PlayerStats(level, xp, statPoints - 1, strength + 1, agility, vitality, sanityStat, sanity);
			case AGILITY -> new PlayerStats(level, xp, statPoints - 1, strength, agility + 1, vitality, sanityStat, sanity);
			case VITALITY -> new PlayerStats(level, xp, statPoints - 1, strength, agility, vitality + 1, sanityStat, sanity);
			case SANITY -> new PlayerStats(level, xp, statPoints - 1, strength, agility, vitality, sanityStat + 1, sanity);
		};
	}

	public enum StatKind {
		STRENGTH,
		AGILITY,
		VITALITY,
		SANITY;

		public static StatKind fromOrdinal(int ordinal) {
			StatKind[] values = values();
			return ordinal >= 0 && ordinal < values.length ? values[ordinal] : STRENGTH;
		}
	}
}
