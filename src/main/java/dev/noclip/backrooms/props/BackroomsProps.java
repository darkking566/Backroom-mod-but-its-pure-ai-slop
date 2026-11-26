package dev.noclip.backrooms.props;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.crafting.StationType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Registers all 6 Backrooms Crafting Workstations (including 7x7 Primary & 9x9 Ultimate Spiderweb),
 * 6 Overworld/Hub facilities, 3 Loot Chests, 22 true Multi-Block Props (breaking ANY block breaks
 * the full structure and drops base salvage parts), and 22 Single-Block Props.
 */
public final class BackroomsProps {
	private static final Map<String, Block> ALL_BLOCKS = new LinkedHashMap<>();
	private static final Map<String, Item> ALL_ITEMS = new LinkedHashMap<>();
	private static final List<Block> BLENDER_PROP_BLOCKS = new ArrayList<>();

	// Invisible linked collision filler block for all MultiPartPropBlock structures (no BlockItem)
	public static final Block PROP_PART = registerBlockOnly("prop_part", PropPartBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.STONE).sound(SoundType.METAL).strength(2.0F, 4.0F).noOcclusion());

	// Glowing Overworld Backrooms Seepage Rift (walk in or right-click to intentionally re-enter Level 0)
	public static final Block BACKROOMS_RIFT = register("backrooms_rift", BackroomsRiftBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).sound(SoundType.AMETHYST)
					.strength(-1.0F, 3600000.0F).noCollision().noOcclusion().noLootTable().lightLevel(s -> 12));

	// 6 Custom Workstations (1->4 Salvage, 5x5 Workshop, 3x3 Distiller, 3x3 Altar, 7x7 Primary & 9x9 Ultimate Spiderweb)
	public static final Block SALVAGE_BENCH = station("salvage_bench", StationType.SALVAGE, SoundType.WOOD, 2.5F, 0);
	public static final Block WORKSHOP_BENCH = station("workshop_bench", StationType.WORKSHOP, SoundType.ANVIL, 4.0F, 0);
	public static final Block ALMOND_DISTILLER = station("almond_distiller", StationType.DISTILLER, SoundType.COPPER, 3.0F, 4);
	public static final Block ANOMALY_ALTAR = station("anomaly_altar", StationType.ALTAR, SoundType.AMETHYST, 5.0F, 9);
	public static final Block SPIDERWEB_CRAFTING_TABLE = station("spiderweb_crafting_table", StationType.SPIDERWEB, SoundType.NETHERITE_BLOCK, 5.0F, 11);
	public static final Block ULTIMATE_SPIDERWEB_CRAFTING_TABLE = station("ultimate_spiderweb_crafting_table", StationType.ULTIMATE_SPIDERWEB, SoundType.NETHERITE_BLOCK, 6.5F, 14);

	// 6 Overworld / Hub Processing & Progression Facilities (GDD Sections 2.1, 2.3, 5.2)
	public static final Block SCRAP_RECYCLER = station("scrap_recycler", StationType.SALVAGE, SoundType.METAL, 3.8F, 2);
	public static final Block ANOMALY_DISTILLERY_BARREL = station("anomaly_distillery_barrel", StationType.DISTILLER, SoundType.WOOD, 3.0F, 3);
	public static final Block ANOMALY_RESEARCH_TERMINAL = station("anomaly_research_terminal", StationType.WORKSHOP, SoundType.METAL, 4.2F, 7);
	public static final Block CRYSTAL_REPLICATOR = station("crystal_replicator", StationType.ALTAR, SoundType.AMETHYST, 5.5F, 10);
	public static final Block ANOMALY_ARCHIVE_BOARD = multiProp("anomaly_archive_board", 2, 2, 1, SoundType.WOOD, 2.4F, 2);
	public static final Block RELAY_DROPOFF_BOX = prop("relay_dropoff_box", SoundType.METAL, 4.5F, 4);

	// 3 Tiered Loot Chests
	public static final Block LOCKED_ARMORY_CRATE = chest("locked_armory_crate", true, SoundType.METAL, 4.5F, 0);
	public static final Block ANOMALY_VAULT_CHEST = chest("anomaly_vault_chest", false, SoundType.AMETHYST, 5.0F, 6);
	public static final Block BOSS_TROPHY_CHEST = chest("boss_trophy_chest", false, SoundType.GILDED_BLACKSTONE, 4.0F, 10);

	// 24 No-Clipped Props (P01..P24: large appliances/furniture are real multi-block structures!)
	public static final Block NOCLIPPED_WOOD_CHAIR = blenderProp("noclipped_wood_chair", SoundType.WOOD, 1.4F, 0);
	public static final Block NOCLIPPED_OFFICE_CHAIR = blenderProp("noclipped_office_chair", SoundType.WOOL, 1.4F, 0);
	public static final Block NOCLIPPED_FOLDING_TABLE = multiProp("noclipped_folding_table", 2, 1, 1, SoundType.WOOD, 1.6F, 0);
	public static final Block NOCLIPPED_FILE_CABINET = multiProp("noclipped_file_cabinet", 1, 2, 1, SoundType.METAL, 2.6F, 0);
	public static final Block NOCLIPPED_CRT_TV = blenderProp("noclipped_crt_tv", SoundType.GLASS, 2.0F, 4);
	public static final Block NOCLIPPED_WATER_COOLER = multiProp("noclipped_water_cooler", 1, 2, 1, SoundType.GLASS, 1.5F, 0);
	public static final Block NOCLIPPED_VENDING_MACHINE = multiProp("noclipped_vending_machine", 2, 2, 1, SoundType.METAL, 3.2F, 6);
	public static final Block NOCLIPPED_FRIDGE = multiProp("noclipped_fridge", 2, 3, 1, SoundType.METAL, 3.0F, 0);
	public static final Block BOARDED_BARRICADE = multiProp("boarded_barricade", 2, 2, 1, SoundType.WOOD, 2.5F, 0);
	public static final Block BARBED_WIRE_FENCE = multiProp("barbed_wire_fence", 2, 1, 1, SoundType.CHAIN, 2.5F, 0);
	public static final Block INDUSTRIAL_FUSE_BOX = blenderProp("industrial_fuse_box", SoundType.METAL, 3.8F, 3);
	public static final Block REINFORCED_STEEL_BULKHEAD = multiProp("reinforced_steel_bulkhead", 2, 2, 1, SoundType.ANVIL, 6.0F, 0);
	public static final Block STEAM_BOILER_UNIT = multiProp("steam_boiler_unit", 2, 2, 2, SoundType.METAL, 5.0F, 4);
	public static final Block HOSPITAL_GURNEY = multiProp("hospital_gurney", 1, 1, 2, SoundType.METAL, 2.2F, 0);
	public static final Block VINTAGE_CHANDELIER = blenderProp("vintage_chandelier", SoundType.GLASS, 2.0F, 10);
	public static final Block ARCADE_CABINET = multiProp("arcade_cabinet", 1, 2, 1, SoundType.METAL, 2.8F, 8);
	public static final Block NEON_SIGN_PROP = multiProp("neon_sign_prop", 2, 1, 1, SoundType.GLASS, 1.8F, 12);
	public static final Block MOLD_VENT_GRATE = prop("mold_vent_grate", SoundType.METAL, 2.2F, 0);
	public static final Block POOL_CERAMIC_GRATE = prop("pool_ceramic_grate", SoundType.STONE, 2.4F, 0);
	public static final Block RED_ANOMALY_CLUSTER = blenderProp("red_anomaly_cluster", SoundType.AMETHYST, 7.5F, 9);
	public static final Block CRYSTAL_VEIN_NODE = blenderProp("crystal_vein_node", SoundType.AMETHYST, 7.0F, 8);
	public static final Block GLITCHED_WALL_SEAM = prop("glitched_wall_seam", SoundType.STONE, 8.0F, 6);
	public static final Block SEALED_VAULT_WALL = prop("sealed_vault_wall", SoundType.NETHERITE_BLOCK, 12.0F, 3);
	public static final Block WANDERER_REMAINS = blenderProp("wanderer_remains", SoundType.BONE_BLOCK, 1.2F, 0);

	// 20 Level 0 & MATERIALS.md Breakable 3D Furniture / Intact-Damaged-NoClipped Variants
	public static final Block PROP_DESKTOP_COMPUTER = blenderProp("prop_desktop_computer", SoundType.METAL, 1.8F, 4);
	public static final Block PROP_DESKTOP_COMPUTER_DAMAGED = blenderProp("prop_desktop_computer_damaged", SoundType.METAL, 1.5F, 1);
	public static final Block PROP_DESKTOP_COMPUTER_NOCLIPPED = blenderProp("prop_desktop_computer_noclipped", SoundType.METAL, 2.2F, 5);
	public static final Block PROP_PHONE = blenderProp("prop_phone", SoundType.GLASS, 0.9F, 3);
	public static final Block PROP_PHONE_CRACKED = blenderProp("prop_phone_cracked", SoundType.GLASS, 0.8F, 1);
	public static final Block PROP_PHONE_NOCLIPPED = blenderProp("prop_phone_noclipped", SoundType.GLASS, 1.4F, 4);
	public static final Block PROP_OFFICE_CHAIR = blenderProp("prop_office_chair", SoundType.WOOL, 1.3F, 0);
	public static final Block PROP_SOFA = multiProp("prop_sofa", 2, 1, 1, SoundType.WOOL, 1.6F, 0);
	public static final Block PROP_SOFA_NOCLIPPED = multiProp("prop_sofa_noclipped", 2, 1, 1, SoundType.WOOL, 2.0F, 0);
	public static final Block PROP_GLASS_PARTITION = multiProp("prop_glass_partition", 2, 2, 1, SoundType.GLASS, 1.4F, 0);
	public static final Block PROP_GLASS_PARTITION_BROKEN = multiProp("prop_glass_partition_broken", 2, 2, 1, SoundType.GLASS, 1.0F, 0);
	public static final Block PROP_FOLDING_CHAIR_PILE = multiProp("prop_folding_chair_pile", 2, 1, 2, SoundType.METAL, 1.7F, 0);
	public static final Block PROP_CURTAIN_HEAP = multiProp("prop_curtain_heap", 2, 1, 1, SoundType.WOOL, 1.0F, 0);
	public static final Block PROP_STANDING_MIRROR = multiProp("prop_standing_mirror", 1, 2, 1, SoundType.GLASS, 1.4F, 0);
	public static final Block PROP_POTTED_PLANT_PLASTIC = blenderProp("prop_potted_plant_plastic", SoundType.GRASS, 0.9F, 0);
	public static final Block PROP_ELECTRIC_FAN = blenderProp("prop_electric_fan", SoundType.METAL, 1.6F, 0);
	public static final Block PROP_MICROWAVE = blenderProp("prop_microwave", SoundType.METAL, 2.0F, 2);
	public static final Block PROP_FLOOR_LAMP = multiProp("prop_floor_lamp", 1, 2, 1, SoundType.GLASS, 1.4F, 9);
	public static final Block PROP_WALL_FIRST_AID_KIT = blenderProp("prop_wall_first_aid_kit", SoundType.METAL, 1.5F, 0);
	public static final Block PROP_CARDBOARD_PILE = multiProp("prop_cardboard_pile", 2, 2, 2, SoundType.WOOD, 1.0F, 0);

	// 8 Intact Counterparts & Movie-Style Wood Chair Pile (so Level 0 clusters mix intact + half-sliced noclipped props!)
	public static final Block PROP_WOOD_CHAIR = blenderProp("prop_wood_chair", SoundType.WOOD, 1.2F, 0);
	public static final Block PROP_WOOD_CHAIR_PILE = multiProp("prop_wood_chair_pile", 2, 2, 2, SoundType.WOOD, 1.8F, 0);
	public static final Block PROP_FOLDING_TABLE = multiProp("prop_folding_table", 2, 1, 1, SoundType.WOOD, 1.5F, 0);
	public static final Block PROP_FILE_CABINET = multiProp("prop_file_cabinet", 1, 2, 1, SoundType.METAL, 2.4F, 0);
	public static final Block PROP_CRT_TV = blenderProp("prop_crt_tv", SoundType.GLASS, 1.8F, 4);
	public static final Block PROP_WATER_COOLER = multiProp("prop_water_cooler", 1, 2, 1, SoundType.GLASS, 1.4F, 0);
	public static final Block PROP_VENDING_MACHINE = multiProp("prop_vending_machine", 2, 2, 1, SoundType.METAL, 3.0F, 6);
	public static final Block PROP_FRIDGE = multiProp("prop_fridge", 2, 3, 1, SoundType.METAL, 2.8F, 0);

	// Shared BlockEntityType for all multi-block + Blender-rendered props (must be registered AFTER all blocks!)
	public static final BlockEntityType<PropBlockEntity> PROP_BLOCK_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			BackroomsMod.id("prop_block_entity"),
			FabricBlockEntityTypeBuilder.create(PropBlockEntity::new, BLENDER_PROP_BLOCKS.toArray(Block[]::new)).build()
	);

	private BackroomsProps() {
	}

	public static void register() {
		// Triggers static registration of all 59 blocks, BlockItems, PROP_PART, and PROP_BLOCK_ENTITY.
	}

	public static Map<String, Block> allBlocks() {
		return Collections.unmodifiableMap(ALL_BLOCKS);
	}

	public static Map<String, Item> allItems() {
		return Collections.unmodifiableMap(ALL_ITEMS);
	}

	private static Block station(String id, StationType stationType, SoundType sound, float strength, int light) {
		return register(id, props -> new WorkstationBlock(stationType, props),
				BlockBehaviour.Properties.of().mapColor(MapColor.METAL).sound(sound).strength(strength, 8.0F).lightLevel(s -> light).noOcclusion());
	}

	private static Block chest(String id, boolean locked, SoundType sound, float strength, int light) {
		return register(id, props -> new BackroomsLootChestBlock(locked, props),
				BlockBehaviour.Properties.of().mapColor(MapColor.METAL).sound(sound).strength(strength, 10.0F).lightLevel(s -> light).noOcclusion());
	}

	private static Block multiProp(String id, int width, int height, int depth, SoundType sound, float strength, int light) {
		Block b = register(id, props -> new MultiPartPropBlock(width, height, depth, props),
				BlockBehaviour.Properties.of().mapColor(MapColor.STONE).sound(sound).strength(strength, strength * 1.5F).lightLevel(s -> light).noOcclusion());
		BLENDER_PROP_BLOCKS.add(b);
		return b;
	}

	private static Block blenderProp(String id, SoundType sound, float strength, int light) {
		Block b = register(id, BlenderSinglePropBlock::new,
				BlockBehaviour.Properties.of().mapColor(MapColor.STONE).sound(sound).strength(strength, strength * 1.5F).lightLevel(s -> light).noOcclusion());
		BLENDER_PROP_BLOCKS.add(b);
		return b;
	}

	private static Block prop(String id, SoundType sound, float strength, int light) {
		return register(id, NoClipPropBlock::new,
				BlockBehaviour.Properties.of().mapColor(MapColor.STONE).sound(sound).strength(strength, strength * 1.5F).lightLevel(s -> light).noOcclusion());
	}

	private static Block registerBlockOnly(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties props) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, BackroomsMod.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, factory.apply(props.setId(blockKey)));
		BLENDER_PROP_BLOCKS.add(block);
		return block;
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties props) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, BackroomsMod.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, factory.apply(props.setId(blockKey)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, BackroomsMod.id(name));
		Item item = Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		ALL_BLOCKS.put(name, block);
		ALL_ITEMS.put(name, item);
		return block;
	}
}
