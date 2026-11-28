package dev.noclip.backrooms.core.story;

import dev.noclip.backrooms.BackroomsMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: action triggered from MissionBoardScreen. */
public record MissionActionPayload(String action, String arg) implements CustomPacketPayload {
	public static final Type<MissionActionPayload> TYPE = new Type<>(BackroomsMod.id("mission_action"));

	public static final StreamCodec<FriendlyByteBuf, MissionActionPayload> STREAM_CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeUtf(p.action);
				buf.writeUtf(p.arg);
			},
			buf -> new MissionActionPayload(buf.readUtf(), buf.readUtf()));

	@Override
	public Type<MissionActionPayload> type() {
		return TYPE;
	}
}
