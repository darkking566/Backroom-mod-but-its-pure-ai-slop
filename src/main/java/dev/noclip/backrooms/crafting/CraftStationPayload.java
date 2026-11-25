package dev.noclip.backrooms.crafting;

import dev.noclip.backrooms.BackroomsMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client-to-server request to craft a selected recipe in an open Backrooms workstation menu. */
public record CraftStationPayload(Identifier recipeId, boolean craftBatch) implements CustomPacketPayload {
	public static final Type<CraftStationPayload> TYPE = new Type<>(BackroomsMod.id("craft_station_recipe"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CraftStationPayload> STREAM_CODEC = StreamCodec.composite(
			Identifier.STREAM_CODEC,
			CraftStationPayload::recipeId,
			ByteBufCodecs.BOOL,
			CraftStationPayload::craftBatch,
			CraftStationPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
