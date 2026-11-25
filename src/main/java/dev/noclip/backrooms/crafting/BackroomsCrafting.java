package dev.noclip.backrooms.crafting;

import dev.noclip.backrooms.BackroomsMod;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

/**
 * Registers all 6 physical-grid workstation MenuTypes, C2S recipe auto-fill / clear-grid handler
 * (powering both the Categorized Recipe Book and JEI '+' transfer), and loads shaped station recipes.
 */
public final class BackroomsCrafting {
	public static final Identifier CLEAR_GRID_ID = BackroomsMod.id("clear_grid");
	public static final Map<StationType, MenuType<BackroomsStationMenu>> MENUS = new EnumMap<>(StationType.class);

	static {
		for (StationType station : StationType.values()) {
			MenuType<BackroomsStationMenu> type = Registry.register(
					BuiltInRegistries.MENU,
					BackroomsMod.id("menu_" + station.id()),
					new MenuType<>((id, inv) -> new BackroomsStationMenu(MENUS.get(station), id, inv, station), FeatureFlags.VANILLA_SET)
			);
			MENUS.put(station, type);
		}
	}

	private BackroomsCrafting() {
	}

	public static MenuType<BackroomsStationMenu> menuType(StationType station) {
		return MENUS.get(station);
	}

	public static void register() {
		BackroomsRecipeRegistry.ensureLoaded();
		PayloadTypeRegistry.serverboundPlay().register(CraftStationPayload.TYPE, CraftStationPayload.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(CraftStationPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (!(player.containerMenu instanceof BackroomsStationMenu menu)) return;
			if (CLEAR_GRID_ID.equals(payload.recipeId())) {
				menu.clearGridToPlayer(player);
				return;
			}
			StationRecipe recipe = BackroomsRecipeRegistry.byId(payload.recipeId());
			if (recipe == null || recipe.station() != menu.stationType()) return;
			menu.fillRecipeFromPlayer(player, recipe, payload.craftBatch());
		});
	}
}
