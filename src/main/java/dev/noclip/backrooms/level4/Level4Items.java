package dev.noclip.backrooms.level4;

import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;

/**
 * Dedicated 3D items introduced with Level 4:
 * - Religious relics ({@link #BRASS_CRUCIFIX}, {@link #FADED_ICON_FRAME}) that pacify Amor Incrementum.
 * - Useless quirky curios sold by T.B.D. ({@link #BROKEN_NW_COMPASS}, {@link #CALENDAR_1998},
 *   {@link #BLANK_STAMP}, {@link #UNLABELED_TAPE}, {@link #RAIN_GLASS_ORB}).
 */
public final class Level4Items {
	public static final Item BRASS_CRUCIFIX = register("brass_crucifix",
			new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON), Item::new);
	public static final Item FADED_ICON_FRAME = register("faded_icon_frame",
			new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON), Item::new);

	public static final Item BROKEN_NW_COMPASS = register("broken_nw_compass",
			new Item.Properties().stacksTo(1), Item::new);
	public static final Item CALENDAR_1998 = register("calendar_1998",
			new Item.Properties().stacksTo(1), Item::new);
	public static final Item BLANK_STAMP = register("blank_stamp",
			new Item.Properties().stacksTo(1), Item::new);
	public static final Item UNLABELED_TAPE = register("unlabeled_tape",
			new Item.Properties().stacksTo(1), Item::new);
	public static final Item RAIN_GLASS_ORB = register("rain_glass_orb",
			new Item.Properties().stacksTo(1), RainOrbItem::new);

	static final List<Item> CURIOS = List.of(
			BROKEN_NW_COMPASS, CALENDAR_1998, BLANK_STAMP, UNLABELED_TAPE, RAIN_GLASS_ORB
	);

	private Level4Items() {
	}

	static void register() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.accept(BRASS_CRUCIFIX);
			output.accept(FADED_ICON_FRAME);
			for (Item curio : CURIOS) output.accept(curio);
		});
	}

	static boolean isReligiousRelic(Player player) {
		return isReligious(player.getMainHandItem()) || isReligious(player.getOffhandItem());
	}

	static boolean isReligious(ItemStack stack) {
		return stack.is(BRASS_CRUCIFIX) || stack.is(FADED_ICON_FRAME);
	}

	private static Item register(String name, Item.Properties properties, Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Level4.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	private static final class RainOrbItem extends Item {
		RainOrbItem(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (player instanceof ServerPlayer sp) {
				level.playSound(null, player.blockPosition(), Level4Sounds.RAIN, SoundSource.AMBIENT, 0.9F, 1.0F);
				sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.rain_orb"));
			}
			return InteractionResult.SUCCESS;
		}
	}
}
