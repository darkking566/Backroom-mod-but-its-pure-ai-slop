package dev.noclip.backrooms.core.look;

import dev.noclip.backrooms.BackroomsMod;
import java.util.Set;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * "Backrooms: Claude's trial models", a built-in resource pack, off by default: eight items and props redrawn in the
 * levels' style (worn, dull, box models with 64-pixel textures from CC0 photos; tools/make_trial_items.py) to set side by
 * side with the current ones. Weapons and props are normally drawn from Blender meshes by their own renderers; while
 * this pack is on, those renderers leave the trial ones to their ordinary models (see {@link #replaces}).
 */
public final class TrialPack {
	public static final Identifier PACK = BackroomsMod.id("claude_trial");
	/** What the pack redraws (item and block ids). */
	public static final Set<String> IDS = Set.of("blunt_rusty_pipe", "improvised_crowbar", "magic_noclip_wand", "gun_pipe_pistol",
			"copper_wire", "almond_elixir", "prop_office_chair", "prop_desktop_computer");

	private TrialPack() {
	}

	public static void register() {
		ResourceLoader.registerBuiltinPack(PACK, FabricLoader.getInstance().getModContainer(BackroomsMod.MOD_ID).orElseThrow(),
				Component.translatable("pack.noclip_backrooms.claude_trial"), PackActivationType.NORMAL);
	}

	/** Whether the pack is switched on. */
	public static boolean on() {
		Minecraft client = Minecraft.getInstance();
		for (String id : client.getResourcePackRepository().getSelectedIds()) {
			if (id.endsWith(PACK.getPath())) return true;
		}
		return false;
	}

	/** Whether this item is drawn from the trial pack's model now (rather than a Blender mesh). */
	public static boolean replaces(ItemStack stack) {
		if (stack.isEmpty()) return false;
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return BackroomsMod.MOD_ID.equals(id.getNamespace()) && IDS.contains(id.getPath()) && on();
	}

	/** Whether this prop block is drawn from the trial pack's model now. */
	public static boolean replaces(String blockPath) {
		return blockPath != null && IDS.contains(blockPath) && on();
	}
}
