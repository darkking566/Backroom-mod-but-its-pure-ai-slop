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
		float sanity,
		boolean luckUnlocked,
		int luck,
		boolean psiUnlocked,
		int psi,
		boolean engUnlocked,
		int eng
) {
	/*
	 * Horror direction (docs/企劃書/1-遊戲玩法/README.md, 2026-11-25): levels make you steadier, not stronger. Max health and
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

	public PlayerStats(int level, int xp, int statPoints, int strength, int agility, int vitality, int sanityStat, float sanity) {
		this(level, xp, statPoints, strength, agility, vitality, sanityStat, sanity, false, 0, false, 0, false, 0);
	}

	public static final Codec<PlayerStats> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("level", 1).forGetter(PlayerStats::level),
			Codec.INT.optionalFieldOf("xp", 0).forGetter(PlayerStats::xp),
			Codec.INT.optionalFieldOf("stat_points", INITIAL_STAT_POINTS).forGetter(PlayerStats::statPoints),
			Codec.INT.optionalFieldOf("strength", 0).forGetter(PlayerStats::strength),
			Codec.INT.optionalFieldOf("agility", 0).forGetter(PlayerStats::agility),
			Codec.INT.optionalFieldOf("vitality", 0).forGetter(PlayerStats::vitality),
			Codec.INT.optionalFieldOf("sanity_stat", 0).forGetter(PlayerStats::sanityStat),
			Codec.FLOAT.optionalFieldOf("sanity", BASE_MAX_SANITY).forGetter(PlayerStats::sanity),
			Codec.BOOL.optionalFieldOf("luck_unlocked", false).forGetter(PlayerStats::luckUnlocked),
			Codec.INT.optionalFieldOf("luck", 0).forGetter(PlayerStats::luck),
			Codec.BOOL.optionalFieldOf("psi_unlocked", false).forGetter(PlayerStats::psiUnlocked),
			Codec.INT.optionalFieldOf("psi", 0).forGetter(PlayerStats::psi),
			Codec.BOOL.optionalFieldOf("eng_unlocked", false).forGetter(PlayerStats::engUnlocked),
			Codec.INT.optionalFieldOf("eng", 0).forGetter(PlayerStats::eng)
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
				int mask = (stats.luckUnlocked ? 1 : 0)
						| (stats.psiUnlocked ? 2 : 0)
						| (stats.engUnlocked ? 4 : 0);
				buf.writeByte(mask);
				buf.writeVarInt(stats.luck);
				buf.writeVarInt(stats.psi);
				buf.writeVarInt(stats.eng);
			},
			buf -> {
				int lvl = buf.readVarInt();
				int x = buf.readVarInt();
				int pts = buf.readVarInt();
				int str = buf.readVarInt();
				int agi = buf.readVarInt();
				int vit = buf.readVarInt();
				int sanStat = buf.readVarInt();
				float san = buf.readFloat();
				int mask = buf.readByte();
				return new PlayerStats(
						lvl, x, pts, str, agi, vit, sanStat, san,
						(mask & 1) != 0, buf.readVarInt(),
						(mask & 2) != 0, buf.readVarInt(),
						(mask & 4) != 0, buf.readVarInt()
				);
			}
	);

	public PlayerStats {
		level = Math.max(1, level);
		xp = Math.max(0, xp);
		statPoints = Math.max(0, statPoints);
		strength = Math.max(0, strength);
		agility = Math.max(0, agility);
		vitality = Math.max(0, vitality);
		sanityStat = Math.max(0, sanityStat);
		luck = Math.max(0, luck);
		luckUnlocked = luckUnlocked || luck > 0;
		psi = Math.max(0, psi);
		psiUnlocked = psiUnlocked || psi > 0;
		eng = Math.max(0, eng);
		engUnlocked = engUnlocked || eng > 0;
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

	/** Strength knockback distance bonus in blocks, diminishing up to +0.8. */
	public double knockbackBonus() {
		return 0.8 * curve(strength);
	}

	/** Strength attack-speed compensation for heavy slow melee weapons, diminishing up to +20%. */
	public double heavySwingSpeedBonus() {
		return 0.20 * curve(strength);
	}

	/** Agility multiplier on firearm cooldown ticks, diminishing down to 0.70 (-30%). */
	public float firearmCooldownScale() {
		return 1.0F - 0.30F * curve(agility);
	}

	/** Agility multiplier on firearm spread, diminishing down to 0.65 (-35%). */
	public float firearmSpreadScale() {
		return 1.0F - 0.35F * curve(agility);
	}

	/** Agility multiplier on firearm client recoil, diminishing down to 0.65 (-35%). */
	public float firearmRecoilScale() {
		return 1.0F - 0.35F * curve(agility);
	}

	/** Agility critical strike chance for firearms & melee (1.35x damage), diminishing up to 20%. */
	public float critChance() {
		return 0.20F * curve(agility);
	}

	/** Agility multiplier on sprint hunger exhaustion, diminishing down to 0.65 (-35%). */
	public float sprintExhaustionScale() {
		return 1.0F - 0.35F * curve(agility);
	}

	/** Vitality reduction on finite harmful effect duration (poison, wither, bleed), up to -45%. */
	public float harmfulDurationReduction() {
		return 0.45F * curve(vitality);
	}

	/** Sanity multiplier on magic weapon sanity cost, diminishing down to 0.65 (-35%). */
	public float magicSanityCostScale() {
		return 1.0F - 0.35F * curve(sanityStat);
	}

	/** Luck extra chance for bonus container/mob drops (theme-locked), up to +35%. */
	public double luckLootChance() {
		return luckUnlocked ? 0.35 * curve(luck) : 0.0;
	}

	/** Luck extra personal credit bonus on completed contracts, up to +25%. */
	public double luckContractBonus() {
		return luckUnlocked ? 0.25 * curve(luck) : 0.0;
	}

	/** Luck roll bonus applied to Fate Dice casts (0..3). */
	public int luckFateRollBonus() {
		return luckUnlocked ? Math.round(3.0F * curve(luck)) : 0;
	}

	/** Psi extra sanity-drain resistance stacked multiplicatively with Sanity, up to 20%. */
	public float psiSanityResistance() {
		return psiUnlocked ? 0.20F * curve(psi) : 0.0F;
	}

	/** Combined sanity drain resistance from Sanity (max 40%) and Psi (max 20%), capped around 52%. */
	public float totalSanityDrainResistance() {
		return 1.0F - (1.0F - sanityDrainResistance()) * (1.0F - psiSanityResistance());
	}

	/** Psi spell radius/reach multiplier on Backrooms magic items, up to 1.35x. */
	public float psiSpellRadiusScale() {
		return psiUnlocked ? 1.0F + 0.35F * curve(psi) : 1.0F;
	}

	/** Engineering chance for firearms to fire without consuming ammo, up to 30%. */
	public float engAmmoSaveChance() {
		return engUnlocked ? 0.30F * curve(eng) : 0.0F;
	}

	/** Engineering chance for extra base material when dismantling Backrooms blocks, up to +40%. */
	public double engSalvageBonusChance() {
		return engUnlocked ? 0.40 * curve(eng) : 0.0;
	}

	public boolean isUnlocked(StatKind kind) {
		return switch (kind) {
			case STRENGTH, AGILITY, VITALITY, SANITY -> true;
			case LUCK -> luckUnlocked;
			case PSI -> psiUnlocked;
			case ENGINEERING -> engUnlocked;
		};
	}

	public boolean canAllocate(StatKind kind) {
		return statPoints > 0 && isUnlocked(kind);
	}

	public int valueOf(StatKind kind) {
		return switch (kind) {
			case STRENGTH -> strength;
			case AGILITY -> agility;
			case VITALITY -> vitality;
			case SANITY -> sanityStat;
			case LUCK -> luck;
			case PSI -> psi;
			case ENGINEERING -> eng;
		};
	}

	public PlayerStats withSanity(float value) {
		return new PlayerStats(level, xp, statPoints, strength, agility, vitality, sanityStat, value,
				luckUnlocked, luck, psiUnlocked, psi, engUnlocked, eng);
	}

	public PlayerStats addSanity(float delta) {
		return withSanity(sanity + delta);
	}

	public PlayerStats addStatPoints(int points) {
		return new PlayerStats(level, xp, statPoints + Math.max(0, points), strength, agility, vitality, sanityStat, sanity,
				luckUnlocked, luck, psiUnlocked, psi, engUnlocked, eng);
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
		return new PlayerStats(newLevel, newXp, newPoints, strength, agility, vitality, sanityStat, newSanity,
				luckUnlocked, luck, psiUnlocked, psi, engUnlocked, eng);
	}

	public PlayerStats allocate(StatKind kind) {
		if (!canAllocate(kind)) return this;
		return switch (kind) {
			case STRENGTH -> new PlayerStats(level, xp, statPoints - 1, strength + 1, agility, vitality, sanityStat, sanity,
					luckUnlocked, luck, psiUnlocked, psi, engUnlocked, eng);
			case AGILITY -> new PlayerStats(level, xp, statPoints - 1, strength, agility + 1, vitality, sanityStat, sanity,
					luckUnlocked, luck, psiUnlocked, psi, engUnlocked, eng);
			case VITALITY -> new PlayerStats(level, xp, statPoints - 1, strength, agility, vitality + 1, sanityStat, sanity,
					luckUnlocked, luck, psiUnlocked, psi, engUnlocked, eng);
			case SANITY -> new PlayerStats(level, xp, statPoints - 1, strength, agility, vitality, sanityStat + 1, sanity,
					luckUnlocked, luck, psiUnlocked, psi, engUnlocked, eng);
			case LUCK -> new PlayerStats(level, xp, statPoints - 1, strength, agility, vitality, sanityStat, sanity,
					true, luck + 1, psiUnlocked, psi, engUnlocked, eng);
			case PSI -> new PlayerStats(level, xp, statPoints - 1, strength, agility, vitality, sanityStat, sanity,
					luckUnlocked, luck, true, psi + 1, engUnlocked, eng);
			case ENGINEERING -> new PlayerStats(level, xp, statPoints - 1, strength, agility, vitality, sanityStat, sanity,
					luckUnlocked, luck, psiUnlocked, psi, true, eng + 1);
		};
	}

	/** Option 1 scroll rule: first scroll unlocks at 1; repeat scrolls of an unlocked stat grant +1 free point. */
	public PlayerStats applyScroll(StatKind kind) {
		return switch (kind) {
			case LUCK -> new PlayerStats(level, xp, statPoints, strength, agility, vitality, sanityStat, sanity,
					true, luckUnlocked ? luck + 1 : 1, psiUnlocked, psi, engUnlocked, eng);
			case PSI -> new PlayerStats(level, xp, statPoints, strength, agility, vitality, sanityStat, sanity,
					luckUnlocked, luck, true, psiUnlocked ? psi + 1 : 1, engUnlocked, eng);
			case ENGINEERING -> new PlayerStats(level, xp, statPoints, strength, agility, vitality, sanityStat, sanity,
					luckUnlocked, luck, psiUnlocked, psi, true, engUnlocked ? eng + 1 : 1);
			default -> this;
		};
	}

	public enum StatKind {
		STRENGTH,
		AGILITY,
		VITALITY,
		SANITY,
		LUCK,
		PSI,
		ENGINEERING;

		public static StatKind fromOrdinal(int ordinal) {
			StatKind[] values = values();
			return ordinal >= 0 && ordinal < values.length ? values[ordinal] : STRENGTH;
		}
	}
}
