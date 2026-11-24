package dev.noclip.backrooms.weapons;

import dev.noclip.backrooms.BackroomsMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server-to-client hit confirmation packet for crosshair Hitmarker feedback (white on hit, crimson on kill). */
public record WeaponHitPayload(boolean killed) implements CustomPacketPayload {
	public static final Type<WeaponHitPayload> TYPE = new Type<>(BackroomsMod.id("weapon_hit"));
	public static final StreamCodec<RegistryFriendlyByteBuf, WeaponHitPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL,
			WeaponHitPayload::killed,
			WeaponHitPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
