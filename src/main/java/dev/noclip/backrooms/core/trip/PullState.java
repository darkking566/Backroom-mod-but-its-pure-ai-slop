package dev.noclip.backrooms.core.trip;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

/**
 * Per-player Overworld "Wretch Rejection & Backrooms Seepage" state (docs/project/1-gameplay/README.md).
 *
 * <p>After escaping alive from the Backrooms, the player carries a reality-rejection mark:
 * <ul>
 *   <li>For the first 2 in-game days ({@code safeUntil}), they are completely safe ({@code pull == 0}).</li>
 *   <li>Once the buffer expires in the Overworld, {@code pull} (rejection/seepage severity, 0..100)
 *       climbs steadily through 4 stages:
 *       Stage 1 (25..49): sanity stops recovering, faint yellow cast &amp; hum, first carpet patches.
 *       Stage 2 (50..74): sanity drains, flickering lights, carpet spreads, a glowing Backrooms Rift
 *                         opens outdoors so the player can choose to step back in.
 *       Stage 3 (75..94): hunger/weakness waves, scratching noises, client-side overworld hallucinations.
 *       Stage 4 (95..100): critical rejection (-2 max HP, chronic weakness/hunger, heavy seepage).</li>
 *   <li>Repeatedly relying on Tier 1 Stabilizers builds {@code tolerance} (0..5), shortening relief
 *       duration from 2.0 days down to 0.5 days until the player crafts Tier 2 Serum or Tier 3 Core.</li>
 * </ul>
 */
public record PullState(float pull, long graceUntil, boolean marked, int tolerance) {
	public static final PullState NONE = new PullState(0.0F, 0L, false, 0);

	public static final int MAX_TOLERANCE = 5;
	public static final long TICKS_PER_DAY = 24_000L;
	/** Two in-game days (~40 minutes) of peace after escaping the Backrooms. */
	public static final long ESCAPE_GRACE_TICKS = 2L * TICKS_PER_DAY;
	/** Tier 2 Serum relief: 6 in-game days. */
	public static final long SERUM_T2_TICKS = 6L * TICKS_PER_DAY;
	/** Tier 3 Reality Anchor Core relief: 12 in-game days + full tolerance reset. */
	public static final long CORE_T3_TICKS = 12L * TICKS_PER_DAY;

	public static final Codec<PullState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.FLOAT.optionalFieldOf("pull", 0.0F).forGetter(PullState::pull),
			Codec.LONG.optionalFieldOf("grace_until", 0L).forGetter(PullState::graceUntil),
			Codec.BOOL.optionalFieldOf("marked", false).forGetter(PullState::marked),
			Codec.INT.optionalFieldOf("tolerance", 0).forGetter(PullState::tolerance)
	).apply(i, PullState::new));

	public static final StreamCodec<ByteBuf, PullState> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, PullState::pull,
			ByteBufCodecs.VAR_LONG, PullState::graceUntil,
			ByteBufCodecs.BOOL, PullState::marked,
			ByteBufCodecs.VAR_INT, PullState::tolerance,
			PullState::new);

	public PullState {
		pull = Mth.clamp(pull, 0.0F, 100.0F);
		graceUntil = Math.max(0L, graceUntil);
		tolerance = Mth.clamp(tolerance, 0, MAX_TOLERANCE);
	}

	/** Backwards-compatible two-arg constructor used by tests/callers. */
	public PullState(float pull, long graceUntil) {
		this(pull, graceUntil, pull > 0.0F || graceUntil > 0L, 0);
	}

	/** Current Wretch Rejection &amp; Seepage stage (0 = safe, 1..4 = escalating crisis). */
	public int stage() {
		if (pull >= 95.0F) return 4;
		if (pull >= 75.0F) return 3;
		if (pull >= 50.0F) return 2;
		if (pull >= 25.0F) return 1;
		return 0;
	}

	/** Duration granted by a Tier 1 Almond Stabilizer at the given tolerance level (2.0d -> 0.5d). */
	public static long tier1ReliefTicks(int currentTolerance) {
		return switch (Mth.clamp(currentTolerance, 0, MAX_TOLERANCE)) {
			case 0 -> 48_000L; // 2.0 days
			case 1 -> 36_000L; // 1.5 days
			case 2 -> 24_000L; // 1.0 day
			case 3 -> 18_000L; // 0.75 day
			default -> 12_000L; // 0.5 day
		};
	}

	public PullState withPull(float value) {
		return new PullState(value, graceUntil, marked, tolerance);
	}

	public PullState withGraceUntil(long until) {
		return new PullState(pull, until, marked, tolerance);
	}

	public PullState markEscaped(long now) {
		return new PullState(0.0F, now + ESCAPE_GRACE_TICKS, true, tolerance);
	}

	public PullState applyTier1Stabilizer(long now) {
		long duration = tier1ReliefTicks(tolerance);
		int nextTol = Math.min(MAX_TOLERANCE, tolerance + 1);
		return new PullState(0.0F, Math.max(graceUntil, now) + duration, marked, nextTol);
	}

	public PullState applyTier2Serum(long now) {
		int nextTol = Math.max(0, tolerance - 1);
		return new PullState(0.0F, Math.max(graceUntil, now) + SERUM_T2_TICKS, marked, nextTol);
	}

	public PullState applyTier3Core(long now) {
		return new PullState(0.0F, now + CORE_T3_TICKS, false, 0);
	}

	public PullState applyMinorRelief(long now, float severityDrop, long extraTicks) {
		float nextPull = Math.max(0.0F, pull - severityDrop);
		long nextGrace = nextPull <= 0.01F ? Math.max(graceUntil, now + extraTicks) : graceUntil;
		return new PullState(nextPull, nextGrace, marked, tolerance);
	}
}
