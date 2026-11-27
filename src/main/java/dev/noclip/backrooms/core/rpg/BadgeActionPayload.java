package dev.noclip.backrooms.core.rpg;

import dev.noclip.backrooms.BackroomsMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client-to-server request to equip/unequip or unlock a specialization badge. */
public record BadgeActionPayload(String action, String badgeId) implements CustomPacketPayload {
	public static final Type<BadgeActionPayload> TYPE = new Type<>(BackroomsMod.id("badge_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, BadgeActionPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, BadgeActionPayload::action,
			ByteBufCodecs.STRING_UTF8, BadgeActionPayload::badgeId,
			BadgeActionPayload::new
	);

	@Override
	public Type<BadgeActionPayload> type() {
		return TYPE;
	}
}
