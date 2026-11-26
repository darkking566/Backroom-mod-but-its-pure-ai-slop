package dev.noclip.backrooms.core.economy;

import com.mojang.brigadier.arguments.LongArgumentType;
import dev.noclip.backrooms.BackroomsMod;
import java.util.function.Function;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

/**
 * Money: the permanent account (Wallet) and what goes into it. Two kinds of coin, after the design spec's Level 0 list:
 * a lost old coin (10 credits) and a tape of static (40). Also /backrooms money get|add|set for testing.
 */
public final class Economy {
	public static final Item LOST_COIN = register("lost_coin", p -> new CoinItem(p.stacksTo(64), 10));
	public static final Item STATIC_TAPE = register("static_tape", p -> new CoinItem(p.stacksTo(16), 40));
	/** What M.E.G.'s missing squad left behind: found in crates, handed in at the outpost (core.quota). */
	public static final Item MEG_SQUAD_REPORT = register("meg_squad_report", p -> new Item(p.stacksTo(16)));

	private Economy() {
	}

	public static void register() {
		// Touch the account so its attachment is registered before any player loads.
		java.util.Objects.requireNonNull(Wallet.CREDITS);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			output.accept(LOST_COIN);
			output.accept(STATIC_TAPE);
			output.accept(MEG_SQUAD_REPORT);
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				Commands.literal("backrooms")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("money")
								.then(Commands.literal("get").executes(ctx -> {
									ServerPlayer player = ctx.getSource().getPlayerOrException();
									ctx.getSource().sendSuccess(() -> Component.translatable("message.noclip_backrooms.money", Wallet.of(player)), false);
									return 1;
								}))
								.then(Commands.literal("add").then(Commands.argument("amount", LongArgumentType.longArg()).executes(ctx -> {
									ServerPlayer player = ctx.getSource().getPlayerOrException();
									Wallet.add(player, LongArgumentType.getLong(ctx, "amount"));
									ctx.getSource().sendSuccess(() -> Component.translatable("message.noclip_backrooms.money", Wallet.of(player)), false);
									return 1;
								})))
								.then(Commands.literal("set").then(Commands.argument("amount", LongArgumentType.longArg(0)).executes(ctx -> {
									ServerPlayer player = ctx.getSource().getPlayerOrException();
									Wallet.set(player, LongArgumentType.getLong(ctx, "amount"));
									ctx.getSource().sendSuccess(() -> Component.translatable("message.noclip_backrooms.money", Wallet.of(player)), false);
									return 1;
								}))))));
	}

	private static Item register(String name, Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BackroomsMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key)));
	}
}
