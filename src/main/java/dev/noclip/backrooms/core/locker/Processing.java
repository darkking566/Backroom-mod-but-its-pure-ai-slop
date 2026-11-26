package dev.noclip.backrooms.core.locker;

import com.mojang.serialization.Codec;
import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.Home;
import dev.noclip.backrooms.core.floors.FloorRun;
import dev.noclip.backrooms.core.trip.Report;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The institute's machines work while you are away (docs/project/1-gameplay/README.md, "按趟結算的工業加工與研究"):
 * sneak and use one to load it before a trip; when you next get back alive, it has done its work, better the deeper the
 * trip went, and what it made waits in your locker (and in the mission report). Nothing runs on the clock, so the five
 * days are not spent waiting on machines.
 * <ul>
 * <li>scrap recycler: anything salvaged becomes iron, copper, redstone and, from the second floor, battery cells;</li>
 * <li>distillery barrel: almond water ages into reserve (two bottles for one, three below the second floor);</li>
 * <li>research terminal: research packs become blueprint pages, more of them the deeper the trip;</li>
 * <li>crystal replicator: rare cores and shards come back with copies, one for every three floors (two at most).</li>
 * </ul>
 */
public final class Processing {
	public static final int SLOTS = 27;
	private static final Codec<Map<String, List<ItemStack>>> QUEUES = Codec.unboundedMap(Codec.STRING, ItemStack.OPTIONAL_CODEC.listOf());
	/** What each player has loaded into each machine (machine id -> stacks). */
	public static final AttachmentType<Map<String, List<ItemStack>>> LOADED = AttachmentRegistry.create(BackroomsMod.id("processing"),
			builder -> builder.persistent(QUEUES).copyOnDeath());
	static final List<String> MACHINES = List.of("scrap_recycler", "anomaly_distillery_barrel", "anomaly_research_terminal", "crystal_replicator");
	static final List<String> RARE = List.of("noclip_shard", "fire_salt", "smiler_eyeball", "core_alpha_fang");

	private Processing() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || !player.isShiftKeyDown() || level.dimension() != Home.FRONTROOMS) return InteractionResult.PASS;
			String machine = BuiltInRegistries.BLOCK.getKey(level.getBlockState(hit.getBlockPos()).getBlock()).getPath();
			if (!MACHINES.contains(machine) || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
			open(serverPlayer, machine);
			return InteractionResult.SUCCESS;
		});
		Report.onReport(Processing::settle);
	}

	/** What this player has loaded into a machine. */
	public static List<ItemStack> loaded(ServerPlayer player, String machine) {
		List<ItemStack> list = new ArrayList<>(player.getAttachedOrElse(LOADED, Map.of()).getOrDefault(machine, List.of()));
		while (list.size() < SLOTS) list.add(ItemStack.EMPTY);
		return list;
	}

	static void store(ServerPlayer player, String machine, List<ItemStack> stacks) {
		Map<String, List<ItemStack>> all = new HashMap<>(player.getAttachedOrElse(LOADED, Map.of()));
		boolean empty = stacks.stream().allMatch(ItemStack::isEmpty);
		if (empty) all.remove(machine);
		else all.put(machine, List.copyOf(stacks));
		if (all.isEmpty()) player.removeAttached(LOADED);
		else player.setAttached(LOADED, all);
	}

	/** Loads a machine: three rows to put things in, kept until the next time the player gets back alive. */
	public static void open(ServerPlayer player, String machine) {
		List<ItemStack> stacks = loaded(player, machine);
		SimpleContainer container = new SimpleContainer(SLOTS) {
			@Override
			public void setChanged() {
				super.setChanged();
				List<ItemStack> now = new ArrayList<>();
				for (int i = 0; i < getContainerSize(); i++) now.add(getItem(i).copy());
				store(player, machine, now);
			}
		};
		for (int i = 0; i < SLOTS; i++) container.setItem(i, stacks.get(i));
		player.openMenu(new SimpleMenuProvider((id, inventory, p) -> ChestMenu.threeRows(id, inventory, container),
				Component.translatable("container.noclip_backrooms.processing", Component.translatable("block.noclip_backrooms." + machine))));
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.processing.hint").withStyle(ChatFormatting.GRAY));
	}

	/** Back alive: every loaded machine has done its work; the results go into the locker, and into the report. */
	static void settle(ServerPlayer player, FloorRun trip, List<Component> notes) {
		int floor = Math.max(1, trip.floor());
		Map<String, List<ItemStack>> all = player.getAttachedOrElse(LOADED, Map.of());
		if (all.isEmpty()) return;
		List<ItemStack> locker = Lockers.contents(player);
		List<ItemStack> relics = Lockers.relics(player);
		for (String machine : MACHINES) {
			List<ItemStack> in = all.get(machine);
			if (in == null) continue;
			Map<Item, Integer> out = work(machine, in, floor);
			if (out.isEmpty()) continue;
			StringBuilder summary = new StringBuilder();
			for (var e : out.entrySet()) {
				ItemStack stack = new ItemStack(e.getKey(), e.getValue());
				if (summary.length() > 0) summary.append(", ");
				summary.append(stack.getHoverName().getString()).append(" ×").append(e.getValue());
				int left = e.getValue();
				while (left > 0) {
					int n = Math.min(left, stack.getMaxStackSize());
					ItemStack rest = Lockers.insert(locker, new ItemStack(e.getKey(), n));
					if (!rest.isEmpty()) relics.add(rest);
					left -= n;
				}
			}
			notes.add(Component.translatable("report.noclip_backrooms.processed", Component.translatable("block.noclip_backrooms." + machine), summary.toString()));
		}
		player.setAttached(Lockers.LOCKER, List.copyOf(locker));
		if (relics.isEmpty()) player.removeAttached(Lockers.RELICS);
		else player.setAttached(Lockers.RELICS, List.copyOf(relics));
		player.removeAttached(LOADED);
	}

	/** What a machine makes of what was loaded into it, on a trip this deep. Things it has no use for come back as they were. */
	static Map<Item, Integer> work(String machine, List<ItemStack> in, int floor) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		int mass = 0, almond = 0, packs = 0;
		for (ItemStack stack : in) {
			if (stack.isEmpty()) continue;
			String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
			switch (machine) {
				case "scrap_recycler" -> mass += stack.getCount();
				case "anomaly_distillery_barrel" -> {
					if (id.equals("almond_water")) almond += stack.getCount();
					else add(out, stack.getItem(), stack.getCount());
				}
				case "anomaly_research_terminal" -> {
					if (id.startsWith("science_pack_")) packs += stack.getCount();
					else add(out, stack.getItem(), stack.getCount());
				}
				default -> {
					add(out, stack.getItem(), stack.getCount());
					if (RARE.contains(id)) add(out, stack.getItem(), Math.min(2, floor / 3));
				}
			}
		}
		if (mass > 0) {
			add(out, "minecraft:iron_ingot", mass / 3 + floor / 2);
			add(out, "minecraft:copper_ingot", mass / 4);
			add(out, "minecraft:redstone", mass / 5);
			if (floor >= 2) add(out, "noclip_backrooms:battery_cell", mass / 10 + 1);
		}
		if (almond > 0) {
			add(out, "noclip_backrooms:aged_almond_reserve", almond / (floor >= 2 ? 2 : 3));
			add(out, "noclip_backrooms:almond_water", almond % (floor >= 2 ? 2 : 3));
		}
		if (packs > 0) add(out, "noclip_backrooms:blueprint_scrap", packs * (1 + floor / 3));
		out.values().removeIf(n -> n <= 0);
		return out;
	}

	private static void add(Map<Item, Integer> out, Item item, int n) {
		if (n > 0) out.merge(item, n, Integer::sum);
	}

	private static void add(Map<Item, Integer> out, String id, int n) {
		Identifier key = Identifier.parse(id);
		if (n > 0 && BuiltInRegistries.ITEM.containsKey(key)) add(out, BuiltInRegistries.ITEM.getValue(key), n);
	}
}
