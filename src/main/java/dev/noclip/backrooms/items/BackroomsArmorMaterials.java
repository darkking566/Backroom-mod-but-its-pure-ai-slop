package dev.noclip.backrooms.items;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * Armor materials for the 4 Backrooms armor sets + Gas Mask.
 * Reuses vanilla EquipmentAssets so worn armor layers render cleanly with zero missing textures.
 */
public final class BackroomsArmorMaterials {
	public static final ArmorMaterial IMPROVISED_OFFICE = new ArmorMaterial(
			14,
			defenseMap(1, 3, 2, 2),
			12,
			SoundEvents.ARMOR_EQUIP_LEATHER,
			0.0F,
			0.0F,
			ItemTags.REPAIRS_LEATHER_ARMOR,
			EquipmentAssets.LEATHER
	);

	public static final ArmorMaterial GAS_MASK = new ArmorMaterial(
			18,
			defenseMap(2, 4, 3, 2),
			10,
			SoundEvents.ARMOR_EQUIP_LEATHER,
			0.5F,
			0.0F,
			ItemTags.REPAIRS_IRON_ARMOR,
			EquipmentAssets.TURTLE_SCUTE
	);

	public static final ArmorMaterial SCRAP_TACTICAL = new ArmorMaterial(
			20,
			defenseMap(3, 6, 5, 2),
			12,
			SoundEvents.ARMOR_EQUIP_IRON,
			1.0F,
			0.05F,
			ItemTags.REPAIRS_IRON_ARMOR,
			EquipmentAssets.CHAINMAIL
	);

	public static final ArmorMaterial SKIN_STEALER = new ArmorMaterial(
			22,
			defenseMap(3, 6, 5, 3),
			15,
			SoundEvents.ARMOR_EQUIP_LEATHER,
			1.0F,
			0.0F,
			ItemTags.REPAIRS_LEATHER_ARMOR,
			EquipmentAssets.LEATHER
	);

	public static final ArmorMaterial BOILER_THERMAL = new ArmorMaterial(
			30,
			defenseMap(3, 8, 6, 3),
			14,
			SoundEvents.ARMOR_EQUIP_IRON,
			2.0F,
			0.1F,
			ItemTags.REPAIRS_IRON_ARMOR,
			EquipmentAssets.IRON
	);

	public static final ArmorMaterial ABYSSAL_VOID = new ArmorMaterial(
			42,
			defenseMap(4, 9, 7, 4),
			20,
			SoundEvents.ARMOR_EQUIP_NETHERITE,
			3.5F,
			0.15F,
			ItemTags.REPAIRS_NETHERITE_ARMOR,
			EquipmentAssets.NETHERITE
	);

	private BackroomsArmorMaterials() {
	}

	private static Map<ArmorType, Integer> defenseMap(int helm, int chest, int legs, int boots) {
		Map<ArmorType, Integer> map = new EnumMap<>(ArmorType.class);
		map.put(ArmorType.HELMET, helm);
		map.put(ArmorType.CHESTPLATE, chest);
		map.put(ArmorType.LEGGINGS, legs);
		map.put(ArmorType.BOOTS, boots);
		map.put(ArmorType.BODY, chest);
		return map;
	}
}
