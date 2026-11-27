package dev.noclip.backrooms.core.floors;

import dev.noclip.backrooms.BackroomsMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Live dual-axis difficulty telemetry sent from server to a single player while inside the Backrooms:
 * current horizontal band (0..3), distance from landing spot, effective threat multiplier, and anti-farm penalty.
 */
public record DifficultyPayload(boolean active, int band, int distance, float threatMult, int farmPenalty)
		implements CustomPacketPayload {
	public static final DifficultyPayload NONE = new DifficultyPayload(false, 0, 0, 1.0F, 0);
	public static final Type<DifficultyPayload> TYPE = new Type<>(BackroomsMod.id("difficulty_status"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DifficultyPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, DifficultyPayload::active,
			ByteBufCodecs.VAR_INT, DifficultyPayload::band,
			ByteBufCodecs.VAR_INT, DifficultyPayload::distance,
			ByteBufCodecs.FLOAT, DifficultyPayload::threatMult,
			ByteBufCodecs.VAR_INT, DifficultyPayload::farmPenalty,
			DifficultyPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
