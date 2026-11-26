package dev.noclip.backrooms.core.locker;

import dev.noclip.backrooms.BackroomsMod;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Syncs the team's unlocked research-pack tiers to clients so workstation recipe books show lock status accurately. */
public record ResearchPayload(List<String> unlocked) implements CustomPacketPayload {
	public static final Type<ResearchPayload> TYPE = new Type<>(BackroomsMod.id("research_state"));
	public static final StreamCodec<FriendlyByteBuf, ResearchPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), ResearchPayload::unlocked,
			ResearchPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
