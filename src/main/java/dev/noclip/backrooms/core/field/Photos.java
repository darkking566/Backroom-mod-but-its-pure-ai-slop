package dev.noclip.backrooms.core.field;

import dev.noclip.backrooms.core.quota.Quota;
import dev.noclip.backrooms.core.quota.QuotaMath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The pictures on the camera's photos (docs/企劃書/1-遊戲玩法/README.md, "高風險現場取證"): the photographer's own
 * screen, kept in the world save under the photo's id, looked at by using the photo, and carried in the album. A photo
 * sold is gone, picture and all. What a photo is worth stays decided by the server (core.field.Evidence).
 */
public final class Photos {
	public static final int ALBUM_SIZE = 24;
	private static final Logger LOG = LoggerFactory.getLogger(Photos.class);
	/** How long after the shutter the picture is still accepted, in ticks. */
	private static final int UPLOAD_TICKS = 200;
	private static final String ID_KEY = "photo";

	private record Pending(UUID player, int tick) {
	}

	private static final Map<UUID, Pending> PENDING = new HashMap<>();

	private Photos() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(PhotoPayloads.Capture.TYPE, PhotoPayloads.Capture.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PhotoPayloads.Image.TYPE, PhotoPayloads.Image.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PhotoPayloads.View.TYPE, PhotoPayloads.View.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(PhotoPayloads.Shoot.TYPE, PhotoPayloads.Shoot.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(PhotoPayloads.Shoot.TYPE, (payload, context) -> Evidence.shoot(context.player(), payload.zoom()));
		PayloadTypeRegistry.serverboundPlay().register(PhotoPayloads.Upload.TYPE, PhotoPayloads.Upload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(PhotoPayloads.Request.TYPE, PhotoPayloads.Request.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(PhotoPayloads.Take.TYPE, PhotoPayloads.Take.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(PhotoPayloads.Upload.TYPE,
				(payload, context) -> upload(context.player(), payload.photo(), payload.pixels()));
		ServerPlayNetworking.registerGlobalReceiver(PhotoPayloads.Request.TYPE, (payload, context) -> {
			byte[] pixels = load(context.player().level().getServer(), payload.photo());
			if (pixels != null) ServerPlayNetworking.send(context.player(), new PhotoPayloads.Image(payload.photo(), pixels));
		});
		ServerPlayNetworking.registerGlobalReceiver(PhotoPayloads.Take.TYPE, (payload, context) -> take(context.player(), payload.index()));
		ServerLifecycleEvents.SERVER_STARTED.register(server -> PENDING.clear());
	}

	/** Gives a fresh photo its id and the day, and asks the photographer's client for the picture. */
	static void shot(ServerPlayer player, ItemStack photo, CompoundTag tag) {
		UUID id = UUID.randomUUID();
		tag.putString(ID_KEY, id.toString());
		MinecraftServer server = player.level().getServer();
		tag.putInt("day", QuotaMath.day(Quota.data(server).homeTicks()));
		photo.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		PENDING.values().removeIf(p -> server.getTickCount() - p.tick() > UPLOAD_TICKS);
		PENDING.put(id, new Pending(player.getUUID(), server.getTickCount()));
		if (ServerPlayNetworking.canSend(player, PhotoPayloads.Capture.TYPE)) {
			ServerPlayNetworking.send(player, new PhotoPayloads.Capture(id));
		}
	}

	/** A picture coming up from a client: only the one it was just asked for, once, and of the right size. */
	private static void upload(ServerPlayer player, UUID id, byte[] pixels) {
		Pending pending = PENDING.get(id);
		MinecraftServer server = player.level().getServer();
		if (pending == null || !pending.player().equals(player.getUUID()) || server.getTickCount() - pending.tick() > UPLOAD_TICKS
				|| pixels.length != PhotoImage.BYTES) return;
		PENDING.remove(id);
		try {
			Path file = file(server, id);
			Files.createDirectories(file.getParent());
			Files.write(file, pixels);
		} catch (IOException e) {
			LOG.warn("Could not keep photo {}", id, e);
		}
	}

	// shortcut: a photo destroyed other than by selling (lava, despawning) leaves its 31 KB file; clean up if saves grow.
	private static Path file(MinecraftServer server, UUID id) {
		return server.getWorldPath(LevelResource.ROOT).resolve("noclip_backrooms_photos").resolve(id + ".rgb565");
	}

	/** The stored picture, or null. */
	public static byte[] load(MinecraftServer server, UUID id) {
		try {
			Path file = file(server, id);
			if (!Files.isRegularFile(file) || Files.size(file) != PhotoImage.BYTES) return null;
			return Files.readAllBytes(file);
		} catch (IOException e) {
			LOG.warn("Could not read photo {}", id, e);
			return null;
		}
	}

	/** The photo's picture id, or null (photos taken before pictures were kept). */
	public static UUID id(ItemStack photo) {
		CustomData data = photo.get(DataComponents.CUSTOM_DATA);
		String id = data == null ? "" : data.copyTag().getStringOr(ID_KEY, "");
		try {
			return id.isEmpty() ? null : UUID.fromString(id);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** Sold: the picture goes with it. */
	static void forget(MinecraftServer server, ItemStack photo) {
		UUID id = id(photo);
		if (id == null) return;
		PENDING.remove(id);
		try {
			Files.deleteIfExists(file(server, id));
		} catch (IOException e) {
			LOG.warn("Could not delete photo {}", id, e);
		}
	}

	/** The photos in an album. */
	public static List<ItemStack> contents(ItemStack album) {
		return new ArrayList<>(album.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemCopyStream().toList());
	}

	private static void setContents(ItemStack album, List<ItemStack> photos) {
		album.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(photos));
	}

	/** Moves every loose photo carried into the album, as far as it has room. */
	private static void store(ServerPlayer player, ItemStack album) {
		List<ItemStack> photos = contents(album);
		Inventory inventory = player.getInventory();
		int stored = 0;
		for (int i = 0; i < inventory.getContainerSize() && photos.size() < ALBUM_SIZE; i++) {
			if (!inventory.getItem(i).is(FieldKit.PHOTO)) continue;
			photos.add(inventory.removeItemNoUpdate(i));
			stored++;
		}
		if (stored > 0) setContents(album, photos);
		player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.field.album_stored", stored, photos.size(), ALBUM_SIZE)
				.withStyle(ChatFormatting.WHITE));
	}

	/** Takes one photo back out of the album in hand, to show or to sell. */
	public static void take(ServerPlayer player, int index) {
		ItemStack album = player.getMainHandItem().is(FieldKit.ALBUM) ? player.getMainHandItem() : player.getOffhandItem();
		if (!album.is(FieldKit.ALBUM)) return;
		List<ItemStack> photos = contents(album);
		if (index < 0 || index >= photos.size()) return;
		ItemStack photo = photos.remove(index);
		setContents(album, photos);
		if (!player.getInventory().add(photo)) player.drop(photo, false);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
		ServerPlayNetworking.send(player, new PhotoPayloads.View(true, photos));
	}

	/** A photo: use it to look at it. */
	static final class Photo extends Item {
		Photo(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (player instanceof ServerPlayer serverPlayer) {
				ServerPlayNetworking.send(serverPlayer, new PhotoPayloads.View(false, List.of(player.getItemInHand(hand).copy())));
			}
			return InteractionResult.SUCCESS;
		}
	}

	/** The album: use it to look through it; sneaking, to put away the loose photos carried. */
	static final class Album extends Item {
		Album(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (player instanceof ServerPlayer serverPlayer) {
				ItemStack album = player.getItemInHand(hand);
				if (player.isShiftKeyDown()) store(serverPlayer, album);
				else ServerPlayNetworking.send(serverPlayer, new PhotoPayloads.View(true, contents(album)));
			}
			return InteractionResult.SUCCESS;
		}
	}
}
