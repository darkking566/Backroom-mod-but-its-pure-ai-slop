package dev.noclip.backrooms.core.locker;

import dev.noclip.backrooms.BackroomsMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client-to-server request from the inventory Safebox button to open the player's personal safebox. */
public record SafeboxOpenPayload() implements CustomPacketPayload {
	public static final SafeboxOpenPayload INSTANCE = new SafeboxOpenPayload();
	public static final Type<SafeboxOpenPayload> TYPE = new Type<>(BackroomsMod.id("open_safebox"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SafeboxOpenPayload> STREAM_CODEC =
			StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
