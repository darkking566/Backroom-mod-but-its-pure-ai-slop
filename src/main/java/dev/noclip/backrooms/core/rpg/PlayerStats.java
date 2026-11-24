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
	public static final float BASE_MAX_SANITY = 100.0F;
	public static final float SANITY_PER_POINT = 10.0F;
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
		float max = BASE_MAX_SANITY + sanityStat * SANITY_PER_POINT;
		sanity = Mth.clamp(sanity, 0.0F, max);
	}

	public static PlayerStats initial() {
		return new PlayerStats(1, 0, INITIAL_STAT_POINTS, 0, 0, 0, 0, BASE_MAX_SANITY);
	}

	public float maxSanity() {
		return BASE_MAX_SANITY + sanityStat * SANITY_PER_POINT;
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

	public double attackDamageBonus() {
		return strength * 0.5;
	}

	public double moveSpeedBonus() {
		return agility * 0.025;
	}

	public double attackSpeedBonus() {
		return agility * 0.04;
	}

	public double maxHealthBonus() {
		return vitality * 2.0;
	}

	public double armorToughnessBonus() {
		return vitality * 0.25;
	}

	/** Fraction of sanity drain negated (capped at 65%). */
	public float sanityDrainResistance() {
		return Math.min(0.65F, sanityStat * 0.06F);
	}

	/** Multiplier for natural sanity recovery (will synergize with the future Mage class). */
	public float sanityRegenMultiplier() {
		return 1.0F + sanityStat * 0.08F;
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
			newSanity += 20.0F;
		}
		return new PlayerStats(newLevel, newXp, newPoints, strength, agility, vitality, sanityStat, newSanity);
	}

	public PlayerStats allocate(StatKind kind) {
		if (statPoints <= 0) return this;
		return switch (kind) {
			case STRENGTH -> new PlayerStats(level, xp, statPoints - 1, strength + 1, agility, vitality, sanityStat, sanity);
			case AGILITY -> new PlayerStats(level, xp, statPoints - 1, strength, agility + 1, vitality, sanityStat, sanity);
			case VITALITY -> new PlayerStats(level, xp, statPoints - 1, strength, agility, vitality + 1, sanityStat, sanity);
			case SANITY -> new PlayerStats(level, xp, statPoints - 1, strength, agility, vitality, sanityStat + 1, sanity + SANITY_PER_POINT);
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
