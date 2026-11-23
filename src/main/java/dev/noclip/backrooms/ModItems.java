package dev.noclip.backrooms;

import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;

final class ModItems {
	static final Item FLASHLIGHT = register("flashlight",
			new Properties().stacksTo(1).durability(FlashlightItem.MAX_BATTERY),
			FlashlightItem::new);
	static final Item ALMOND_WATER = register("almond_water",
			new Properties(),
			AlmondWaterItem::new);

	private ModItems() {
	}

	static void register() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
				.register(output -> output.accept(FLASHLIGHT));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS)
				.register(output -> output.accept(ALMOND_WATER));
	}

	private static Item register(String name, Properties properties, Function<Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BackroomsMod.id(name));
		Item item = factory.apply(properties.setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}
}
