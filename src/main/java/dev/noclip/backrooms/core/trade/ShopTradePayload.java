package dev.noclip.backrooms.core.trade;

import dev.noclip.backrooms.BackroomsMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: trade offer {@code index} of this merchant, {@code times} times. */
public record ShopTradePayload(String role, int index, int times) implements CustomPacketPayload {
	public static final Type<ShopTradePayload> TYPE = new Type<>(BackroomsMod.id("shop_trade"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ShopTradePayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, ShopTradePayload::role,
			ByteBufCodecs.VAR_INT, ShopTradePayload::index,
			ByteBufCodecs.VAR_INT, ShopTradePayload::times,
			ShopTradePayload::new);

	@Override
	public Type<ShopTradePayload> type() {
		return TYPE;
	}
}
