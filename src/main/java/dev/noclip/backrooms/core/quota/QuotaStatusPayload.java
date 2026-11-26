package dev.noclip.backrooms.core.quota;

import dev.noclip.backrooms.BackroomsMod;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: the team's M.E.G. cycle, for the Overworld HUD (core.quota.QuotaClient). */
public record QuotaStatusPayload(boolean active, int day, int daysLeft, long delivered, long required, long debt, boolean locked,
		List<Need> items) implements CustomPacketPayload {
	public static final Type<QuotaStatusPayload> TYPE = new Type<>(BackroomsMod.id("quota_status"));

	/** A required item: its id, how many are in, how many are wanted. */
	public record Need(String item, int handed, int needed) {
		static final StreamCodec<ByteBuf, Need> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, Need::item,
				ByteBufCodecs.VAR_INT, Need::handed,
				ByteBufCodecs.VAR_INT, Need::needed,
				Need::new);
	}

	public static final StreamCodec<ByteBuf, QuotaStatusPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, QuotaStatusPayload::active,
			ByteBufCodecs.VAR_INT, QuotaStatusPayload::day,
			ByteBufCodecs.VAR_INT, QuotaStatusPayload::daysLeft,
			ByteBufCodecs.VAR_LONG, QuotaStatusPayload::delivered,
			ByteBufCodecs.VAR_LONG, QuotaStatusPayload::required,
			ByteBufCodecs.VAR_LONG, QuotaStatusPayload::debt,
			ByteBufCodecs.BOOL, QuotaStatusPayload::locked,
			Need.STREAM_CODEC.apply(ByteBufCodecs.list()), QuotaStatusPayload::items,
			QuotaStatusPayload::new);

	public static final QuotaStatusPayload NONE = new QuotaStatusPayload(false, 0, 0, 0, 0, 0, false, List.of());

	@Override
	public Type<QuotaStatusPayload> type() {
		return TYPE;
	}
}
