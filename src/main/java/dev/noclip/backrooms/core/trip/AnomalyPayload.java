package dev.noclip.backrooms.core.trip;

import dev.noclip.backrooms.BackroomsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** This trip's anomaly (core.trip.Anomalies), for the HUD and the red mist; empty outside the Backrooms or with none. */
public record AnomalyPayload(String affix) implements CustomPacketPayload {
	public static final Type<AnomalyPayload> TYPE = new Type<>(BackroomsMod.id("anomaly"));
	public static final StreamCodec<ByteBuf, AnomalyPayload> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(AnomalyPayload::new, AnomalyPayload::affix);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
