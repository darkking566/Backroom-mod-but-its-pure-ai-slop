package dev.noclip.backrooms.frontrooms;

import dev.noclip.backrooms.BackroomsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Whether the Frontrooms' night is Level 398's (Nights398), for the darker, redder look of it (client Night398Client). */
public record Night398Payload(boolean active) implements CustomPacketPayload {
	public static final Type<Night398Payload> TYPE = new Type<>(BackroomsMod.id("night_398"));
	public static final StreamCodec<ByteBuf, Night398Payload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, Night398Payload::active, Night398Payload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
