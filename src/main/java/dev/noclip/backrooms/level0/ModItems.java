package dev.noclip.backrooms.level0;

import dev.noclip.backrooms.BackroomsMod;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Rarity;

public final class ModItems {
	/** Standard limited-battery starter flashlight (custom 5-bar battery, no vanilla durability bar). */
	public static final Item FLASHLIGHT = register("flashlight",
			new Properties().stacksTo(1),
			props -> new FlashlightItem(props, false));

	/** Level 3 infinite ambient-energy flashlight ("draws ambient energy from the air and converts it into electricity"). */
	public static final Item AMBIENT_FLASHLIGHT = register("ambient_flashlight",
			new Properties().stacksTo(1).rarity(Rarity.EPIC),
			props -> new FlashlightItem(props, true));

	private ModItems() {
	}

	static void register() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
				.register(output -> {
					output.accept(FLASHLIGHT);
					output.accept(AMBIENT_FLASHLIGHT);
				});
	}

	private static Item register(String name, Properties properties, Function<Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BackroomsMod.id(name));
		Item item = factory.apply(properties.setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}
}
