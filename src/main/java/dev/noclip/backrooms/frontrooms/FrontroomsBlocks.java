package dev.noclip.backrooms.frontrooms;

import dev.noclip.backrooms.BackroomsMod;
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
import net.minecraft.world.level.material.PushReaction;

/**
 * The Frontrooms' blocks: the institute's (which nothing breaks: it is M.E.G.'s, see {@link Institute}) and the
 * quarantine wall's around the grounds. Textures and models come from tools/make_frontrooms.py.
 */
public final class FrontroomsBlocks {
	static final List<Block> ALL = new ArrayList<>();

	public static final Block INSTITUTE_WALL = register("institute_wall", building(MapColor.SNOW));
	public static final Block INSTITUTE_WAINSCOT = register("institute_wainscot", building(MapColor.COLOR_GRAY));
	public static final Block INSTITUTE_FLOOR = register("institute_floor", building(MapColor.STONE));
	public static final Block INSTITUTE_WORK_FLOOR = register("institute_work_floor", building(MapColor.COLOR_YELLOW));
	public static final Block INSTITUTE_CEILING = register("institute_ceiling", building(MapColor.SNOW));
	public static final Block INSTITUTE_LIGHT = register("institute_light", building(MapColor.SNOW).sound(SoundType.GLASS).lightLevel(s -> 15));
	public static final Block EMERGENCY_LIGHT = register("institute_emergency_light", building(MapColor.COLOR_RED).sound(SoundType.GLASS).lightLevel(s -> 7));
	public static final Block INSTITUTE_CONCRETE = register("institute_concrete", building(MapColor.STONE));
	public static final Block INSTITUTE_COUNTER = register("institute_counter", building(MapColor.METAL).sound(SoundType.METAL));
	public static final Block BLAST_DOOR = register("blast_door", building(MapColor.METAL).sound(SoundType.METAL).noOcclusion(), BlastDoorBlock::new);
	public static final Block DOOR_LAMP = register("door_lamp", building(MapColor.SNOW).sound(SoundType.GLASS).lightLevel(s -> 7), DoorLampBlock::new);
	public static final Block MEG_LOCKER = register("meg_locker", building(MapColor.COLOR_BLUE).sound(SoundType.METAL), InstituteBlocks.Locker::new);
	public static final Block WEAPON_LOCKER = register("weapon_locker", building(MapColor.COLOR_GRAY).sound(SoundType.METAL), InstituteBlocks.Facing::new);
	public static final Block TRANSPORT_PAD = register("transport_pad", building(MapColor.COLOR_CYAN).sound(SoundType.METAL).noOcclusion()
			.lightLevel(s -> 9), InstituteBlocks.Pad::new);
	public static final Block ARCHIVE_HATCH = register("archive_hatch", building(MapColor.METAL).sound(SoundType.METAL).noOcclusion(), InstituteBlocks.Hatch::new);
	public static final Block ARCHIVE_FILE = register("archive_file", building(MapColor.WOOD).sound(SoundType.WOOD), InstituteBlocks.File::new);

	// Around the grounds
	public static final Block QUARANTINE_WALL = register("quarantine_wall", building(MapColor.STONE));
	public static final Block QUARANTINE_BAND = register("quarantine_band", building(MapColor.COLOR_YELLOW));
	public static final Block QUARANTINE_FLOODLIGHT = register("quarantine_floodlight", building(MapColor.METAL).sound(SoundType.GLASS).lightLevel(s -> 15));
	public static final Block ELECTRIC_FENCE = register("electric_fence", building(MapColor.METAL).sound(SoundType.CHAIN).noCollision().noOcclusion(),
			InstituteBlocks.Fence::new);
	public static final Block WARNING_SIGN = register("warning_sign", building(MapColor.COLOR_YELLOW).sound(SoundType.METAL).noCollision().noOcclusion(),
			InstituteBlocks.Sign::new);

	private FrontroomsBlocks() {
	}

	static void register() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS).register(output -> ALL.forEach(output::accept));
	}

	/** Cannot be broken by hand or blown up. */
	private static Properties building(MapColor color) {
		return Properties.of().mapColor(color).strength(-1.0F, 3600000.0F).noLootTable().sound(SoundType.STONE).pushReaction(PushReaction.BLOCK);
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
