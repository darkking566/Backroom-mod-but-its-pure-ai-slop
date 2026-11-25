package dev.noclip.backrooms.props;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.crafting.StationType;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Registers all 4 Backrooms Crafting Workstations, 24 No-Clipped Props (P01..P24), and 3 Loot Chests.
 */
public final class BackroomsProps {
	private static final Map<String, Block> ALL_BLOCKS = new LinkedHashMap<>();
	private static final Map<String, Item> ALL_ITEMS = new LinkedHashMap<>();

	// 5 Custom Workstations (+ Spiderweb Crafting Table from GDD Section 5.1)
	public static final Block SALVAGE_BENCH = station("salvage_bench", StationType.SALVAGE, SoundType.WOOD, 2.5F, 0);
	public static final Block WORKSHOP_BENCH = station("workshop_bench", StationType.WORKSHOP, SoundType.ANVIL, 4.0F, 0);
	public static final Block ALMOND_DISTILLER = station("almond_distiller", StationType.DISTILLER, SoundType.COPPER, 3.0F, 4);
	public static final Block ANOMALY_ALTAR = station("anomaly_altar", StationType.ALTAR, SoundType.AMETHYST, 5.0F, 9);
	public static final Block SPIDERWEB_CRAFTING_TABLE = station("spiderweb_crafting_table", StationType.SPIDERWEB, SoundType.NETHERITE_BLOCK, 5.0F, 11);

	// 6 Overworld / Hub Processing & Progression Facilities (GDD Sections 2.1, 2.3, 5.2)
	public static final Block SCRAP_RECYCLER = station("scrap_recycler", StationType.SALVAGE, SoundType.METAL, 3.8F, 2);
	public static final Block ANOMALY_DISTILLERY_BARREL = station("anomaly_distillery_barrel", StationType.DISTILLER, SoundType.WOOD, 3.0F, 3);
	public static final Block ANOMALY_RESEARCH_TERMINAL = station("anomaly_research_terminal", StationType.WORKSHOP, SoundType.METAL, 4.2F, 7);
	public static final Block CRYSTAL_REPLICATOR = station("crystal_replicator", StationType.ALTAR, SoundType.AMETHYST, 5.5F, 10);
	public static final Block ANOMALY_ARCHIVE_BOARD = prop("anomaly_archive_board", SoundType.WOOD, 2.4F, 2);
	public static final Block RELAY_DROPOFF_BOX = prop("relay_dropoff_box", SoundType.METAL, 4.5F, 4);

	// 3 Tiered Loot Chests
	public static final Block LOCKED_ARMORY_CRATE = chest("locked_armory_crate", true, SoundType.METAL, 4.5F, 0);
	public static final Block ANOMALY_VAULT_CHEST = chest("anomaly_vault_chest", false, SoundType.AMETHYST, 5.0F, 6);
	public static final Block BOSS_TROPHY_CHEST = chest("boss_trophy_chest", false, SoundType.GILDED_BLACKSTONE, 4.0F, 10);

	// 24 No-Clipped Props (P01..P24 from 01_materials_and_props.md)
	public static final Block NOCLIPPED_WOOD_CHAIR = prop("noclipped_wood_chair", SoundType.WOOD, 1.4F, 0);
	public static final Block NOCLIPPED_OFFICE_CHAIR = prop("noclipped_office_chair", SoundType.WOOL, 1.4F, 0);
	public static final Block NOCLIPPED_FOLDING_TABLE = prop("noclipped_folding_table", SoundType.WOOD, 1.6F, 0);
	public static final Block NOCLIPPED_FILE_CABINET = prop("noclipped_file_cabinet", SoundType.METAL, 2.6F, 0);
	public static final Block NOCLIPPED_CRT_TV = prop("noclipped_crt_tv", SoundType.GLASS, 2.0F, 4);
	public static final Block NOCLIPPED_WATER_COOLER = prop("noclipped_water_cooler", SoundType.GLASS, 1.5F, 0);
	public static final Block NOCLIPPED_VENDING_MACHINE = prop("noclipped_vending_machine", SoundType.METAL, 3.2F, 6);
	public static final Block NOCLIPPED_FRIDGE = prop("noclipped_fridge", SoundType.METAL, 3.0F, 0);
	public static final Block BOARDED_BARRICADE = prop("boarded_barricade", SoundType.WOOD, 2.5F, 0);
	public static final Block BARBED_WIRE_FENCE = prop("barbed_wire_fence", SoundType.CHAIN, 2.5F, 0);
	public static final Block INDUSTRIAL_FUSE_BOX = prop("industrial_fuse_box", SoundType.METAL, 3.8F, 3);
	public static final Block REINFORCED_STEEL_BULKHEAD = prop("reinforced_steel_bulkhead", SoundType.ANVIL, 6.0F, 0);
	public static final Block STEAM_BOILER_UNIT = prop("steam_boiler_unit", SoundType.METAL, 5.0F, 4);
	public static final Block HOSPITAL_GURNEY = prop("hospital_gurney", SoundType.METAL, 2.2F, 0);
	public static final Block VINTAGE_CHANDELIER = prop("vintage_chandelier", SoundType.GLASS, 2.0F, 10);
	public static final Block ARCADE_CABINET = prop("arcade_cabinet", SoundType.METAL, 2.8F, 8);
	public static final Block NEON_SIGN_PROP = prop("neon_sign_prop", SoundType.GLASS, 1.8F, 12);
	public static final Block MOLD_VENT_GRATE = prop("mold_vent_grate", SoundType.METAL, 2.2F, 0);
	public static final Block POOL_CERAMIC_GRATE = prop("pool_ceramic_grate", SoundType.STONE, 2.4F, 0);
	public static final Block RED_ANOMALY_CLUSTER = prop("red_anomaly_cluster", SoundType.AMETHYST, 7.5F, 9);
	public static final Block CRYSTAL_VEIN_NODE = prop("crystal_vein_node", SoundType.AMETHYST, 7.0F, 8);
	public static final Block GLITCHED_WALL_SEAM = prop("glitched_wall_seam", SoundType.STONE, 8.0F, 6);
	public static final Block SEALED_VAULT_WALL = prop("sealed_vault_wall", SoundType.NETHERITE_BLOCK, 12.0F, 3);
	public static final Block WANDERER_REMAINS = prop("wanderer_remains", SoundType.BONE_BLOCK, 1.2F, 0);

	// 20 Level 0 & MATERIALS.md Breakable 3D Furniture / Intact-Damaged-NoClipped Variants (GDD 6.1 + MATERIALS.md)
	public static final Block PROP_DESKTOP_COMPUTER = prop("prop_desktop_computer", SoundType.METAL, 1.8F, 4);
	public static final Block PROP_DESKTOP_COMPUTER_DAMAGED = prop("prop_desktop_computer_damaged", SoundType.METAL, 1.5F, 1);
	public static final Block PROP_DESKTOP_COMPUTER_NOCLIPPED = prop("prop_desktop_computer_noclipped", SoundType.METAL, 2.2F, 5);
	public static final Block PROP_PHONE = prop("prop_phone", SoundType.GLASS, 0.9F, 3);
	public static final Block PROP_PHONE_CRACKED = prop("prop_phone_cracked", SoundType.GLASS, 0.8F, 1);
	public static final Block PROP_PHONE_NOCLIPPED = prop("prop_phone_noclipped", SoundType.GLASS, 1.4F, 4);
	public static final Block PROP_OFFICE_CHAIR = prop("prop_office_chair", SoundType.WOOL, 1.3F, 0);
	public static final Block PROP_SOFA = prop("prop_sofa", SoundType.WOOL, 1.6F, 0);
	public static final Block PROP_SOFA_NOCLIPPED = prop("prop_sofa_noclipped", SoundType.WOOL, 2.0F, 0);
	public static final Block PROP_GLASS_PARTITION = prop("prop_glass_partition", SoundType.GLASS, 1.4F, 0);
	public static final Block PROP_GLASS_PARTITION_BROKEN = prop("prop_glass_partition_broken", SoundType.GLASS, 1.0F, 0);
	public static final Block PROP_FOLDING_CHAIR_PILE = prop("prop_folding_chair_pile", SoundType.METAL, 1.7F, 0);
	public static final Block PROP_CURTAIN_HEAP = prop("prop_curtain_heap", SoundType.WOOL, 1.0F, 0);
	public static final Block PROP_STANDING_MIRROR = prop("prop_standing_mirror", SoundType.GLASS, 1.4F, 0);
	public static final Block PROP_POTTED_PLANT_PLASTIC = prop("prop_potted_plant_plastic", SoundType.GRASS, 0.9F, 0);
	public static final Block PROP_ELECTRIC_FAN = prop("prop_electric_fan", SoundType.METAL, 1.6F, 0);
	public static final Block PROP_MICROWAVE = prop("prop_microwave", SoundType.METAL, 2.0F, 2);
	public static final Block PROP_FLOOR_LAMP = prop("prop_floor_lamp", SoundType.GLASS, 1.4F, 9);
	public static final Block PROP_WALL_FIRST_AID_KIT = prop("prop_wall_first_aid_kit", SoundType.METAL, 1.5F, 0);
	public static final Block PROP_CARDBOARD_PILE = prop("prop_cardboard_pile", SoundType.WOOD, 1.0F, 0);

	private BackroomsProps() {
	}

	public static void register() {
		// Triggers static registration of all 31 blocks and BlockItems.
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

	private static Block prop(String id, SoundType sound, float strength, int light) {
		return register(id, NoClipPropBlock::new,
				BlockBehaviour.Properties.of().mapColor(MapColor.STONE).sound(sound).strength(strength, strength * 1.5F).lightLevel(s -> light).noOcclusion());
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
