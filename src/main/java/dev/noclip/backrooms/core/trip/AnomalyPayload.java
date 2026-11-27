package dev.noclip.backrooms.core.trip;

import dev.noclip.backrooms.BackroomsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * This trip's anomaly (core.trip.Anomalies), for the HUD and localized red-mist haze; empty outside the Backrooms or
 * with none. {@code inZone} is true only while the player stands inside a localized Red Mist enclave zone.
 */
public record AnomalyPayload(String affix, boolean inZone) implements CustomPacketPayload {
	public static final Type<AnomalyPayload> TYPE = new Type<>(BackroomsMod.id("anomaly"));
	public static final StreamCodec<ByteBuf, AnomalyPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, AnomalyPayload::affix,
			ByteBufCodecs.BOOL, AnomalyPayload::inZone,
			AnomalyPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
