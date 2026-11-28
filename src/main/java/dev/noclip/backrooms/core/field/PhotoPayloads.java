package dev.noclip.backrooms.core.field;

import dev.noclip.backrooms.BackroomsMod;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/** The packets of the camera's pictures (core.field.Photos). */
public final class PhotoPayloads {
	private PhotoPayloads() {
	}

	private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> id(String name) {
		return new CustomPacketPayload.Type<>(BackroomsMod.id(name));
	}

	/** Server to client: the shutter went, send what you see as photo {@code photo}. */
	public record Capture(UUID photo) implements CustomPacketPayload {
		public static final Type<Capture> TYPE = id("photo_capture");
		public static final StreamCodec<RegistryFriendlyByteBuf, Capture> CODEC = StreamCodec.composite(
				UUIDUtil.STREAM_CODEC, Capture::photo, Capture::new);

		@Override
		public Type<Capture> type() {
			return TYPE;
		}
	}

	/** Client to server: the shot just taken. */
	public record Upload(UUID photo, byte[] pixels) implements CustomPacketPayload {
		public static final Type<Upload> TYPE = id("photo_upload");
		public static final StreamCodec<RegistryFriendlyByteBuf, Upload> CODEC = StreamCodec.composite(
				UUIDUtil.STREAM_CODEC, Upload::photo, ByteBufCodecs.byteArray(PhotoImage.BYTES), Upload::pixels, Upload::new);

		@Override
		public Type<Upload> type() {
			return TYPE;
		}
	}

	/** Client to server: what is on photo {@code photo}? */
	public record Request(UUID photo) implements CustomPacketPayload {
		public static final Type<Request> TYPE = id("photo_request");
		public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.composite(
				UUIDUtil.STREAM_CODEC, Request::photo, Request::new);

		@Override
		public Type<Request> type() {
			return TYPE;
		}
	}

	/** Server to client: the stored picture. */
	public record Image(UUID photo, byte[] pixels) implements CustomPacketPayload {
		public static final Type<Image> TYPE = id("photo_image");
		public static final StreamCodec<RegistryFriendlyByteBuf, Image> CODEC = StreamCodec.composite(
				UUIDUtil.STREAM_CODEC, Image::photo, ByteBufCodecs.byteArray(PhotoImage.BYTES), Image::pixels, Image::new);

		@Override
		public Type<Image> type() {
			return TYPE;
		}
	}

	/** Server to client: look at these photos ({@code album}: they are in the album held, and can be taken out). */
	public record View(boolean album, List<ItemStack> photos) implements CustomPacketPayload {
		public static final Type<View> TYPE = id("photo_view");
		public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = StreamCodec.composite(
				ByteBufCodecs.BOOL, View::album, ItemStack.OPTIONAL_LIST_STREAM_CODEC, View::photos, View::new);

		@Override
		public Type<View> type() {
			return TYPE;
		}
	}

	/** Client to server: take the photo at {@code index} out of the album held. */
	public record Take(int index) implements CustomPacketPayload {
		public static final Type<Take> TYPE = id("album_take");
		public static final StreamCodec<RegistryFriendlyByteBuf, Take> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Take::index, Take::new);

		@Override
		public Type<Take> type() {
			return TYPE;
		}
	}
}
