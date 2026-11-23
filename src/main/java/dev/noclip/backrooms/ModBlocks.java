package dev.noclip.backrooms;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.material.MapColor;

final class ModBlocks {
	static final List<Block> ALL = new ArrayList<>();

	static final Block WALLPAPER = register("wallpaper", wall(MapColor.COLOR_YELLOW));
	static final Block WALLPAPER_BASEBOARD = register("wallpaper_baseboard", wall(MapColor.COLOR_YELLOW));
	static final Block PALE_WALLPAPER = register("pale_wallpaper", wall(MapColor.QUARTZ));
	static final Block PALE_WALLPAPER_BASEBOARD = register("pale_wallpaper_baseboard", wall(MapColor.QUARTZ));
	static final Block RED_WALLPAPER = register("red_wallpaper", wall(MapColor.COLOR_RED));
	static final Block RED_WALLPAPER_BASEBOARD = register("red_wallpaper_baseboard", wall(MapColor.COLOR_RED));
	static final Block PEELING_RED_WALLPAPER = register("peeling_red_wallpaper", wall(MapColor.TERRACOTTA_RED));
	static final Block ROUGH_WALLPAPER = register("rough_wallpaper", Properties.of()
			.mapColor(MapColor.SAND).strength(2.2F).sound(SoundType.TUFF));
	static final Block ROUGH_WALLPAPER_BASEBOARD = register("rough_wallpaper_baseboard", Properties.of()
			.mapColor(MapColor.SAND).strength(2.2F).sound(SoundType.TUFF));
	static final Block CARPET = register("carpet", Properties.of()
			.mapColor(MapColor.COLOR_BROWN).strength(0.8F).sound(SoundType.WOOL));
	static final Block DRY_CARPET = register("dry_carpet", Properties.of()
			.mapColor(MapColor.SAND).strength(0.7F).sound(SoundType.WOOL).speedFactor(1.04F));
	static final Block THICK_CARPET = register("thick_carpet", Properties.of()
			.mapColor(MapColor.TERRACOTTA_BROWN).strength(1.0F).sound(SoundType.WOOL).speedFactor(0.72F));
	static final Block CEILING_TILE = register("ceiling_tile", Properties.of()
			.mapColor(MapColor.SAND).strength(1.0F).sound(SoundType.STONE));
	static final Block FLUORESCENT_LIGHT = register("fluorescent_light", lamp().lightLevel(state -> 15));
	static final Block BROKEN_FLUORESCENT_LIGHT = register("broken_fluorescent_light", lamp());
	static final Block RED_FLUORESCENT_LIGHT = register("red_fluorescent_light", lamp().lightLevel(state -> 7));
	static final Block EXIT_LIGHT = register("exit_light", lamp().lightLevel(state -> 12));
	static final Block EXIT_DOOR = register("exit_door", exit().lightLevel(state -> 4), ExitDoorBlock::new);
	static final Block EXIT_DOOR_TOP = register("exit_door_top", exit().lightLevel(state -> 4), ExitDoorBlock::new);
	static final Block EXIT_SIGN = register("exit_sign", exit().lightLevel(state -> 10));

	private ModBlocks() {
	}

	// Calling this from onInitialize runs the registrations above before the registries freeze.
	static void register() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS)
				.register(output -> ALL.forEach(output::accept));
	}

	private static Properties wall(MapColor color) {
		return Properties.of().mapColor(color).strength(2.0F).sound(SoundType.WOOD);
	}

	private static Properties lamp() {
		return Properties.of().mapColor(MapColor.SAND).strength(0.3F).sound(SoundType.GLASS);
	}

	// Like bedrock, so the way out can be neither destroyed nor carried away.
	private static Properties exit() {
		return Properties.of().mapColor(MapColor.COLOR_GREEN).strength(-1.0F, 3600000.0F).noLootTable().sound(SoundType.METAL);
	}

	private static Block register(String name, Properties properties) {
		return register(name, properties, Block::new);
	}

	private static Block register(String name, Properties properties, Function<Properties, Block> factory) {
		Identifier id = BackroomsMod.id(name);
		Block block = Blocks.register(ResourceKey.create(Registries.BLOCK, id), factory, properties);
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
		BlockItem item = new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix());
		item.registerBlocks(Item.BY_BLOCK, item);
		Registry.register(BuiltInRegistries.ITEM, itemKey, item);
		ALL.add(block);
		return block;
	}
}
