package dev.noclip.backrooms.weapons;

import dev.noclip.backrooms.BackroomsMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client-to-server request to fire the Backrooms firearm held in main hand (left-click trigger). */
public record FireWeaponPayload(boolean aiming) implements CustomPacketPayload {
	public static final Type<FireWeaponPayload> TYPE = new Type<>(BackroomsMod.id("fire_weapon"));
	public static final StreamCodec<RegistryFriendlyByteBuf, FireWeaponPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL,
			FireWeaponPayload::aiming,
			FireWeaponPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
