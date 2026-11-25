package dev.noclip.backrooms.core.trade;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * One line of a shop. When {@code fromPlayer} is false the merchant sells {@code goods} for {@code price} credits (and
 * {@code extra} too, when not empty: the spec's "money and a rare material" for the best gear); when true the merchant
 * buys {@code goods} from the player for {@code price} credits.
 */
public record ShopOffer(ItemStack goods, int price, ItemStack extra, boolean fromPlayer) {
	public static final StreamCodec<RegistryFriendlyByteBuf, ShopOffer> STREAM_CODEC = StreamCodec.composite(
			ItemStack.STREAM_CODEC, ShopOffer::goods,
			ByteBufCodecs.VAR_INT, ShopOffer::price,
			ItemStack.OPTIONAL_STREAM_CODEC, ShopOffer::extra,
			ByteBufCodecs.BOOL, ShopOffer::fromPlayer,
			ShopOffer::new);
}
