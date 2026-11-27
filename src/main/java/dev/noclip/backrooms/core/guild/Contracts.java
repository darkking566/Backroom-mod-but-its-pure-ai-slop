package dev.noclip.backrooms.core.guild;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.Home;
import dev.noclip.backrooms.core.LevelExits;
import dev.noclip.backrooms.core.economy.Wallet;
import dev.noclip.backrooms.core.field.FieldKit;
import dev.noclip.backrooms.core.floors.FloorRun;
import dev.noclip.backrooms.core.floors.Floors;
import dev.noclip.backrooms.core.locker.RelayService;
import dev.noclip.backrooms.core.locker.Safebox;
import dev.noclip.backrooms.core.people.Person;
import dev.noclip.backrooms.core.rpg.PlayerRpg;
import dev.noclip.backrooms.core.trade.ShopRole;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * M.E.G. contracts (docs/project/1-gameplay/README.md): reach orders, entity hunts, field photography,
 * topology mapping, supply recovery, and multi-level uninterrupted courier runs, filtered by deadlock prevention
 * so only currently reachable levels & registered targets are ever offered.
 */
public final class Contracts {
	public enum Kind {
		REACH, HUNT, PHOTO, MAP, COLLECT, COURIER
	}

	public enum Contract {
		SURVEY("survey_level1", "1", 150, Kind.REACH, "1", 1, List.of("0", "1")),
		RECOVER("recover_level2", "2", 400, Kind.REACH, "2", 1, List.of("0", "1", "2")),
		READINGS("readings_level3", "3", 800, Kind.REACH, "3", 1, List.of("0", "1", "2", "3")),
		HUNT_HOUNDS("hunt_hounds", "1", 260, Kind.HUNT, "hound", 2, List.of("0", "1")),
		PHOTO_SMILER("photo_smiler", "1", 300, Kind.PHOTO, "smiler", 1, List.of("0", "1")),
		MAP_LEVEL0("map_level0", "0", 280, Kind.MAP, "0", 1, List.of("0")),
		COLLECT_ALMOND("collect_almond", "1", 220, Kind.COLLECT, "noclip_backrooms:almond_water", 4, List.of("0", "1")),
		COURIER_012("courier_0_1_2", "2", 520, Kind.COURIER, "0->1->2", 3, List.of("0", "1", "2"));

		public final String id, level;
		public final long reward;
		public final Kind kind;
		public final String target;
		public final int goal;
		public final List<String> requiredLevels;

		Contract(String id, String level, long reward, Kind kind, String target, int goal, List<String> requiredLevels) {
			this.id = id;
			this.level = level;
			this.reward = reward;
			this.kind = kind;
			this.target = target;
			this.goal = goal;
			this.requiredLevels = requiredLevels;
		}

		public static Contract byId(String id) {
			for (Contract c : values()) if (c.id.equals(id)) return c;
			return null;
		}

		Component title() {
			return Component.translatable("contract.noclip_backrooms." + id);
		}

		/** Pure reachability check for unit tests (anti-deadlock rule: never assign unreachable levels). */
		public boolean completableWith(Collection<String> openLevels) {
			for (String lvl : requiredLevels) {
				if (!openLevels.contains(lvl)) return false;
			}
			return true;
		}
	}

	public record Active(String id, int progress, boolean reached) {
		static final Codec<Active> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("id").forGetter(Active::id),
				Codec.INT.optionalFieldOf("progress", 0).forGetter(Active::progress),
				Codec.BOOL.optionalFieldOf("reached", false).forGetter(Active::reached)).apply(i, Active::new));
	}

	public static final AttachmentType<Active> ACTIVE = AttachmentRegistry.create(BackroomsMod.id("contract"),
			builder -> builder.persistent(Active.CODEC).copyOnDeath());
	/** How near the commander the player must be to take a contract. */
	static final double NEAR_COMMANDER = 8.0;

	private Contracts() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
				Commands.literal("meg").then(Commands.literal("contract")
						.then(Commands.literal("accept").then(Commands.argument("id", StringArgumentType.word())
								.executes(ctx -> accept(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "id")) ? 1 : 0)))
						.then(Commands.literal("status").executes(ctx -> {
							showStatus(ctx.getSource().getPlayerOrException());
							return 1;
						}))
						.then(Commands.literal("abandon").executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							if (!player.hasAttached(ACTIVE)) return 0;
							player.removeAttached(ACTIVE);
							player.sendSystemMessage(Component.translatable("message.noclip_backrooms.contract.abandoned"));
							return 1;
						})))));

		Floors.onArrive((player, level, run, newLevel) -> {
			Active active = player.getAttached(ACTIVE);
			Contract contract = active == null ? null : Contract.byId(active.id());
			if (contract == null || active.reached()) return;
			if (contract.kind == Kind.REACH && contract.level.equals(level)) {
				player.setAttached(ACTIVE, new Active(active.id(), 1, true));
				player.sendSystemMessage(Component.translatable("message.noclip_backrooms.contract.reached", contract.reward)
						.withStyle(ChatFormatting.GREEN));
			} else if (contract.kind == Kind.COURIER) {
				int prog = active.progress();
				List<String> route = contract.requiredLevels;
				if (prog >= 0 && prog < route.size() && route.get(prog).equals(level)) {
					int nextProg = prog + 1;
					boolean done = nextProg >= route.size();
					player.setAttached(ACTIVE, new Active(active.id(), nextProg, done));
					if (done) {
						player.sendSystemMessage(Component.translatable("message.noclip_backrooms.contract.reached", contract.reward)
								.withStyle(ChatFormatting.GREEN));
					} else {
						player.sendSystemMessage(Component.translatable("message.noclip_backrooms.contract.progress",
								contract.title(), nextProg, route.size()).withStyle(ChatFormatting.AQUA));
					}
				}
			}
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity instanceof Monster) || !(source.getEntity() instanceof ServerPlayer player)) return;
			if (!Home.inBackrooms(player.level().dimension())) return;
			Active active = player.getAttached(ACTIVE);
			Contract contract = active == null ? null : Contract.byId(active.id());
			if (contract == null || contract.kind != Kind.HUNT || active.reached()) return;
			String path = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
			if (!contract.target.equals(path)) return;
			int nextProg = active.progress() + 1;
			boolean done = nextProg >= contract.goal;
			player.setAttached(ACTIVE, new Active(active.id(), nextProg, done));
			if (done) {
				player.sendSystemMessage(Component.translatable("message.noclip_backrooms.contract.reached", contract.reward)
						.withStyle(ChatFormatting.GREEN));
			} else {
				player.sendSystemMessage(Component.translatable("message.noclip_backrooms.contract.progress",
						contract.title(), nextProg, contract.goal).withStyle(ChatFormatting.AQUA));
			}
		});

		Floors.onHome(Contracts::cameHome);
	}

	/** Called when {@code player} shoots a field photo (core.field.Evidence). */
	public static void onPhoto(ServerPlayer player, String subject, String levelKey) {
		Active active = player.getAttached(ACTIVE);
		Contract contract = active == null ? null : Contract.byId(active.id());
		if (contract == null || contract.kind != Kind.PHOTO || active.reached()) return;
		if (!contract.target.equals(subject)) return;
		player.setAttached(ACTIVE, new Active(active.id(), 1, true));
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.contract.reached", contract.reward)
				.withStyle(ChatFormatting.GREEN));
	}

	/** Called when a mapping beacon completes a level scan for {@code player}. */
	public static void onTape(ServerPlayer player, String levelKey) {
		Active active = player.getAttached(ACTIVE);
		Contract contract = active == null ? null : Contract.byId(active.id());
		if (contract == null || contract.kind != Kind.MAP || active.reached()) return;
		if (!contract.target.equals(levelKey)) return;
		player.setAttached(ACTIVE, new Active(active.id(), 1, true));
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.contract.reached", contract.reward)
				.withStyle(ChatFormatting.GREEN));
	}

	/** Pure reachability check for unit tests (anti-deadlock rule: never assign unreachable levels). */
	public static boolean isCompletableWith(Contract contract, Collection<String> openLevels) {
		for (String lvl : contract.requiredLevels) {
			if (!openLevels.contains(lvl)) return false;
		}
		return true;
	}

	/** Runtime anti-deadlock check against currently registered open levels & registries. */
	public static boolean isCompletable(Contract contract) {
		if (!isCompletableWith(contract, LevelExits.openLevels())) return false;
		if (contract.kind == Kind.COLLECT) {
			return BuiltInRegistries.ITEM.containsKey(Identifier.parse(contract.target));
		}
		if (contract.kind == Kind.HUNT) {
			return BuiltInRegistries.ENTITY_TYPE.containsKey(BackroomsMod.id(contract.target));
		}
		return true;
	}

	/** All contracts that can currently be completed without deadlocks. */
	public static List<Contract> available() {
		List<Contract> list = new ArrayList<>();
		for (Contract c : Contract.values()) {
			if (isCompletable(c)) list.add(c);
		}
		return list;
	}

	/** What the commander says when spoken to: quota/clearance, safebox & insurance, and open contracts. */
	public static void talk(ServerPlayer player) {
		for (int i = 1; i <= 3; i++) player.sendSystemMessage(Component.translatable("guild.noclip_backrooms.master." + i));
		dev.noclip.backrooms.core.quota.Quota.talk(player);
		if (dev.noclip.backrooms.core.quota.Quota.locked(player.level().getServer())) return;
		Safebox.talk(player);
		RelayService.talk(player);
		Active active = player.getAttached(ACTIVE);
		Contract current = active == null ? null : Contract.byId(active.id());
		if (current != null) {
			player.sendSystemMessage(Component.translatable("contract.noclip_backrooms.active", current.title(),
					statusText(player, current, active))
					.append(" ").append(button("contract.noclip_backrooms.abandon", "/meg contract abandon", ChatFormatting.RED)));
			return;
		}
		player.sendSystemMessage(Component.translatable("contract.noclip_backrooms.header").withStyle(ChatFormatting.GOLD));
		for (Contract contract : available()) {
			player.sendSystemMessage(Component.translatable("contract.noclip_backrooms.line", contract.title(), contract.reward)
					.append(" ").append(button("contract.noclip_backrooms.accept", "/meg contract accept " + contract.id, ChatFormatting.GREEN)));
		}
	}

	private static void showStatus(ServerPlayer player) {
		Active active = player.getAttached(ACTIVE);
		Contract current = active == null ? null : Contract.byId(active.id());
		if (current == null) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.contract.none").withStyle(ChatFormatting.GRAY));
			return;
		}
		player.sendSystemMessage(Component.translatable("contract.noclip_backrooms.active", current.title(),
				statusText(player, current, active)));
	}

	private static Component statusText(ServerPlayer player, Contract contract, Active active) {
		if (contract.kind == Kind.COLLECT) {
			int carried = countCollectItem(player.getInventory(), contract.target);
			return carried >= contract.goal
					? Component.translatable("contract.noclip_backrooms.status.reached")
					: Component.translatable("contract.noclip_backrooms.status.progress", carried, contract.goal);
		}
		if (active.reached()) return Component.translatable("contract.noclip_backrooms.status.reached");
		if (contract.goal > 1) {
			return Component.translatable("contract.noclip_backrooms.status.progress", active.progress(), contract.goal);
		}
		return Component.translatable("contract.noclip_backrooms.status.going");
	}

	private static MutableComponent button(String key, String command, ChatFormatting colour) {
		return Component.translatable(key).withStyle(style -> style.withColor(colour)
				.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(command))));
	}

	static boolean accept(ServerPlayer player, String id) {
		Contract contract = Contract.byId(id);
		String refusal = contract == null ? "message.noclip_backrooms.contract.unknown"
				: !isCompletable(contract) ? "message.noclip_backrooms.contract.unavailable"
				: player.hasAttached(ACTIVE) ? "message.noclip_backrooms.contract.already"
				: !Home.isHome(player.level().dimension()) ? "message.noclip_backrooms.contract.not_home"
				: !nearCommander(player) ? "message.noclip_backrooms.contract.no_commander"
				: dev.noclip.backrooms.core.quota.Quota.locked(player.level().getServer()) ? "message.noclip_backrooms.contract.locked" : null;
		if (refusal != null) {
			player.sendSystemMessage(Component.translatable(refusal));
			return false;
		}
		start(player, contract);
		return true;
	}

	/** Takes the contract and sends the player in (accept() checks first; tests call this directly). */
	public static void start(ServerPlayer player, Contract contract) {
		player.setAttached(ACTIVE, new Active(contract.id, 0, false));
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.contract.accepted", contract.title())
				.withStyle(ChatFormatting.GRAY));
		LevelExits.fallInto(player, "0");
	}

	private static boolean nearCommander(ServerPlayer player) {
		return !player.level().getEntitiesOfClass(Person.class, player.getBoundingBox().inflate(NEAR_COMMANDER),
				person -> ShopRole.of(person) == ShopRole.MASTER).isEmpty();
	}

	private static void cameHome(ServerPlayer player, FloorRun run) {
		Active active = player.getAttached(ACTIVE);
		if (active == null) return;
		Contract contract = Contract.byId(active.id());
		player.removeAttached(ACTIVE);
		if (contract == null) return;

		boolean success = switch (contract.kind) {
			case COLLECT -> consumeCollectItem(player.getInventory(), contract.target, contract.goal);
			case MAP -> active.reached() || hasMappedTape(player.getInventory(), contract.target);
			default -> active.reached();
		};

		if (success) {
			// An M.E.G. order: its value goes into the quota (or the debt), and half of it again into the account as a bonus.
			long left = dev.noclip.backrooms.core.quota.Quota.addPoints(player, contract.reward);
			long bonus = contract.reward / 2 + left;
			Wallet.add(player, bonus);
			PlayerRpg.grantXp(player, (int) (contract.reward / 5));
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.contract.paid", contract.title(), contract.reward - left, bonus)
					.withStyle(ChatFormatting.GREEN));
		} else {
			String key = contract.kind == Kind.REACH
					? "message.noclip_backrooms.contract.failed"
					: "message.noclip_backrooms.contract.failed_generic";
			player.sendSystemMessage(Component.translatable(key, contract.title())
					.withStyle(ChatFormatting.RED));
		}
	}

	private static boolean hasMappedTape(Inventory inv, String levelKey) {
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (!stack.is(FieldKit.TAPE)) continue;
			CustomData data = stack.get(DataComponents.CUSTOM_DATA);
			if (data != null && levelKey.equals(data.copyTag().getStringOr("level", ""))) return true;
		}
		return false;
	}

	private static int countCollectItem(Inventory inv, String itemId) {
		Identifier id = Identifier.parse(itemId);
		if (!BuiltInRegistries.ITEM.containsKey(id)) return 0;
		Item target = BuiltInRegistries.ITEM.getValue(id);
		int count = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.is(target)) count += stack.getCount();
		}
		return count;
	}

	private static boolean consumeCollectItem(Inventory inv, String itemId, int goal) {
		if (countCollectItem(inv, itemId) < goal) return false;
		Item target = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
		int remaining = goal;
		for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
			ItemStack stack = inv.getItem(i);
			if (!stack.is(target)) continue;
			int take = Math.min(remaining, stack.getCount());
			stack.shrink(take);
			remaining -= take;
		}
		inv.setChanged();
		return true;
	}
}
