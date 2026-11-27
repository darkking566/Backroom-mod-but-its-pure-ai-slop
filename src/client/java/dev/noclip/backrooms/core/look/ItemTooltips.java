package dev.noclip.backrooms.core.look;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.items.BackroomsConsumableItem;
import dev.noclip.backrooms.level0.FlashlightItem;
import dev.noclip.backrooms.tools.BackroomsToolItem;
import dev.noclip.backrooms.weapons.BackroomsFirearmItem;
import dev.noclip.backrooms.weapons.BackroomsMagicItem;
import dev.noclip.backrooms.weapons.BackroomsMeleeItem;
import java.util.List;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Client-side item descriptions for Backrooms tools, consumables, field kit items, armor, workstations,
 * loot chests, props, and crafting materials that do not already render a {@code lore.noclip_backrooms.*} tooltip.
 */
public final class ItemTooltips {
	private ItemTooltips() {
	}

	public static void register() {
		ItemTooltipCallback.EVENT.register(ItemTooltips::append);
	}

	private static void append(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> lines) {
		Item item = stack.getItem();
		if (item instanceof BackroomsConsumableItem
				|| item instanceof BackroomsToolItem
				|| item instanceof BackroomsMeleeItem
				|| item instanceof BackroomsFirearmItem
				|| item instanceof BackroomsMagicItem) {
			return;
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(item);
		if (!BackroomsMod.MOD_ID.equals(id.getNamespace())) return;

		Language lang = Language.getInstance();
		String path = id.getPath();

		// Segmented 5-bar / infinite battery line for Backrooms flashlights (replaces vanilla durability bar)
		if (FlashlightItem.isFlashlight(stack)) {
			if (FlashlightItem.isInfinite(stack)) {
				lines.add(Component.translatable("tooltip.noclip_backrooms.ambient_flashlight.battery")
						.withStyle(ChatFormatting.AQUA));
			} else {
				int bars = FlashlightItem.bars(stack);
				int pct = Math.round(FlashlightItem.batteryRatio(stack) * 100.0F);
				ChatFormatting barColor = bars >= 3 ? ChatFormatting.GREEN : (bars == 2 ? ChatFormatting.YELLOW : ChatFormatting.RED);
				lines.add(Component.translatable("tooltip.noclip_backrooms.flashlight.battery",
						FlashlightItem.barGlyphs(stack), pct).withStyle(barColor));
			}
		}

		String exactKey = "tooltip.noclip_backrooms." + path;
		if (lang.has(exactKey)) {
			addFormattedLines(lines, lang.getOrDefault(exactKey));
			return;
		}

		String fallbackKey = categoryTooltipKey(path, item instanceof BlockItem);
		if (fallbackKey != null && lang.has(fallbackKey)) {
			addFormattedLines(lines, lang.getOrDefault(fallbackKey));
		}
	}

	private static void addFormattedLines(List<Component> lines, String translated) {
		for (String part : translated.split("\n")) {
			if (part.isEmpty()) continue;
			ChatFormatting color = part.startsWith("▶") ? ChatFormatting.DARK_AQUA : ChatFormatting.GRAY;
			lines.add(Component.literal(part).withStyle(color));
		}
	}

	private static String categoryTooltipKey(String path, boolean isBlock) {
		if (path.endsWith("_spawn_egg")) return "tooltip.noclip_backrooms.cat.spawn_egg";
		if (path.startsWith("ammo_")) return "tooltip.noclip_backrooms.cat.ammo";
		if (path.startsWith("core_")) return "tooltip.noclip_backrooms.cat.boss_core";
		if (path.startsWith("gun_")) return "tooltip.noclip_backrooms.cat.gun_part";
		if (path.startsWith("mod_")) return "tooltip.noclip_backrooms.cat.weapon_mod";
		if (path.startsWith("module_")) return "tooltip.noclip_backrooms.cat.machine_module";
		if (path.startsWith("prop_") || path.startsWith("noclipped_")) return "tooltip.noclip_backrooms.cat.prop";
		if (path.startsWith("scrap_tactical_")) return "tooltip.noclip_backrooms.cat.armor_scrap_tactical";
		if (path.startsWith("skin_stealer_")) return "tooltip.noclip_backrooms.cat.armor_skin_stealer";
		if (path.startsWith("boiler_thermal_")) return "tooltip.noclip_backrooms.cat.armor_boiler_thermal";
		if (path.startsWith("abyssal_void_")) return "tooltip.noclip_backrooms.cat.armor_abyssal_void";
		if (isBlock) return null;
		if (isMonsterDrop(path)) return "tooltip.noclip_backrooms.cat.monster_drop";
		return "tooltip.noclip_backrooms.cat.material";
	}

	private static boolean isMonsterDrop(String p) {
		return p.startsWith("hound_") || p.startsWith("smiler_") || p.startsWith("moth_")
				|| p.equals("faceless_membrane") || p.equals("thick_disguise_hide")
				|| p.equals("yellow_party_hide") || p.equals("withered_heart")
				|| p.equals("corrupted_marrow") || p.equals("grasping_tendon")
				|| p.equals("host_nerve_cord") || p.equals("lure_cornea")
				|| p.equals("morph_gland") || p.equals("plague_ichor")
				|| p.equals("shadow_mote") || p.equals("spectral_cloth")
				|| p.equals("spiral_chitin") || p.equals("tangled_knuckle")
				|| p.equals("toxic_spore_sac") || p.equals("translucent_blood_vial")
				|| p.equals("acid_sac") || p.equals("long_limb_bone") || p.equals("bone_fragment");
	}
}
