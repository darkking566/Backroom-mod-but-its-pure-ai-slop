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

	// 4 Custom Workstations
	public static final Block SALVAGE_BENCH = station("salvage_bench", StationType.SALVAGE, SoundType.WOOD, 2.5F, 0);
	public static final Block WORKSHOP_BENCH = station("workshop_bench", StationType.WORKSHOP, SoundType.ANVIL, 4.0F, 0);
	public static final Block ALMOND_DISTILLER = station("almond_distiller", StationType.DISTILLER, SoundType.COPPER, 3.0F, 4);
	public static final Block ANOMALY_ALTAR = station("anomaly_altar", StationType.ALTAR, SoundType.AMETHYST, 5.0F, 9);

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
