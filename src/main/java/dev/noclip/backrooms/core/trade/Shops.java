package dev.noclip.backrooms.core.trade;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.economy.Wallet;
import dev.noclip.backrooms.core.people.Person;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The merchants' shops. Right-click a merchant (a person tagged with a ShopRole) and the server sends their offers;
 * the shop screen asks to trade, and the server checks the player is still at that merchant, has the credits or the
 * goods, and makes the trade. Offers are data: {@code data/noclip_backrooms/shops/<role>.json} with a "sells" list (the
 * merchant sells: item, count, price, and an optional "extra" item cost) and a "buys" list (the merchant buys: item,
 * count, price). Items that do not exist (renamed, removed) are skipped with a warning.
 */
public final class Shops {
	private static final Logger LOGGER = LoggerFactory.getLogger("noclip_backrooms/shops");
	/** How near the merchant a player has to stand to trade. */
	private static final double REACH = 8;
	private static final Map<UUID, Long> LAST_OPEN = new ConcurrentHashMap<>();
	private static Map<ShopRole, List<ShopOffer>> offers;

	public enum Outcome {
		DONE, NO_CREDITS, NO_GOODS, TOO_FAR, NO_SUCH_OFFER, LOCKED, WING_LOCKED
	}

	private Shops() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(ShopOpenPayload.TYPE, ShopOpenPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ShopTradePayload.TYPE, ShopTradePayload.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ShopTradePayload.TYPE, (payload, context) -> {
			ShopRole role = ShopRole.byId(payload.role());
			if (role != null) trade(context.player(), role, payload.index(), Math.clamp(payload.times(), 1, 64));
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(entity instanceof Person)) return InteractionResult.PASS;
			ShopRole role = ShopRole.of(entity);
			if (role == null || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
			// One click reaches the server twice (with and without the spot clicked on): answer once.
			long now = serverPlayer.level().getGameTime();
			Long last = LAST_OPEN.put(serverPlayer.getUUID(), now);
			if (last != null && now - last < 5 && now >= last) return InteractionResult.SUCCESS;
			if (role == ShopRole.MASTER) {
				dev.noclip.backrooms.core.guild.Contracts.talk(serverPlayer);
			} else {
				if (role == ShopRole.MEDIC) Treatment.offer(serverPlayer);
				ServerPlayNetworking.send(serverPlayer, new ShopOpenPayload(role.id(), offers(role)));
			}
			return InteractionResult.SUCCESS;
		});
	}

	/** A merchant's offers, read once from the data files. */
	public static synchronized List<ShopOffer> offers(ShopRole role) {
		if (offers == null) {
			offers = new EnumMap<>(ShopRole.class);
			for (ShopRole each : ShopRole.values()) offers.put(each, load(each));
		}
		return offers.get(role);
	}

	private static List<ShopOffer> load(ShopRole role) {
		String path = "data/" + BackroomsMod.MOD_ID + "/shops/" + role.id() + ".json";
		List<ShopOffer> list = new ArrayList<>();
		try (InputStream stream = Shops.class.getClassLoader().getResourceAsStream(path)) {
			if (stream == null) return List.of();
			JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
			read(root.getAsJsonArray("sells"), false, list, path);
			read(root.getAsJsonArray("buys"), true, list, path);
		} catch (Exception e) {
			LOGGER.error("Could not read {}", path, e);
		}
		return List.copyOf(list);
	}

	private static void read(JsonArray array, boolean fromPlayer, List<ShopOffer> into, String path) {
		if (array == null) return;
		for (JsonElement element : array) {
			JsonObject o = element.getAsJsonObject();
			ItemStack goods = stack(o.get("item").getAsString(), o.has("count") ? o.get("count").getAsInt() : 1, path);
			if (goods.isEmpty()) continue;
			ItemStack extra = ItemStack.EMPTY;
			if (o.has("extra")) {
				JsonObject e = o.getAsJsonObject("extra");
				extra = stack(e.get("item").getAsString(), e.has("count") ? e.get("count").getAsInt() : 1, path);
				if (extra.isEmpty()) continue;
			}
			into.add(new ShopOffer(goods, o.get("price").getAsInt(), extra, fromPlayer));
		}
	}

	private static ItemStack stack(String id, int count, String path) {
		Item item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).orElse(null);
		if (item == null) {
			LOGGER.warn("{}: no item {}, offer left out", path, id);
			return ItemStack.EMPTY;
		}
		return new ItemStack(item, count);
	}

	/** Makes a trade, if the player stands at that merchant and can pay; tells them how it went. */
	public static Outcome trade(ServerPlayer player, ShopRole role, int index, int times) {
		Outcome outcome = tryTrade(player, role, index, times);
		if (outcome == Outcome.DONE) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 0.5F, 1.2F);
		} else if (outcome != Outcome.NO_SUCH_OFFER) {
			player.sendOverlayMessage(Component.translatable("shop.noclip_backrooms." + outcome.name().toLowerCase()));
		}
		return outcome;
	}

	private static Outcome tryTrade(ServerPlayer player, ShopRole role, int index, int times) {
		List<ShopOffer> list = offers(role);
		if (index < 0 || index >= list.size()) return Outcome.NO_SUCH_OFFER;
		boolean near = !player.level().getEntitiesOfClass(Person.class, player.getBoundingBox().inflate(REACH),
				p -> p.entityTags().contains(role.tag())).isEmpty();
		if (!near) return Outcome.TOO_FAR;
		// At the institute, the medic and the quartermaster only serve a team that has met enough quotas.
		if (role.clearance() > 0 && player.level().dimension() == dev.noclip.backrooms.core.Home.FRONTROOMS
				&& dev.noclip.backrooms.core.quota.Quota.clearance(player.level().getServer()) < role.clearance()) return Outcome.WING_LOCKED;
		// A team in debt to M.E.G. finds the outpost closed, all but the buyer (selling is how the debt gets paid).
		if (role != ShopRole.BUYER && dev.noclip.backrooms.core.quota.Quota.locked(player.level().getServer())) return Outcome.LOCKED;
		ShopOffer offer = list.get(index);
		Inventory inventory = player.getInventory();
		long price = (long) offer.price() * times;
		if (offer.fromPlayer() && dev.noclip.backrooms.core.field.Evidence.valued(offer.goods().getItem())) {
			// Photos and tapes: each is worth what is in it (core.field.Evidence).
			long total = 0;
			int sold = 0;
			for (int n = 0; n < times; n++) {
				int slot = -1;
				for (int i = 0; i < inventory.getContainerSize() && slot < 0; i++) if (inventory.getItem(i).is(offer.goods().getItem())) slot = i;
				if (slot < 0) break;
				ItemStack stack = inventory.getItem(slot).copy();
				inventory.removeItem(slot, 1);
				total += dev.noclip.backrooms.core.field.Evidence.value(player.level().getServer(), stack);
				dev.noclip.backrooms.core.field.Evidence.sold(player.level().getServer(), player, stack);
				sold++;
			}
			if (sold == 0) return Outcome.NO_GOODS;
			long credits = dev.noclip.backrooms.core.quota.Quota.onSold(player, offer.goods().getItem(), sold, total);
			if (credits > 0) Wallet.add(player, credits);
			return Outcome.DONE;
		}
		if (offer.fromPlayer()) {
			int needed = offer.goods().getCount() * times;
			if (count(inventory, offer.goods()) < needed) return Outcome.NO_GOODS;
			remove(inventory, offer.goods(), needed);
			// Selling to the buyer is handing things in to M.E.G.: debt first, then the quota; only the rest is paid out.
			long credits = dev.noclip.backrooms.core.quota.Quota.onSold(player, offer.goods().getItem(), needed, price);
			if (credits > 0) Wallet.add(player, credits);
			return Outcome.DONE;
		}
		if (Wallet.of(player) < price) return Outcome.NO_CREDITS;
		int extra = offer.extra().getCount() * times;
		if (!offer.extra().isEmpty() && count(inventory, offer.extra()) < extra) return Outcome.NO_GOODS;
		Wallet.take(player, price);
		if (!offer.extra().isEmpty()) remove(inventory, offer.extra(), extra);
		for (int i = 0; i < times; i++) {
			ItemStack goods = offer.goods().copy();
			if (!inventory.add(goods)) player.drop(goods, false);
		}
		return Outcome.DONE;
	}

	/** How many of an item the inventory holds. */
	public static int count(Inventory inventory, ItemStack like) {
		int total = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(like.getItem())) total += stack.getCount();
		}
		return total;
	}

	private static void remove(Inventory inventory, ItemStack like, int amount) {
		for (int i = 0; i < inventory.getContainerSize() && amount > 0; i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.is(like.getItem())) continue;
			int take = Math.min(amount, stack.getCount());
			stack.shrink(take);
			amount -= take;
		}
		inventory.setChanged();
	}
}
