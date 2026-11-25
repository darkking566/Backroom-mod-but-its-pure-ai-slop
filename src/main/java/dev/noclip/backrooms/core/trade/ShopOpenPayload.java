package dev.noclip.backrooms.core.trade;

import dev.noclip.backrooms.BackroomsMod;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: open this merchant's shop with these offers. */
public record ShopOpenPayload(String role, List<ShopOffer> offers) implements CustomPacketPayload {
	public static final Type<ShopOpenPayload> TYPE = new Type<>(BackroomsMod.id("shop_open"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ShopOpenPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, ShopOpenPayload::role,
			ShopOffer.STREAM_CODEC.apply(ByteBufCodecs.list()), ShopOpenPayload::offers,
			ShopOpenPayload::new);

	@Override
	public Type<ShopOpenPayload> type() {
		return TYPE;
	}
}
