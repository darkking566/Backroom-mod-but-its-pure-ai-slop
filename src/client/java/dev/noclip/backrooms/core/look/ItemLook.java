package dev.noclip.backrooms.core.look;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.noclip.backrooms.BackroomsMod;
import java.io.IOException;
import java.io.Reader;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * "Backrooms: realistic items", a built-in resource pack, on by default: items, weapons, gear and props redrawn in the
 * levels' style (worn, dull, box models with 64-pixel textures from CC0 photos; tools/make_realistic_items.py). Turn it
 * off in the resource pack screen to see the earlier models.
 *
 * <p>Weapons and props are otherwise drawn from Blender meshes by their own renderers; for whatever the pack lists in
 * {@code item_look.json} those renderers step aside and the pack's ordinary models are drawn ({@link #replaces}). With
 * the pack off the list is not there, so nothing is replaced.
 */
public final class ItemLook {
	public static final Identifier PACK = BackroomsMod.id("realistic_items");
	private static final Logger LOGGER = LoggerFactory.getLogger("noclip_backrooms/item_look");
	private static final Identifier LIST = BackroomsMod.id("item_look.json");
	private static volatile Set<String> covered = Set.of();

	private ItemLook() {
	}

	public static void register() {
		ResourceLoader.registerBuiltinPack(PACK, FabricLoader.getInstance().getModContainer(BackroomsMod.MOD_ID).orElseThrow(),
				Component.translatable("pack.noclip_backrooms.realistic_items"), PackActivationType.DEFAULT_ENABLED);
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(BackroomsMod.id("item_look"),
				(ResourceManagerReloadListener) ItemLook::reload);
	}

	private static void reload(ResourceManager manager) {
		Set<String> ids = new HashSet<>();
		manager.getResource(LIST).ifPresent(resource -> {
			try (Reader in = resource.openAsReader()) {
				for (JsonElement id : JsonParser.parseReader(in).getAsJsonObject().getAsJsonArray("replaces")) ids.add(id.getAsString());
			} catch (IOException | RuntimeException e) {
				LOGGER.warn("Could not read {}", LIST, e);
			}
		});
		covered = Set.copyOf(ids);
	}

	/** What the pack redraws now (empty when it is off). */
	public static Set<String> covered() {
		return covered;
	}

	/** Whether the pack is loaded now (it lists at least one thing). */
	public static boolean on() {
		return !covered.isEmpty();
	}

	/** Whether this item is drawn from the pack's model now (rather than a Blender mesh). */
	public static boolean replaces(ItemStack stack) {
		if (stack.isEmpty() || covered.isEmpty()) return false;
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return BackroomsMod.MOD_ID.equals(id.getNamespace()) && covered.contains(id.getPath());
	}

	/** Whether this prop block is drawn from the pack's model now. */
	public static boolean replaces(String blockPath) {
		return blockPath != null && covered.contains(blockPath);
	}
}
