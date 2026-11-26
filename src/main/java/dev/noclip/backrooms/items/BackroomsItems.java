package dev.noclip.backrooms.items;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.props.BackroomsProps;
import dev.noclip.backrooms.tools.BackroomsToolItem;
import dev.noclip.backrooms.weapons.BackroomsFirearmItem;
import dev.noclip.backrooms.weapons.BackroomsFirearmItem.FireMode;
import dev.noclip.backrooms.weapons.BackroomsMagicItem;
import dev.noclip.backrooms.weapons.BackroomsMagicItem.Spell;
import dev.noclip.backrooms.weapons.BackroomsMeleeItem;
import dev.noclip.backrooms.weapons.BackroomsMeleeItem.Style;
import dev.noclip.backrooms.weapons.BackroomsMeleeItem.Trait;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;

/**
 * Master item registry for the entire Backrooms crafting, salvage, tool, armor, melee, firearm,
 * ammo, tactical mod, monster drop, Boss core, and magic weapon catalog (docs/project/3-crafting/).
 * Also registers 4 dedicated Backrooms Creative Mode Tabs so JEI indexes every item automatically.
 */
public final class BackroomsItems {
	private static final Map<String, Item> ALL = new LinkedHashMap<>();
	private static final List<Item> TAB_MATERIALS = new ArrayList<>();
	private static final List<Item> TAB_GEAR = new ArrayList<>();
	private static final List<Item> TAB_MELEE = new ArrayList<>();
	private static final List<Item> TAB_RANGED_MAGIC = new ArrayList<>();

	// =========================================================================
	// 1. MATERIALS (01_materials_and_props.md: 76 new + 3 existing reused)
	// =========================================================================
	public static final Item DAMP_PAPER_SCRAP = mat("damp_paper_scrap", Rarity.COMMON);
	public static final Item ROTTEN_WOOD_STRIP = mat("rotten_wood_strip", Rarity.COMMON);
	public static final Item TREATED_PLANK = mat("treated_plank", Rarity.COMMON);
	public static final Item DAMP_CARPET_FIBER = mat("damp_carpet_fiber", Rarity.COMMON);
	public static final Item SYNTHETIC_FABRIC = mat("synthetic_fabric", Rarity.COMMON);
	public static final Item FOAM_PADDING = mat("foam_padding", Rarity.COMMON);
	public static final Item PLASTER_DUST = mat("plaster_dust", Rarity.COMMON);
	public static final Item MINERAL_WOOL = mat("mineral_wool", Rarity.COMMON);
	public static final Item CONCRETE_RUBBLE = mat("concrete_rubble", Rarity.COMMON);
	public static final Item CRACKED_BRICK = mat("cracked_brick", Rarity.COMMON);
	public static final Item GLASS_TUBE_SHARD = mat("glass_tube_shard", Rarity.COMMON);
	public static final Item CERAMIC_SHARD = mat("ceramic_shard", Rarity.COMMON);
	public static final Item PLASTIC_CASING = mat("plastic_casing", Rarity.COMMON);
	public static final Item RUSTY_NAIL = mat("rusty_nail", Rarity.COMMON);
	public static final Item RUSTY_SCREWS = mat("rusty_screws", Rarity.COMMON);
	public static final Item THIN_WIRE = mat("thin_wire", Rarity.COMMON);
	public static final Item BARBED_WIRE = mat("barbed_wire", Rarity.COMMON);
	public static final Item BONE_FRAGMENT = mat("bone_fragment", Rarity.COMMON);

	public static final Item SCRAP_METAL = mat("scrap_metal", Rarity.COMMON);
	public static final Item IRON_PIPE = mat("iron_pipe", Rarity.COMMON);
	public static final Item ANGLE_IRON = mat("angle_iron", Rarity.COMMON);
	public static final Item REBAR_FRAGMENT = mat("rebar_fragment", Rarity.COMMON);
	public static final Item SHEET_METAL = mat("sheet_metal", Rarity.COMMON);
	public static final Item HEAVY_STEEL_PLATE = mat("heavy_steel_plate", Rarity.UNCOMMON);
	public static final Item REFINED_STEEL_INGOT = mat("refined_steel_ingot", Rarity.UNCOMMON);
	public static final Item COPPER_WIRE = mat("copper_wire", Rarity.COMMON);
	public static final Item COPPER_PIPE = mat("copper_pipe", Rarity.COMMON);
	public static final Item BRASS_INGOT = mat("brass_ingot", Rarity.UNCOMMON);
	public static final Item LEAD_CHUNK = mat("lead_chunk", Rarity.COMMON);
	public static final Item TUNGSTEN_ALLOY = mat("tungsten_alloy", Rarity.RARE);
	public static final Item STAINLESS_ROD = mat("stainless_rod", Rarity.UNCOMMON);
	public static final Item ANOMALOUS_MYTHRIL = mat("anomalous_mythril", Rarity.RARE);
	public static final Item VOID_ALLOY_INGOT = mat("void_alloy_ingot", Rarity.EPIC);

	public static final Item METAL_SPRING = mat("metal_spring", Rarity.COMMON);
	public static final Item PIPE_VALVE = mat("pipe_valve", Rarity.COMMON);
	public static final Item STEEL_GEAR = mat("steel_gear", Rarity.UNCOMMON);
	public static final Item STEEL_WHEEL = mat("steel_wheel", Rarity.COMMON);
	public static final Item BALLAST_COIL = mat("ballast_coil", Rarity.COMMON);
	public static final Item CAPACITOR = mat("capacitor", Rarity.COMMON);
	public static final Item OLD_CIRCUIT_BOARD = mat("old_circuit_board", Rarity.UNCOMMON);
	public static final Item MICROCHIP = mat("microchip", Rarity.RARE);
	public static final Item BATTERY_CELL = mat("battery_cell", Rarity.UNCOMMON);
	public static final Item HIGH_VOLTAGE_COIL = mat("high_voltage_coil", Rarity.RARE);
	public static final Item ELECTRIC_MOTOR = mat("electric_motor", Rarity.UNCOMMON);
	public static final Item OPTICAL_LENS = mat("optical_lens", Rarity.UNCOMMON);
	public static final Item PRISM_GLASS = mat("prism_glass", Rarity.RARE);
	public static final Item NEON_TUBE = mat("neon_tube", Rarity.UNCOMMON);

	public static final Item MURKY_WATER_VIAL = mat("murky_water_vial", Rarity.COMMON);
	public static final Item COAL_COKE = mat("coal_coke", Rarity.COMMON);
	public static final Item CRUDE_FUEL_CAN = mat("crude_fuel_can", Rarity.COMMON);
	public static final Item COOLANT_VIAL = mat("coolant_vial", Rarity.UNCOMMON);
	public static final Item NITER_POWDER = mat("niter_powder", Rarity.COMMON);
	public static final Item SULFUR_DUST = mat("sulfur_dust", Rarity.COMMON);
	public static final Item BLACK_POWDER = mat("black_powder", Rarity.COMMON);
	public static final Item SMOKELESS_POWDER = mat("smokeless_powder", Rarity.UNCOMMON);
	public static final Item PRIMER_CAP = mat("primer_cap", Rarity.UNCOMMON);
	public static final Item ACID_VIAL = mat("acid_vial", Rarity.UNCOMMON);
	public static final Item TOXIC_SPORE_SAC = mat("toxic_spore_sac", Rarity.UNCOMMON);
	public static final Item PURIFIED_ALMOND_ESSENCE = mat("purified_almond_essence", Rarity.RARE);

	public static final Item RED_ROOM_SHARD = mat("red_room_shard", Rarity.RARE);
	public static final Item CURSED_FIBER = mat("cursed_fiber", Rarity.RARE);
	public static final Item BLOOD_RESONANCE_CORE = mat("blood_resonance_core", Rarity.RARE);
	public static final Item ANOMALY_CRYSTAL = mat("anomaly_crystal", Rarity.RARE);
	public static final Item THERMAL_CORE = mat("thermal_core", Rarity.RARE);
	public static final Item REALITY_FRAGMENT = mat("reality_fragment", Rarity.EPIC);
	public static final Item VOID_DUST = mat("void_dust", Rarity.RARE);
	public static final Item ALMOND_MANA_CELL = mat("almond_mana_cell", Rarity.RARE);
	public static final Item BLUEPRINT_SCRAP = mat("blueprint_scrap", Rarity.UNCOMMON);
	public static final Item WEAPON_BLUEPRINT_BLANK = mat("weapon_blueprint_blank", Rarity.RARE);
	public static final Item WEAPON_PARTS_SCRAP = mat("weapon_parts_scrap", Rarity.UNCOMMON);
	public static final Item PURE_ALMOND_DROP = mat("pure_almond_drop", Rarity.UNCOMMON);
	public static final Item WEAPON_GRIP_WRAP = mat("weapon_grip_wrap", Rarity.COMMON);
	public static final Item POLYMER_FRAME = mat("polymer_frame", Rarity.UNCOMMON);
	public static final Item QUENCH_OIL = mat("quench_oil", Rarity.UNCOMMON);
	public static final Item SILENCER_BAFFLE = mat("silencer_baffle", Rarity.UNCOMMON);
	public static final Item RESONANCE_CAPACITOR = mat("resonance_capacitor", Rarity.RARE);

	// =========================================================================
	// 1B. GDD ALIGNED MATERIALS, CIRCUITS, SCIENCE PACKS, TECH BRICKS & GLASS SERIES
	//     (docs/project/（原企劃書，已併入各章） & docs/project/3-crafting/by-level.md)
	// =========================================================================
	// Level 0 Office Junk, Salvage & Electronics (GDD 6.2 A/B/C)
	public static final Item RAGS = mat("rags", Rarity.COMMON);
	public static final Item BROKEN_TABLE_LEG = mat("broken_table_leg", Rarity.COMMON);
	public static final Item SCREW_NAIL_BOX = mat("screw_nail_box", Rarity.COMMON);
	public static final Item INSULATING_TAPE = mat("insulating_tape", Rarity.COMMON);
	public static final Item SUPER_GLUE = mat("super_glue", Rarity.COMMON);
	public static final Item STAPLER = mat("stapler", Rarity.UNCOMMON);
	public static final Item BALLPOINT_PEN = mat("ballpoint_pen", Rarity.COMMON);
	public static final Item RUBBER_BAND_BALL = mat("rubber_band_ball", Rarity.COMMON);
	public static final Item MAGAZINE_BUNDLE = mat("magazine_bundle", Rarity.COMMON);
	public static final Item BROKEN_MIRROR_SHARD = mat("broken_mirror_shard", Rarity.COMMON);
	public static final Item CASE_FAN = mat("case_fan", Rarity.COMMON);
	public static final Item CRT_TUBE = mat("crt_tube", Rarity.UNCOMMON);
	public static final Item FLICKERING_LAMP_TUBE = mat("flickering_lamp_tube", Rarity.UNCOMMON);
	public static final Item OFFICE_KEYBOARD = mat("office_keyboard", Rarity.COMMON);
	public static final Item LANDLINE_PHONE = mat("landline_phone", Rarity.COMMON);
	public static final Item EMPTY_PLASTIC_BOTTLE = mat("empty_plastic_bottle", Rarity.COMMON);
	public static final Item STERILE_GAUZE = mat("sterile_gauze", Rarity.COMMON);
	public static final Item RUBBING_ALCOHOL = mat("rubbing_alcohol", Rarity.COMMON);
	public static final Item DRY_SOIL_HERBS = mat("dry_soil_herbs", Rarity.COMMON);

	// Trade Goods, Deep Rewards & Monster/Level Exclusive Materials (GDD 5.2, 6.2 D, 6.3, MATERIALS.md)
	public static final Item STATIC_CASSETTE = mat("static_cassette", Rarity.UNCOMMON);
	public static final Item NOCLIP_SHARD = mat("noclip_shard", Rarity.EPIC);
	public static final Item FIRE_SALT = mat("fire_salt", Rarity.RARE);
	public static final Item SMILER_EYEBALL = mat("smiler_eyeball", Rarity.RARE);
	public static final Item RUSTED_STAIRWELL_KEY = mat("rusted_stairwell_key", Rarity.UNCOMMON);
	public static final Item LEVEL_KEYCARD = mat("level_keycard", Rarity.RARE);
	public static final Item RETRO_TERMINAL = mat("retro_terminal", Rarity.RARE);
	public static final Item TIRE = mat("tire", Rarity.COMMON);
	public static final Item RUBBER_CONVEYOR_BELT = mat("rubber_conveyor_belt", Rarity.UNCOMMON);

	// Glass Series (MATERIALS.md Section 7)
	public static final Item OFFICE_GLASS = mat("office_glass", Rarity.COMMON);
	public static final Item AUTO_SAFETY_GLASS = mat("auto_safety_glass", Rarity.UNCOMMON);
	public static final Item WIRE_MESH_GLASS = mat("wire_mesh_glass", Rarity.UNCOMMON);
	public static final Item REINFORCED_OBSERVATION_GLASS = mat("reinforced_observation_glass", Rarity.RARE);
	public static final Item SANCTUARY_STAINED_GLASS = mat("sanctuary_stained_glass", Rarity.RARE);
	public static final Item ANOMALOUS_MIRROR_GLASS = mat("anomalous_mirror_glass", Rarity.EPIC);

	// Factorio Circuits, Science Packs & Modules + Abiotic Factor Tech Bricks (GDD 5.2)
	public static final Item GREEN_CIRCUIT = mat("green_circuit", Rarity.COMMON);
	public static final Item RED_CIRCUIT = mat("red_circuit", Rarity.UNCOMMON);
	public static final Item BLUE_CIRCUIT = mat("blue_circuit", Rarity.RARE);
	public static final Item SCIENCE_PACK_YELLOW = mat("science_pack_yellow", Rarity.UNCOMMON);
	public static final Item SCIENCE_PACK_GRAY = mat("science_pack_gray", Rarity.RARE);
	public static final Item SCIENCE_PACK_PURPLE = mat("science_pack_purple", Rarity.EPIC);
	public static final Item MODULE_SPEED = mat("module_speed", Rarity.RARE);
	public static final Item MODULE_PRODUCTIVITY = mat("module_productivity", Rarity.RARE);
	public static final Item MODULE_EFFICIENCY = mat("module_efficiency", Rarity.RARE);
	public static final Item ENERGY_BRICK = mat("energy_brick", Rarity.UNCOMMON);
	public static final Item COMPUTATION_BRICK = mat("computation_brick", Rarity.UNCOMMON);
	public static final Item CONTROLLER_UNIT = mat("controller_unit", Rarity.UNCOMMON);

	// =========================================================================
	// 2. MONSTER DROPS & BOSS CORES (02_monsters_bosses_loot.md)
	// =========================================================================
	public static final Item HOUND_FANG = mat("hound_fang", Rarity.UNCOMMON);
	public static final Item HOUND_MATTED_HAIR = mat("hound_matted_hair", Rarity.COMMON);
	public static final Item HOUND_SINEW = mat("hound_sinew", Rarity.UNCOMMON);
	public static final Item SMILER_REMNANT = mat("smiler_remnant", Rarity.RARE);
	public static final Item SHADOW_MOTE = mat("shadow_mote", Rarity.UNCOMMON);
	public static final Item DREAD_CRYSTAL = mat("dread_crystal", Rarity.RARE);
	public static final Item FACELESS_MEMBRANE = mat("faceless_membrane", Rarity.UNCOMMON);
	public static final Item WORN_SUIT_CLOTH = mat("worn_suit_cloth", Rarity.COMMON);
	public static final Item MEMORY_SLIVER = mat("memory_sliver", Rarity.UNCOMMON);
	public static final Item THICK_DISGUISE_HIDE = mat("thick_disguise_hide", Rarity.UNCOMMON);
	public static final Item MORPH_GLAND = mat("morph_gland", Rarity.RARE);
	public static final Item TRANSLUCENT_BLOOD_VIAL = mat("translucent_blood_vial", Rarity.UNCOMMON);
	public static final Item MOTH_WING_SCALE = mat("moth_wing_scale", Rarity.COMMON);
	public static final Item ACID_SAC = mat("acid_sac", Rarity.UNCOMMON);
	public static final Item ACID_CRYSTAL = mat("acid_crystal", Rarity.RARE);
	public static final Item MOTH_CHITIN = mat("moth_chitin", Rarity.UNCOMMON);
	public static final Item TANGLED_KNUCKLE = mat("tangled_knuckle", Rarity.UNCOMMON);
	public static final Item GRASPING_TENDON = mat("grasping_tendon", Rarity.UNCOMMON);
	public static final Item ASH_RESIDUE = mat("ash_residue", Rarity.COMMON);
	public static final Item SILENCE_CORE = mat("silence_core", Rarity.RARE);
	public static final Item FALSE_GLASS = mat("false_glass", Rarity.UNCOMMON);
	public static final Item LURE_CORNEA = mat("lure_cornea", Rarity.RARE);
	public static final Item YELLOW_PARTY_HIDE = mat("yellow_party_hide", Rarity.UNCOMMON);
	public static final Item SMILEY_PATCH = mat("smiley_patch", Rarity.RARE);
	public static final Item PARTY_BALLOON_RUBBER = mat("party_balloon_rubber", Rarity.COMMON);
	public static final Item HOST_NERVE_CORD = mat("host_nerve_cord", Rarity.RARE);
	public static final Item WITHERED_HEART = mat("withered_heart", Rarity.RARE);
	public static final Item CORRUPTED_MARROW = mat("corrupted_marrow", Rarity.UNCOMMON);
	public static final Item BLACK_FILAMENT = mat("black_filament", Rarity.RARE);
	public static final Item STATIC_SCREECH_CORE = mat("static_screech_core", Rarity.RARE);
	public static final Item WATCH_OCULAR_LENS = mat("watch_ocular_lens", Rarity.RARE);
	public static final Item LONG_LIMB_BONE = mat("long_limb_bone", Rarity.UNCOMMON);
	public static final Item CEL_PAINT_SHARD = mat("cel_paint_shard", Rarity.UNCOMMON);
	public static final Item ANIMATION_JOINT = mat("animation_joint", Rarity.UNCOMMON);
	public static final Item SPECTRAL_CLOTH = mat("spectral_cloth", Rarity.RARE);
	public static final Item PLAGUE_ICHOR = mat("plague_ichor", Rarity.UNCOMMON);
	public static final Item SPIRAL_CHITIN = mat("spiral_chitin", Rarity.UNCOMMON);
	public static final Item GILDED_STEEL_SCRAP = mat("gilded_steel_scrap", Rarity.RARE);
	public static final Item RUNE_PARCHMENT = mat("rune_parchment", Rarity.RARE);
	public static final Item LUCK_TOKEN = mat("luck_token", Rarity.RARE);
	public static final Item PASTEL_CHARM = mat("pastel_charm", Rarity.EPIC);
	public static final Item ASTRAL_PARCHMENT = mat("astral_parchment", Rarity.EPIC);

	// 10 Boss Cores
	public static final Item CORE_CRIMSON_HEART = mat("core_crimson_heart", Rarity.EPIC);
	public static final Item CORE_ALPHA_FANG = mat("core_alpha_fang", Rarity.EPIC);
	public static final Item CORE_STEAM_TURBINE = mat("core_steam_turbine", Rarity.EPIC);
	public static final Item CORE_ARC_REACTOR = mat("core_arc_reactor", Rarity.EPIC);
	public static final Item CORE_ACID_QUEEN = mat("core_acid_queen", Rarity.EPIC);
	public static final Item CORE_MELANCHOLY_BELL = mat("core_melancholy_bell", Rarity.EPIC);
	public static final Item CORE_ABYSSAL_SCALE = mat("core_abyssal_scale", Rarity.EPIC);
	public static final Item CORE_STOPMOTION_GEAR = mat("core_stopmotion_gear", Rarity.EPIC);
	public static final Item CORE_FATE_DIE = mat("core_fate_die", Rarity.EPIC);
	public static final Item CORE_VOID_WEAVER = mat("core_void_weaver", Rarity.EPIC);

	// =========================================================================
	// 3. INTERMEDIATE CRAFTING PARTS (Tool, Melee, Firearm, Ammo, Magic)
	// =========================================================================
	public static final Item TOOL_HANDLE_BASIC = mat("tool_handle_basic", Rarity.COMMON);
	public static final Item TOOL_HANDLE_REINFORCED = mat("tool_handle_reinforced", Rarity.UNCOMMON);
	public static final Item INSULATED_GRIP = mat("insulated_grip", Rarity.UNCOMMON);
	public static final Item HYDRAULIC_MODULE = mat("hydraulic_module", Rarity.RARE);
	public static final Item RESONANCE_EMITTER = mat("resonance_emitter", Rarity.EPIC);

	public static final Item WEAPON_SHAFT_WOOD = mat("weapon_shaft_wood", Rarity.COMMON);
	public static final Item WEAPON_SHAFT_STEEL = mat("weapon_shaft_steel", Rarity.UNCOMMON);
	public static final Item CROSSGUARD_IRON = mat("crossguard_iron", Rarity.COMMON);
	public static final Item CROSSGUARD_BRASS = mat("crossguard_brass", Rarity.UNCOMMON);
	public static final Item POMMEL_LEAD = mat("pommel_lead", Rarity.COMMON);
	public static final Item FORGED_IRON_BLANK = mat("forged_iron_blank", Rarity.COMMON);
	public static final Item FORGED_STEEL_BLANK = mat("forged_steel_blank", Rarity.UNCOMMON);
	public static final Item FORGED_TUNGSTEN_BLANK = mat("forged_tungsten_blank", Rarity.RARE);
	public static final Item FORGED_MYTHRIL_BLANK = mat("forged_mythril_blank", Rarity.EPIC);
	public static final Item SPIKED_HEAD_IRON = mat("spiked_head_iron", Rarity.UNCOMMON);
	public static final Item HEAVY_HAMMER_HEAD = mat("heavy_hammer_head", Rarity.UNCOMMON);
	public static final Item FORGED_CHAIN_LINK = mat("forged_chain_link", Rarity.UNCOMMON);

	public static final Item GUN_BARREL_PIPE = mat("gun_barrel_pipe", Rarity.COMMON);
	public static final Item GUN_BARREL_RIFLED_SHORT = mat("gun_barrel_rifled_short", Rarity.UNCOMMON);
	public static final Item GUN_BARREL_RIFLED_LONG = mat("gun_barrel_rifled_long", Rarity.UNCOMMON);
	public static final Item GUN_BARREL_HEAVY_TUNGSTEN = mat("gun_barrel_heavy_tungsten", Rarity.RARE);
	public static final Item GUN_RECEIVER_BASIC = mat("gun_receiver_basic", Rarity.COMMON);
	public static final Item GUN_RECEIVER_PRECISION = mat("gun_receiver_precision", Rarity.UNCOMMON);
	public static final Item GUN_TRIGGER_GROUP = mat("gun_trigger_group", Rarity.UNCOMMON);
	public static final Item GUN_REVOLVER_CYLINDER = mat("gun_revolver_cylinder", Rarity.UNCOMMON);
	public static final Item GUN_PUMP_ACTION = mat("gun_pump_action", Rarity.UNCOMMON);
	public static final Item GUN_STOCK_WOOD = mat("gun_stock_wood", Rarity.COMMON);
	public static final Item GUN_STOCK_POLYMER = mat("gun_stock_polymer", Rarity.UNCOMMON);
	public static final Item GUN_MAG_PISTOL = mat("gun_mag_pistol", Rarity.COMMON);
	public static final Item GUN_MAG_RIFLE = mat("gun_mag_rifle", Rarity.UNCOMMON);
	public static final Item GUN_COIL_ACCELERATOR = mat("gun_coil_accelerator", Rarity.RARE);

	public static final Item CASING_PISTOL = mat("casing_pistol", Rarity.COMMON);
	public static final Item CASING_RIFLE = mat("casing_rifle", Rarity.COMMON);
	public static final Item HULL_SHOTGUN = mat("hull_shotgun", Rarity.COMMON);
	public static final Item BULLET_LEAD_TIP = mat("bullet_lead_tip", Rarity.COMMON);
	public static final Item BULLET_AP_CORE = mat("bullet_ap_core", Rarity.RARE);

	public static final Item STAFF_ROD_INSULATED = mat("staff_rod_insulated", Rarity.RARE);
	public static final Item FOCUS_PRISM_CORE = mat("focus_prism_core", Rarity.RARE);
	public static final Item GRIMOIRE_BINDING = mat("grimoire_binding", Rarity.RARE);

	// =========================================================================
	// 4. COMPENDIUM, TIER 1-4 TOOLS, SURVIVAL CONSUMABLES & 4 ARMOR SETS
	// =========================================================================
	public static final Item BACKROOMS_COMPENDIUM = gear("backrooms_compendium",
			new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON),
			p -> new BackroomsConsumableItem(p, BackroomsConsumableItem.Kind.COMPENDIUM, "lore.noclip_backrooms.compendium"));

	public static final Item IMPROVISED_CROWBAR = tool("improvised_crowbar", ToolMaterial.IRON, 1, 280, 4.5F, -2.5F, Rarity.COMMON, "lore.noclip_backrooms.improvised_crowbar");
	public static final Item WIRE_CUTTERS = tool("wire_cutters", ToolMaterial.IRON, 1, 240, 2.0F, -2.2F, Rarity.COMMON, "lore.noclip_backrooms.wire_cutters");
	public static final Item PIPE_WRENCH = tool("pipe_wrench", ToolMaterial.IRON, 1, 320, 5.5F, -2.8F, Rarity.COMMON, "lore.noclip_backrooms.pipe_wrench");
	public static final Item LOCKPICK_SET = gear("lockpick_set", new Item.Properties().stacksTo(16),
			p -> new BackroomsToolItem(p, 1, "lore.noclip_backrooms.lockpick_set"));
	public static final Item INSULATED_SHEARS = tool("insulated_shears", ToolMaterial.DIAMOND, 2, 520, 3.0F, -2.2F, Rarity.UNCOMMON, "lore.noclip_backrooms.insulated_shears");
	public static final Item HYDRAULIC_CUTTER = tool("hydraulic_cutter", ToolMaterial.DIAMOND, 2, 760, 6.0F, -2.9F, Rarity.UNCOMMON, "lore.noclip_backrooms.hydraulic_cutter");
	public static final Item INDUSTRIAL_SAW = tool("industrial_saw", ToolMaterial.DIAMOND, 2, 920, 7.0F, -2.8F, Rarity.UNCOMMON, "lore.noclip_backrooms.industrial_saw");
	public static final Item ANOMALY_RESONANCE_PICK = tool("anomaly_resonance_pick", ToolMaterial.NETHERITE, 3, 1500, 6.0F, -2.7F, Rarity.RARE, "lore.noclip_backrooms.anomaly_resonance_pick");
	public static final Item CRIMSON_WALLBREAKER = tool("crimson_wallbreaker", ToolMaterial.NETHERITE, 3, 1650, 9.0F, -3.0F, Rarity.RARE, "lore.noclip_backrooms.crimson_wallbreaker");
	public static final Item VOID_DISRUPTOR = tool("void_disruptor", ToolMaterial.NETHERITE, 4, 2800, 10.0F, -2.6F, Rarity.EPIC, "lore.noclip_backrooms.void_disruptor");

	// Abiotic Factor Precision Disassembly & Keypad Hackers T1-T4 (GDD 4.3 & 5.2)
	public static final Item PRECISION_SCREWDRIVER = tool("precision_screwdriver", ToolMaterial.DIAMOND, 2, 640, 3.0F, -2.0F, Rarity.UNCOMMON, "lore.noclip_backrooms.precision_screwdriver");
	public static final Item KEYPAD_HACKER_T1 = gear("keypad_hacker_t1", new Item.Properties().stacksTo(1).durability(120).rarity(Rarity.COMMON),
			p -> new BackroomsToolItem(p, 1, "lore.noclip_backrooms.keypad_hacker_t1"));
	public static final Item KEYPAD_HACKER_T2 = gear("keypad_hacker_t2", new Item.Properties().stacksTo(1).durability(240).rarity(Rarity.UNCOMMON),
			p -> new BackroomsToolItem(p, 2, "lore.noclip_backrooms.keypad_hacker_t2"));
	public static final Item KEYPAD_HACKER_T3 = gear("keypad_hacker_t3", new Item.Properties().stacksTo(1).durability(420).rarity(Rarity.RARE),
			p -> new BackroomsToolItem(p, 3, "lore.noclip_backrooms.keypad_hacker_t3"));
	public static final Item KEYPAD_HACKER_T4 = gear("keypad_hacker_t4", new Item.Properties().stacksTo(1).durability(800).rarity(Rarity.EPIC),
			p -> new BackroomsToolItem(p, 4, "lore.noclip_backrooms.keypad_hacker_t4"));

	public static final Item FLASHLIGHT_BATTERY_PACK = gear("flashlight_battery_pack", new Item.Properties().stacksTo(16),
			p -> new BackroomsConsumableItem(p, BackroomsConsumableItem.Kind.BATTERY_PACK, "lore.noclip_backrooms.battery_pack"));
	public static final Item TACTICAL_UV_FLASHLIGHT = gear("tactical_uv_flashlight", new Item.Properties().stacksTo(1).durability(450).rarity(Rarity.RARE),
			p -> new BackroomsConsumableItem(p, BackroomsConsumableItem.Kind.UV_FLASHLIGHT, "lore.noclip_backrooms.uv_flashlight"));
	public static final Item FIELD_BANDAGE = gear("field_bandage", new Item.Properties().stacksTo(16),
			p -> new BackroomsConsumableItem(p, BackroomsConsumableItem.Kind.FIELD_BANDAGE, "lore.noclip_backrooms.field_bandage"));
	public static final Item ALMOND_ELIXIR = gear("almond_elixir", new Item.Properties().stacksTo(16).rarity(Rarity.RARE),
			p -> new BackroomsConsumableItem(p, BackroomsConsumableItem.Kind.ALMOND_ELIXIR, "lore.noclip_backrooms.almond_elixir"));
	public static final Item PHOSPHOR_FLARE = gear("phosphor_flare", new Item.Properties().stacksTo(16),
			p -> new BackroomsConsumableItem(p, BackroomsConsumableItem.Kind.PHOSPHOR_FLARE, "lore.noclip_backrooms.phosphor_flare"));
	public static final Item TRIPWIRE_SHRAPNEL_MINE = gear("tripwire_shrapnel_mine", new Item.Properties().stacksTo(16),
			p -> new BackroomsConsumableItem(p, BackroomsConsumableItem.Kind.SHRAPNEL_MINE, "lore.noclip_backrooms.shrapnel_mine"));

	// GDD Survival Consumables, Extraction Anchor, Geodes, Coins, Utility & Forbidden Scrolls (GDD 2.3, 3.3, 5.2, 6.2-6.4)
	public static final Item OLD_BANDAID = gear("old_bandaid", new Item.Properties().stacksTo(32),
			p -> new BackroomsConsumableItem(p, BackroomsConsumableItem.Kind.OLD_BANDAID, "lore.noclip_backrooms.old_bandaid"));
	public static final Item EXPIRED_PAINKILLERS = gear("expired_painkillers", new Item.Properties().stacksTo(16),
			p -> new BackroomsConsumableItem(p, BackroomsConsumableItem.Kind.EXPIRED_PAINKILLERS, "lore.noclip_backrooms.expired_painkillers"));
	public static final Item EXPIRED_CANNED_FOOD = gear("expired_canned_food", new Item.Properties().stacksTo(16),
			p -> new BackroomsConsumableItem(p, BackroomsConsumableItem.Kind.EXPIRED_CANNED_FOOD, "lore.noclip_backrooms.expired_canned_food"));
	public static final Item RATION_BISCUIT = gear("ration_biscuit", new Item.Properties().stacksTo(32),
			p -> new BackroomsConsumableItem(p, BackroomsConsumableItem.Kind.RATION_BISCUIT, "lore.noclip_backrooms.ration_biscuit"));
	public static final Item IMPROVISED_CALMING_TEA = gear("improvised_calming_tea", new Item.Properties().stacksTo(16),
			p -> new BackroomsConsumableItem(p, BackroomsConsumableItem.Kind.IMPROVISED_CALMING_TEA, "lore.noclip_backrooms.improvised_calming_tea"));
	public static final Item ROUGH_BANDAGE = gear("rough_bandage", new Item.Properties().stacksTo(16),
			p -> new BackroomsConsumableItem(p, BackroomsConsumableItem.Kind.ROUGH_BANDAGE, "lore.noclip_backrooms.rough_bandage"));
	public static final Item STERILE_BANDAGE = gear("sterile_bandage", new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON),
			p -> new BackroomsConsumableItem(p, BackroomsConsumableItem.Kind.STERILE_BANDAGE, "lore.noclip_backrooms.sterile_bandage"));
	public static final Item AGED_ALMOND_RESERVE = gear("aged_almond_reserve", new Item.Properties().stacksTo(16).rarity(Rarity.EPIC),
			p -> new BackroomsConsumableItem(p, BackroomsConsumableItem.Kind.AGED_ALMOND_RESERVE, "lore.noclip_backrooms.aged_almond_reserve"));
	// 3-Tier Wretch Rejection & Overworld Seepage Stabilizers (Mode 1 + Mode 2 Horror Loop)
	public static final Item REALITY_STABILIZER_T1 = gear("reality_stabilizer_t1", new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON),
			p -> new BackroomsConsumableItem(p, BackroomsConsumableItem.Kind.REALITY_STABILIZER_T1, "lore.noclip_backrooms.reality_stabilizer_t1"));
	public static final Item REALITY_SERUM_T2 = gear("reality_serum_t2", new Item.Properties().stacksTo(8).rarity(Rarity.RARE),
			p -> new BackroomsConsumableItem(p, BackroomsConsumableItem.Kind.REALITY_SERUM_T2, "lore.noclip_backrooms.reality_serum_t2"));
	public static final Item REALITY_ANCHOR_CORE = gear("reality_anchor_core", new Item.Properties().stacksTo(4).rarity(Rarity.EPIC),
			p -> new BackroomsConsumableItem(p, BackroomsConsumableItem.Kind.REALITY_ANCHOR_CORE, "lore.noclip_backrooms.reality_anchor_core"));
	public static final Item MOLOTOV_COCKTAIL = gear("molotov_cocktail", new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON),
			p -> new BackroomsConsumableItem(p, BackroomsConsumableItem.Kind.MOLOTOV_COCKTAIL, "lore.noclip_backrooms.molotov_cocktail"));
	public static final Item TACTICAL_CORNER_MIRROR = gear("tactical_corner_mirror", new Item.Properties().stacksTo(1).durability(240).rarity(Rarity.UNCOMMON),
			p -> new BackroomsConsumableItem(p, BackroomsConsumableItem.Kind.TACTICAL_CORNER_MIRROR, "lore.noclip_backrooms.tactical_corner_mirror"));
	public static final Item HOMEWARD_ANCHOR = gear("homeward_anchor", new Item.Properties().stacksTo(4).rarity(Rarity.EPIC),
			p -> new BackroomsConsumableItem(p, BackroomsConsumableItem.Kind.HOMEWARD_ANCHOR, "lore.noclip_backrooms.homeward_anchor"));
	public static final Item LOST_OLD_COIN = gear("lost_old_coin", new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON),
			p -> new BackroomsConsumableItem(p, BackroomsConsumableItem.Kind.LOST_OLD_COIN, "lore.noclip_backrooms.lost_old_coin"));
	public static final Item RUSTED_OFFICE_SAFE = gear("rusted_office_safe", new Item.Properties().stacksTo(16).rarity(Rarity.RARE),
			p -> new BackroomsConsumableItem(p, BackroomsConsumableItem.Kind.GEODE_SAFE, "lore.noclip_backrooms.rusted_office_safe"));
	public static final Item ENCRYPTED_HARD_DRIVE = gear("encrypted_hard_drive", new Item.Properties().stacksTo(16).rarity(Rarity.RARE),
			p -> new BackroomsConsumableItem(p, BackroomsConsumableItem.Kind.GEODE_DRIVE, "lore.noclip_backrooms.encrypted_hard_drive"));
	public static final Item FORBIDDEN_SCROLL_LUCK = gear("forbidden_scroll_luck", new Item.Properties().stacksTo(16).rarity(Rarity.EPIC),
			p -> new BackroomsConsumableItem(p, BackroomsConsumableItem.Kind.FORBIDDEN_SCROLL_LUCK, "lore.noclip_backrooms.forbidden_scroll_luck"));
	public static final Item FORBIDDEN_SCROLL_PSY = gear("forbidden_scroll_psy", new Item.Properties().stacksTo(16).rarity(Rarity.EPIC),
			p -> new BackroomsConsumableItem(p, BackroomsConsumableItem.Kind.FORBIDDEN_SCROLL_PSY, "lore.noclip_backrooms.forbidden_scroll_psy"));
	public static final Item FORBIDDEN_SCROLL_TECH = gear("forbidden_scroll_tech", new Item.Properties().stacksTo(16).rarity(Rarity.EPIC),
			p -> new BackroomsConsumableItem(p, BackroomsConsumableItem.Kind.FORBIDDEN_SCROLL_TECH, "lore.noclip_backrooms.forbidden_scroll_tech"));

	// Early Improvised Office Armor (GDD 6.2 & 6.4) + Gas Mask + 4 Full Armor Sets (19 pieces)
	public static final Item MAGAZINE_ARMOR_CHESTPLATE = armor("magazine_armor_chestplate", BackroomsArmorMaterials.IMPROVISED_OFFICE, ArmorType.CHESTPLATE, Rarity.COMMON);
	public static final Item SILENT_CARPET_BOOTS = armor("silent_carpet_boots", BackroomsArmorMaterials.IMPROVISED_OFFICE, ArmorType.BOOTS, Rarity.COMMON);
	public static final Item ALMOND_GAS_MASK = armor("almond_gas_mask", BackroomsArmorMaterials.GAS_MASK, ArmorType.HELMET, Rarity.UNCOMMON);
	public static final Item SCRAP_TACTICAL_HELMET = armor("scrap_tactical_helmet", BackroomsArmorMaterials.SCRAP_TACTICAL, ArmorType.HELMET, Rarity.UNCOMMON);
	public static final Item SCRAP_TACTICAL_CHESTPLATE = armor("scrap_tactical_chestplate", BackroomsArmorMaterials.SCRAP_TACTICAL, ArmorType.CHESTPLATE, Rarity.UNCOMMON);
	public static final Item SCRAP_TACTICAL_LEGGINGS = armor("scrap_tactical_leggings", BackroomsArmorMaterials.SCRAP_TACTICAL, ArmorType.LEGGINGS, Rarity.UNCOMMON);
	public static final Item SCRAP_TACTICAL_BOOTS = armor("scrap_tactical_boots", BackroomsArmorMaterials.SCRAP_TACTICAL, ArmorType.BOOTS, Rarity.UNCOMMON);
	public static final Item SKIN_STEALER_HELMET = armor("skin_stealer_helmet", BackroomsArmorMaterials.SKIN_STEALER, ArmorType.HELMET, Rarity.RARE);
	public static final Item SKIN_STEALER_CHESTPLATE = armor("skin_stealer_chestplate", BackroomsArmorMaterials.SKIN_STEALER, ArmorType.CHESTPLATE, Rarity.RARE);
	public static final Item SKIN_STEALER_LEGGINGS = armor("skin_stealer_leggings", BackroomsArmorMaterials.SKIN_STEALER, ArmorType.LEGGINGS, Rarity.RARE);
	public static final Item SKIN_STEALER_BOOTS = armor("skin_stealer_boots", BackroomsArmorMaterials.SKIN_STEALER, ArmorType.BOOTS, Rarity.RARE);
	public static final Item BOILER_THERMAL_HELMET = armor("boiler_thermal_helmet", BackroomsArmorMaterials.BOILER_THERMAL, ArmorType.HELMET, Rarity.RARE);
	public static final Item BOILER_THERMAL_CHESTPLATE = armor("boiler_thermal_chestplate", BackroomsArmorMaterials.BOILER_THERMAL, ArmorType.CHESTPLATE, Rarity.RARE);
	public static final Item BOILER_THERMAL_LEGGINGS = armor("boiler_thermal_leggings", BackroomsArmorMaterials.BOILER_THERMAL, ArmorType.LEGGINGS, Rarity.RARE);
	public static final Item BOILER_THERMAL_BOOTS = armor("boiler_thermal_boots", BackroomsArmorMaterials.BOILER_THERMAL, ArmorType.BOOTS, Rarity.RARE);
	public static final Item ABYSSAL_VOID_HELMET = armor("abyssal_void_helmet", BackroomsArmorMaterials.ABYSSAL_VOID, ArmorType.HELMET, Rarity.EPIC);
	public static final Item ABYSSAL_VOID_CHESTPLATE = armor("abyssal_void_chestplate", BackroomsArmorMaterials.ABYSSAL_VOID, ArmorType.CHESTPLATE, Rarity.EPIC);
	public static final Item ABYSSAL_VOID_LEGGINGS = armor("abyssal_void_leggings", BackroomsArmorMaterials.ABYSSAL_VOID, ArmorType.LEGGINGS, Rarity.EPIC);
	public static final Item ABYSSAL_VOID_BOOTS = armor("abyssal_void_boots", BackroomsArmorMaterials.ABYSSAL_VOID, ArmorType.BOOTS, Rarity.EPIC);

	// =========================================================================
	// 5. MELEE WEAPONS (04_weapons_melee.md: 41 + improvised_crowbar = 42)
	// =========================================================================
	// Medieval Blunt (11)
	public static final Item BLUNT_RUSTY_PIPE = melee("blunt_rusty_pipe", Style.BLUNT, 0, 5.0F, 1.4F, 0.0F, 180, Trait.KNOCKBACK, Rarity.COMMON);
	public static final Item BLUNT_NAILED_BAT = melee("blunt_nailed_bat", Style.BLUNT, 1, 7.5F, 1.3F, 0.0F, 260, Trait.BLEED, Rarity.COMMON);
	public static final Item BLUNT_BARBED_KANABO = melee("blunt_barbed_kanabo", Style.BLUNT, 1, 9.0F, 1.1F, 0.3F, 380, Trait.BLEED, Rarity.UNCOMMON);
	public static final Item BLUNT_IRON_MACE = melee("blunt_iron_mace", Style.BLUNT, 1, 8.5F, 1.3F, 0.0F, 420, Trait.ARMOR_PIERCE_20, Rarity.UNCOMMON);
	public static final Item BLUNT_FLANGED_MACE = melee("blunt_flanged_mace", Style.BLUNT, 2, 10.5F, 1.25F, 0.0F, 680, Trait.ARMOR_PIERCE_35, Rarity.UNCOMMON);
	public static final Item BLUNT_WARHAMMER_1H = melee("blunt_warhammer_1h", Style.BLUNT, 2, 10.0F, 1.2F, 0.2F, 650, Trait.HEAVY_SLAM, Rarity.UNCOMMON);
	public static final Item BLUNT_CLUMP_FLAIL = melee("blunt_clump_flail", Style.BLUNT, 2, 12.0F, 1.0F, 0.8F, 720, Trait.ARMOR_PIERCE_35, Rarity.RARE);
	public static final Item BLUNT_TWOHAND_MAUL = melee("blunt_twohand_maul", Style.BLUNT, 2, 14.5F, 0.85F, 0.4F, 880, Trait.HEAVY_SLAM, Rarity.RARE);
	public static final Item BLUNT_LUCERNE_HAMMER = melee("blunt_lucerne_hammer", Style.BLUNT, 3, 13.5F, 0.95F, 1.5F, 1250, Trait.BOSS_BREAKER, Rarity.RARE);
	public static final Item BLUNT_CRIMSON_CRUSHER = melee("blunt_crimson_crusher", Style.BLUNT, 3, 17.0F, 0.85F, 0.6F, 1650, Trait.CRIMSON_SHOCKWAVE, Rarity.EPIC);
	public static final Item BLUNT_THUNDER_COLOSSUS = melee("blunt_thunder_colossus", Style.BLUNT, 4, 20.0F, 0.9F, 0.8F, 2400, Trait.THUNDER_AOE, Rarity.EPIC);

	// Western (14)
	public static final Item WEST_SCRAP_KNIFE = melee("west_scrap_knife", Style.WESTERN, 0, 4.5F, 2.4F, 0.0F, 160, Trait.NONE, Rarity.COMMON);
	public static final Item WEST_HOUND_DAGGER = melee("west_hound_dagger", Style.WESTERN, 1, 6.5F, 2.3F, 0.0F, 340, Trait.BACKSTAB, Rarity.UNCOMMON);
	public static final Item WEST_GLADIUS = melee("west_gladius", Style.WESTERN, 1, 7.5F, 1.8F, 0.0F, 450, Trait.NONE, Rarity.UNCOMMON);
	public static final Item WEST_ARMING_SWORD = melee("west_arming_sword", Style.WESTERN, 2, 9.5F, 1.6F, 0.2F, 720, Trait.NONE, Rarity.UNCOMMON);
	public static final Item WEST_RAPIER = melee("west_rapier", Style.WESTERN, 2, 8.0F, 2.2F, 0.5F, 660, Trait.ARMOR_PIERCE_45, Rarity.RARE);
	public static final Item WEST_VIKING_AXE = melee("west_viking_axe", Style.WESTERN, 2, 10.5F, 1.25F, 0.0F, 700, Trait.ARMOR_PIERCE_20, Rarity.UNCOMMON);
	public static final Item WEST_CLAYMORE = melee("west_claymore", Style.WESTERN, 2, 12.5F, 1.1F, 0.6F, 850, Trait.CLEAVE, Rarity.RARE);
	public static final Item WEST_EXECUTIONER_AXE = melee("west_executioner_axe", Style.WESTERN, 2, 15.0F, 0.85F, 0.3F, 900, Trait.EXECUTE, Rarity.RARE);
	public static final Item WEST_HALBERD = melee("west_halberd", Style.WESTERN, 3, 13.0F, 1.05F, 1.6F, 1300, Trait.KNOCKBACK, Rarity.RARE);
	public static final Item WEST_ZWEIHANDER = melee("west_zweihander", Style.WESTERN, 3, 15.5F, 0.95F, 0.9F, 1450, Trait.CLEAVE, Rarity.RARE);
	public static final Item WEST_ACID_SCIMITAR = melee("west_acid_scimitar", Style.WESTERN, 3, 12.0F, 1.65F, 0.2F, 1150, Trait.ACID_CORRODE, Rarity.RARE);
	public static final Item WEST_HALLOWED_BLADE = melee("west_hallowed_blade", Style.WESTERN, 3, 14.0F, 1.65F, 0.3F, 1500, Trait.UNDEAD_SMILER_BANE, Rarity.RARE);
	public static final Item WEST_HOTEL_GREATSWORD = melee("west_hotel_greatsword", Style.WESTERN, 3, 17.5F, 1.05F, 0.7F, 1850, Trait.VAMPIRIC_CLEANSE, Rarity.EPIC);
	public static final Item WEST_VOID_GREATSWORD = melee("west_void_greatsword", Style.WESTERN, 4, 22.0F, 1.1F, 0.9F, 2600, Trait.VOID_WAVE, Rarity.EPIC);

	// Chinese (16)
	public static final Item CN_WOOD_STAFF = melee("cn_wood_staff", Style.CHINESE, 0, 5.0F, 1.7F, 0.8F, 200, Trait.KNOCKBACK, Rarity.COMMON);
	public static final Item CN_IRON_STAFF = melee("cn_iron_staff", Style.CHINESE, 1, 8.0F, 1.55F, 1.0F, 480, Trait.CLEAVE, Rarity.UNCOMMON);
	public static final Item CN_PEACHWOOD_SWORD = melee("cn_peachwood_sword", Style.CHINESE, 1, 6.0F, 1.8F, 0.0F, 360, Trait.PEACHWOOD_BANE, Rarity.UNCOMMON);
	public static final Item CN_RING_POMMEL_DAO = melee("cn_ring_pommel_dao", Style.CHINESE, 1, 8.5F, 1.65F, 0.0F, 640, Trait.NONE, Rarity.UNCOMMON);
	public static final Item CN_TANG_HENGDAO = melee("cn_tang_hengdao", Style.CHINESE, 2, 10.5F, 1.75F, 0.2F, 820, Trait.ARMOR_PIERCE_20, Rarity.RARE);
	public static final Item CN_HAN_JIAN = melee("cn_han_jian", Style.CHINESE, 2, 10.0F, 1.85F, 0.3F, 780, Trait.ARMOR_PIERCE_20, Rarity.UNCOMMON);
	public static final Item CN_YANLING_DAO = melee("cn_yanling_dao", Style.CHINESE, 2, 11.0F, 1.65F, 0.2F, 800, Trait.CLEAVE, Rarity.UNCOMMON);
	public static final Item CN_MIAODAO = melee("cn_miaodao", Style.CHINESE, 2, 13.0F, 1.35F, 1.1F, 920, Trait.CLEAVE, Rarity.RARE);
	public static final Item CN_NUNCHAKU = melee("cn_nunchaku", Style.CHINESE, 2, 7.5F, 2.6F, 0.0F, 720, Trait.COMBO_STUN, Rarity.UNCOMMON);
	public static final Item CN_RED_TASSEL_SPEAR = melee("cn_red_tassel_spear", Style.CHINESE, 2, 11.5F, 1.4F, 1.8F, 880, Trait.ARMOR_PIERCE_35, Rarity.RARE);
	public static final Item CN_METEOR_HAMMER = melee("cn_meteor_hammer", Style.CHINESE, 2, 12.5F, 1.15F, 0.8F, 860, Trait.METEOR_PULL, Rarity.RARE);
	public static final Item CN_SLEEVE_DARTS = melee("cn_sleeve_darts", Style.CHINESE, 2, 6.0F, 2.2F, 0.0F, 620, Trait.SLEEVE_DARTS, Rarity.UNCOMMON);
	public static final Item CN_GUANDAO = melee("cn_guandao", Style.CHINESE, 3, 16.0F, 0.95F, 1.6F, 1400, Trait.CLEAVE, Rarity.RARE);
	public static final Item CN_FANGTIAN_JI = melee("cn_fangtian_ji", Style.CHINESE, 3, 17.5F, 1.15F, 1.8F, 1750, Trait.STOPMOTION_FREEZE, Rarity.EPIC);
	public static final Item CN_COIN_SWORD = melee("cn_coin_sword", Style.CHINESE, 3, 11.5F, 1.75F, 0.3F, 1350, Trait.COIN_WARD, Rarity.RARE);
	public static final Item CN_ALPHA_TANGDAO = melee("cn_alpha_tangdao", Style.CHINESE, 4, 21.0F, 1.2F, 0.9F, 2500, Trait.ALPHA_BLOODLUST, Rarity.EPIC);

	// =========================================================================
	// 6. FIREARMS (18), AMMO (11), TACTICAL MODS (6) (05_weapons_firearms.md)
	// =========================================================================
	public static final Item GUN_PIPE_PISTOL = gun("gun_pipe_pistol", 1, List.of("ammo_scrap_slug", "ammo_9mm"), 10.0F, 1, 26.0F, 24, 0.05F, 180, FireMode.STANDARD, Rarity.COMMON);
	public static final Item GUN_PIPE_SHOTGUN = gun("gun_pipe_shotgun", 1, List.of("ammo_12g_buck", "ammo_12g_acid"), 3.2F, 6, 16.0F, 26, 0.14F, 240, FireMode.SHOTGUN, Rarity.UNCOMMON);
	public static final Item GUN_NAILGUN = gun("gun_nailgun", 1, List.of("ammo_steel_nails"), 5.5F, 1, 22.0F, 7, 0.04F, 360, FireMode.SILENT_NAIL, Rarity.COMMON);
	public static final Item GUN_TASER = gun("gun_taser", 2, List.of("battery_cell"), 4.0F, 1, 14.0F, 26, 0.02F, 320, FireMode.TASER_STUN, Rarity.UNCOMMON);
	public static final Item GUN_FLAMETHROWER = gun("gun_flamethrower", 3, List.of("ammo_fuel_canister"), 9.0F, 1, 10.0F, 6, 0.12F, 680, FireMode.FLAMETHROWER, Rarity.EPIC);

	public static final Item GUN_REVOLVER = gun("gun_revolver", 2, List.of("ammo_9mm", "ammo_308"), 13.0F, 1, 34.0F, 14, 0.025F, 520, FireMode.STANDARD, Rarity.UNCOMMON);
	public static final Item GUN_PISTOL_9MM = gun("gun_pistol_9mm", 2, List.of("ammo_9mm"), 8.5F, 1, 30.0F, 7, 0.03F, 560, FireMode.STANDARD, Rarity.UNCOMMON);
	public static final Item GUN_HEAVY_HANDGUN = gun("gun_heavy_handgun", 3, List.of("ammo_308", "ammo_308_ap"), 17.0F, 1, 38.0F, 18, 0.02F, 740, FireMode.PIERCING_AP, Rarity.RARE);

	public static final Item GUN_DOUBLE_BARREL = gun("gun_double_barrel", 2, List.of("ammo_12g_buck", "ammo_12g_acid"), 3.8F, 8, 18.0F, 22, 0.13F, 580, FireMode.SHOTGUN, Rarity.UNCOMMON);
	public static final Item GUN_PUMP_SHOTGUN = gun("gun_pump_shotgun", 2, List.of("ammo_12g_buck", "ammo_12g_acid"), 3.6F, 8, 20.0F, 16, 0.11F, 680, FireMode.SHOTGUN, Rarity.UNCOMMON);
	public static final Item GUN_AUTO_SHOTGUN = gun("gun_auto_shotgun", 3, List.of("ammo_12g_acid", "ammo_12g_buck"), 4.2F, 8, 22.0F, 9, 0.12F, 1050, FireMode.ACID_SHOTGUN, Rarity.EPIC);

	public static final Item GUN_SCRAP_SMG = gun("gun_scrap_smg", 2, List.of("ammo_9mm"), 6.5F, 1, 26.0F, 4, 0.075F, 540, FireMode.STANDARD, Rarity.UNCOMMON);
	public static final Item GUN_TACTICAL_SMG = gun("gun_tactical_smg", 2, List.of("ammo_9mm"), 7.5F, 1, 32.0F, 4, 0.035F, 760, FireMode.SILENT_NAIL, Rarity.RARE);
	public static final Item GUN_ASSAULT_RIFLE = gun("gun_assault_rifle", 3, List.of("ammo_556_phosphor", "ammo_556"), 11.5F, 1, 48.0F, 5, 0.025F, 980, FireMode.STANDARD, Rarity.RARE);
	public static final Item GUN_BOLT_SNIPER = gun("gun_bolt_sniper", 3, List.of("ammo_308", "ammo_308_ap"), 24.0F, 1, 80.0F, 26, 0.004F, 860, FireMode.STANDARD, Rarity.RARE);
	public static final Item GUN_ANTIMATERIEL_RIFLE = gun("gun_antimateriel_rifle", 4, List.of("ammo_308_ap", "ammo_308"), 34.0F, 1, 96.0F, 30, 0.002F, 1450, FireMode.PIERCING_AP, Rarity.EPIC);

	public static final Item GUN_RAILGUN = gun("gun_railgun", 4, List.of("ammo_rail_slug"), 38.0F, 1, 90.0F, 28, 0.002F, 1600, FireMode.RAILGUN_ARC, Rarity.EPIC);
	public static final Item GUN_SONIC_PULSE = gun("gun_sonic_pulse", 3, List.of("almond_mana_cell"), 15.0F, 1, 16.0F, 20, 0.01F, 1100, FireMode.SONIC_PULSE, Rarity.EPIC);
	public static final Item OFFICE_CROSSBOW = gun("office_crossbow", 1, List.of("ammo_pen_bolt"), 9.5F, 1, 32.0F, 18, 0.02F, 260, FireMode.SILENT_NAIL, Rarity.COMMON);

	// 12 Ammo Types
	public static final Item AMMO_PEN_BOLT = ammo("ammo_pen_bolt", Rarity.COMMON);
	public static final Item AMMO_SCRAP_SLUG = ammo("ammo_scrap_slug", Rarity.COMMON);
	public static final Item AMMO_STEEL_NAILS = ammo("ammo_steel_nails", Rarity.COMMON);
	public static final Item AMMO_9MM = ammo("ammo_9mm", Rarity.COMMON);
	public static final Item AMMO_12G_BUCK = ammo("ammo_12g_buck", Rarity.COMMON);
	public static final Item AMMO_12G_ACID = ammo("ammo_12g_acid", Rarity.RARE);
	public static final Item AMMO_556 = ammo("ammo_556", Rarity.UNCOMMON);
	public static final Item AMMO_556_PHOSPHOR = ammo("ammo_556_phosphor", Rarity.RARE);
	public static final Item AMMO_308 = ammo("ammo_308", Rarity.UNCOMMON);
	public static final Item AMMO_308_AP = ammo("ammo_308_ap", Rarity.RARE);
	public static final Item AMMO_FUEL_CANISTER = ammo("ammo_fuel_canister", Rarity.UNCOMMON);
	public static final Item AMMO_RAIL_SLUG = ammo("ammo_rail_slug", Rarity.EPIC);

	// 6 Tactical Mods
	public static final Item MOD_SILENCER = ammo("mod_silencer", Rarity.RARE);
	public static final Item MOD_RED_DOT = ammo("mod_red_dot", Rarity.UNCOMMON);
	public static final Item MOD_SNIPER_SCOPE = ammo("mod_sniper_scope", Rarity.RARE);
	public static final Item MOD_WEAPON_LIGHT = ammo("mod_weapon_light", Rarity.UNCOMMON);
	public static final Item MOD_EXT_MAG = ammo("mod_ext_mag", Rarity.UNCOMMON);
	public static final Item MOD_BAYONET = ammo("mod_bayonet", Rarity.UNCOMMON);

	// =========================================================================
	// 7. MAGIC & ANOMALY WEAPONS (06_weapons_magic_and_chests.md + GDD 6.2B: 20 items)
	// =========================================================================
	public static final Item STATIC_PULSE_STAFF = magic("static_pulse_staff", 1, Spell.STATIC_PULSE, "", 24, 4.0F, 0.0F, 340, 1, Rarity.UNCOMMON);
	public static final Item MAGIC_PHOSPHOR_TALISMAN = magic("magic_phosphor_talisman", 1, Spell.PHOSPHOR_TALISMAN, "SELF", 16, 0.0F, 0.0F, 0, 16, Rarity.UNCOMMON);
	public static final Item MAGIC_ALMOND_CENSER = magic("magic_almond_censer", 2, Spell.ALMOND_CENSER, "almond_water", 60, 0.0F, 0.0F, 400, 1, Rarity.UNCOMMON);
	public static final Item MAGIC_SCREECH_BELL = magic("magic_screech_bell", 2, Spell.SCREECH_BELL, "", 160, 0.0F, 0.0F, 420, 1, Rarity.RARE);
	public static final Item MAGIC_DECOY_MIRROR = magic("magic_decoy_mirror", 2, Spell.DECOY_MIRROR, "almond_mana_cell", 120, 0.0F, 0.0F, 480, 1, Rarity.RARE);
	public static final Item MAGIC_PRISM_LIGHT_STAFF = magic("magic_prism_light_staff", 2, Spell.PRISM_LIGHT, "almond_mana_cell", 30, 0.0F, 0.0F, 680, 1, Rarity.RARE);
	public static final Item MAGIC_STEAM_FLAME_STAFF = magic("magic_steam_flame_staff", 3, Spell.STEAM_FLAME, "almond_mana_cell", 35, 0.0F, 0.0F, 920, 1, Rarity.EPIC);
	public static final Item MAGIC_ACID_GRIMOIRE = magic("magic_acid_grimoire", 3, Spell.ACID_GRIMOIRE, "almond_mana_cell", 45, 0.0F, 0.0F, 980, 1, Rarity.EPIC);
	public static final Item MAGIC_ARC_LIGHTNING_STAFF = magic("magic_arc_lightning_staff", 3, Spell.ARC_LIGHTNING, "almond_mana_cell", 35, 0.0F, 0.0F, 1020, 1, Rarity.EPIC);
	public static final Item MAGIC_ABYSSAL_FROST_SCEPTER = magic("magic_abyssal_frost_scepter", 3, Spell.ABYSSAL_FROST, "almond_mana_cell", 40, 0.0F, 0.0F, 1020, 1, Rarity.EPIC);
	public static final Item MAGIC_PLAGUE_CENSER_WAND = magic("magic_plague_censer_wand", 3, Spell.PLAGUE_CENSER, "almond_mana_cell", 40, 0.0F, 0.0F, 860, 1, Rarity.RARE);
	public static final Item MAGIC_NOCLIP_WAND = magic("magic_noclip_wand", 3, Spell.NOCLIP_WAND, "almond_mana_cell", 240, 6.0F, 0.0F, 720, 1, Rarity.EPIC);
	public static final Item MAGIC_CLUMP_GRAPPLE = magic("magic_clump_grapple", 2, Spell.CLUMP_GRAPPLE, "", 100, 0.0F, 0.0F, 520, 1, Rarity.RARE);
	public static final Item MAGIC_PARTY_GRIMOIRE = magic("magic_party_grimoire", 3, Spell.PARTY_GRIMOIRE, "almond_mana_cell", 100, 5.0F, 0.0F, 820, 1, Rarity.RARE);
	public static final Item MAGIC_STOPMOTION_WATCH = magic("magic_stopmotion_watch", 3, Spell.STOPMOTION_WATCH, "almond_mana_cell", 500, 4.0F, 0.0F, 920, 1, Rarity.EPIC);
	public static final Item MAGIC_CRIMSON_SCYTHE = magic("magic_crimson_scythe", 4, Spell.CRIMSON_SCYTHE, "almond_mana_cell", 40, 8.0F, 18.0F, 2200, 1, Rarity.EPIC);
	public static final Item MAGIC_BEAST_SUMMON_BELL = magic("magic_beast_summon_bell", 4, Spell.BEAST_SUMMON_BELL, "almond_mana_cell", 800, 6.0F, 0.0F, 1200, 1, Rarity.EPIC);
	public static final Item MAGIC_FATE_DICE_STAFF = magic("magic_fate_dice_staff", 4, Spell.FATE_DICE, "almond_mana_cell", 45, 3.0F, 0.0F, 1400, 1, Rarity.EPIC);
	public static final Item MAGIC_VOID_RIFT_STAFF = magic("magic_void_rift_staff", 4, Spell.VOID_RIFT, "almond_mana_cell", 60, 10.0F, 0.0F, 2000, 1, Rarity.EPIC);
	public static final Item MAGIC_ASTRAL_CODEX = magic("magic_astral_codex", 4, Spell.ASTRAL_CODEX, "almond_mana_cell", 50, 0.0F, 0.0F, 2200, 1, Rarity.EPIC);

	// 4 Dedicated Backrooms Creative Mode Tabs (automatically indexed by JEI!)
	public static final CreativeModeTab CREATIVE_TAB_MATERIALS = registerTab("materials_and_parts",
			ANOMALY_CRYSTAL, TAB_MATERIALS);
	public static final CreativeModeTab CREATIVE_TAB_GEAR = registerTab("tools_props_and_gear",
			WORKSHOP_ICON(), TAB_GEAR);
	public static final CreativeModeTab CREATIVE_TAB_MELEE = registerTab("melee_weapons",
			CN_TANG_HENGDAO, TAB_MELEE);
	public static final CreativeModeTab CREATIVE_TAB_RANGED_MAGIC = registerTab("firearms_and_magic",
			MAGIC_NOCLIP_WAND, TAB_RANGED_MAGIC);

	private BackroomsItems() {
	}

	private static Item WORKSHOP_ICON() {
		return IMPROVISED_CROWBAR;
	}

	public static void register() {
		// Armor set passive bonuses (Gas Mask, Boiler Thermal, Abyssal Void)
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 40 != 0) return;
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
				ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
				ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
				ItemStack feet = player.getItemBySlot(EquipmentSlot.FEET);

				if (feet.is(SILENT_CARPET_BOOTS)) {
					player.addEffect(new MobEffectInstance(MobEffects.SPEED, 80, 0, true, false));
				}
				if (head.is(ALMOND_GAS_MASK)) {
					player.removeEffect(MobEffects.POISON);
					player.removeEffect(MobEffects.NAUSEA);
				}
				if (head.is(BOILER_THERMAL_HELMET) && chest.is(BOILER_THERMAL_CHESTPLATE)
						&& legs.is(BOILER_THERMAL_LEGGINGS) && feet.is(BOILER_THERMAL_BOOTS)) {
					player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 80, 0, true, false));
				}
				if (head.is(ABYSSAL_VOID_HELMET) && chest.is(ABYSSAL_VOID_CHESTPLATE)
						&& legs.is(ABYSSAL_VOID_LEGGINGS) && feet.is(ABYSSAL_VOID_BOOTS)) {
					player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 80, 0, true, false));
					player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false));
				}
			}
		});
	}

	public static Map<String, Item> allItems() {
		return Collections.unmodifiableMap(ALL);
	}

	private static CreativeModeTab registerTab(String id, Item icon, List<Item> items) {
		ResourceKey<CreativeModeTab> key = ResourceKey.create(Registries.CREATIVE_MODE_TAB, BackroomsMod.id(id));
		CreativeModeTab tab = FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup.noclip_backrooms." + id))
				.icon(() -> new ItemStack(icon))
				.displayItems((params, output) -> {
					if ("tools_props_and_gear".equals(id)) {
						for (Item blockItem : BackroomsProps.allItems().values()) {
							output.accept(blockItem);
						}
					}
					for (Item item : items) {
						output.accept(item);
					}
				})
				.build();
		return Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, key, tab);
	}

	private static Item mat(String id, Rarity rarity) {
		Item item = register(id, new Item.Properties().rarity(rarity), Item::new);
		TAB_MATERIALS.add(item);
		return item;
	}

	private static Item gear(String id, Item.Properties props, Function<Item.Properties, Item> factory) {
		Item item = register(id, props, factory);
		TAB_GEAR.add(item);
		return item;
	}

	private static Item tool(String id, ToolMaterial material, int tier, int durability, float atkDmg, float atkSpd, Rarity rarity, String loreKey) {
		Item.Properties props = new Item.Properties()
				.pickaxe(material, atkDmg, atkSpd)
				.durability(durability)
				.rarity(rarity);
		Item item = register(id, props, p -> new BackroomsToolItem(p, tier, loreKey));
		TAB_GEAR.add(item);
		return item;
	}

	private static Item armor(String id, ArmorMaterial material, ArmorType type, Rarity rarity) {
		Item.Properties props = new Item.Properties()
				.humanoidArmor(material, type)
				.rarity(rarity);
		Item item = register(id, props, Item::new);
		TAB_GEAR.add(item);
		return item;
	}

	private static Item melee(String id, Style style, int tier, float dmg, float spd, float reach, int durability, Trait trait, Rarity rarity) {
		Item.Properties props = new Item.Properties().stacksTo(1).durability(durability).rarity(rarity);
		Item item = register(id, props, p -> new BackroomsMeleeItem(p, style, tier, dmg, spd, reach, trait, "lore.noclip_backrooms." + id));
		TAB_MELEE.add(item);
		return item;
	}

	private static Item gun(String id, int tier, List<String> ammos, float dmg, int pellets, float range, int cooldown, float spread, int durability, FireMode mode, Rarity rarity) {
		Item.Properties props = new Item.Properties().stacksTo(1).durability(durability).rarity(rarity);
		Item item = register(id, props, p -> new BackroomsFirearmItem(p, tier, ammos, dmg, pellets, range, cooldown, spread, mode, "lore.noclip_backrooms." + id));
		TAB_RANGED_MAGIC.add(item);
		return item;
	}

	private static Item ammo(String id, Rarity rarity) {
		Item item = register(id, new Item.Properties().rarity(rarity), Item::new);
		TAB_RANGED_MAGIC.add(item);
		return item;
	}

	private static Item magic(String id, int tier, Spell spell, String costItemId, int cooldown, float sanityCost, float meleeDmg, int durability, int stackSize, Rarity rarity) {
		Item.Properties props = new Item.Properties().stacksTo(stackSize).rarity(rarity);
		if (durability > 0) props = props.durability(durability);
		Item item = register(id, props, p -> new BackroomsMagicItem(p, tier, spell, costItemId, cooldown, sanityCost, meleeDmg, "lore.noclip_backrooms." + id));
		TAB_RANGED_MAGIC.add(item);
		return item;
	}

	private static Item register(String name, Item.Properties properties, Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BackroomsMod.id(name));
		Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
		ALL.put(name, item);
		return item;
	}
}
