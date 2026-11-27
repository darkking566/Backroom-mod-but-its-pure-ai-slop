package dev.noclip.backrooms.core.trip;

import dev.noclip.backrooms.core.floors.FloorRun;
import dev.noclip.backrooms.core.quota.Quota;
import dev.noclip.backrooms.core.quota.QuotaData;
import dev.noclip.backrooms.core.quota.QuotaMath;
import dev.noclip.backrooms.core.trade.ShopOffer;
import dev.noclip.backrooms.core.trade.ShopRole;
import dev.noclip.backrooms.core.trade.Shops;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The mission report (docs/企劃書/1-遊戲玩法/README.md, "任務報告畫面"), sent a moment after someone gets back to
 * the institute, when their screen has the new place loaded: like the end of a day in Lethal Company.
 */
public final class Report {
	/** How long after arriving the report comes up, in ticks. */
	static final int DELAY = 30;
	private static final Map<UUID, Pending> PENDING = new HashMap<>();
	/** Something with news for a report: work done while the player was away, and so on. */
	@FunctionalInterface
	public interface Notes {
		void add(ServerPlayer player, FloorRun trip, List<Component> notes);
	}

	private static final List<Notes> NOTES = new ArrayList<>();
	/** The last report sent to each player, for tests. */
	private static final Map<UUID, MissionReportPayload> LAST = new HashMap<>();

	private record Pending(FloorRun run, int at) {
	}

	private Report() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(MissionReportPayload.TYPE, MissionReportPayload.STREAM_CODEC);
		ServerTickEvents.END_SERVER_TICK.register(Report::tick);
	}

	/** Something else that has news for the report (work finished while the player was away, and so on). */
	public static void onReport(Notes notes) {
		NOTES.add(notes);
	}

	static void send(ServerPlayer player, FloorRun run) {
		PENDING.put(player.getUUID(), new Pending(run, player.level().getServer().getTickCount() + DELAY));
	}

	public static MissionReportPayload last(ServerPlayer player) {
		return LAST.get(player.getUUID());
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Pending> entry = it.next();
			if (server.getTickCount() < entry.getValue().at()) continue;
			it.remove();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) continue;
			MissionReportPayload report = build(player, entry.getValue().run());
			LAST.put(player.getUUID(), report);
			ServerPlayNetworking.send(player, report);
		}
	}

	static MissionReportPayload build(ServerPlayer player, FloorRun run) {
		// What the buyer would pay for what came back, best first.
		Map<Item, long[]> rates = new HashMap<>();
		for (ShopOffer offer : Shops.offers(ShopRole.BUYER)) {
			if (offer.fromPlayer()) rates.putIfAbsent(offer.goods().getItem(), new long[] {offer.goods().getCount(), offer.price()});
		}
		Map<Item, Integer> carried = new LinkedHashMap<>();
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && rates.containsKey(stack.getItem())) carried.merge(stack.getItem(), stack.getCount(), Integer::sum);
		}
		List<Map.Entry<Item, Long>> worth = new ArrayList<>();
		long total = 0;
		for (var e : carried.entrySet()) {
			long[] rate = rates.get(e.getKey());
			long value = e.getValue() / rate[0] * rate[1];
			if (value <= 0) continue;
			worth.add(Map.entry(e.getKey(), value));
			total += value;
		}
		worth.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
		List<ItemStack> haul = new ArrayList<>();
		List<Long> values = new ArrayList<>();
		for (int i = 0; i < Math.min(8, worth.size()); i++) {
			haul.add(new ItemStack(worth.get(i).getKey(), carried.get(worth.get(i).getKey())));
			values.add(worth.get(i).getValue());
		}
		List<Component> notes = new ArrayList<>();
		for (var source : NOTES) source.add(player, run, notes);
		MinecraftServer server = player.level().getServer();
		QuotaData d = Quota.data(server);
		return new MissionReportPayload(run.floor(), run.visited(), haul, values, total, d.active(), QuotaMath.day(d.homeTicks()),
				QuotaMath.daysLeft(d.homeTicks()), d.delivered(), d.required(), d.debt(), Quota.clearance(server), notes);
	}
}
