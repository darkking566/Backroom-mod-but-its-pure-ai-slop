package dev.noclip.backrooms.core.rpg;

import dev.noclip.backrooms.BackroomsMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client-to-server request to spend 1 unallocated RPG stat point from the inventory panel. */
public record AllocateStatPayload(PlayerStats.StatKind stat) implements CustomPacketPayload {
	public static final Type<AllocateStatPayload> TYPE = new Type<>(BackroomsMod.id("allocate_stat"));

	public static final StreamCodec<RegistryFriendlyByteBuf, AllocateStatPayload> STREAM_CODEC = StreamCodec.of(
			(buf, payload) -> buf.writeByte(payload.stat().ordinal()),
			buf -> new AllocateStatPayload(PlayerStats.StatKind.fromOrdinal(buf.readByte()))
	);

	@Override
	public Type<AllocateStatPayload> type() {
		return TYPE;
	}
}
