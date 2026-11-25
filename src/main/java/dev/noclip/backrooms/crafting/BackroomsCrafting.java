package dev.noclip.backrooms.crafting;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.rpg.PlayerRpg;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

/** Registers workstation MenuTypes, C2S crafting payload handler, and loads station recipes. */
public final class BackroomsCrafting {
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
			StationRecipe recipe = BackroomsRecipeRegistry.byId(payload.recipeId());
			if (recipe == null || recipe.station() != menu.stationType()) return;

			int maxCrafts = payload.craftBatch() ? 8 : 1;
			int crafted = 0;
			for (int i = 0; i < maxCrafts; i++) {
				if (!recipe.craftOnce(player)) break;
				crafted++;
				if (player.isCreative()) break;
			}
			if (crafted > 0) {
				player.containerMenu.broadcastChanges();
				if (recipe.xpReward() > 0) {
					PlayerRpg.grantXp(player, Math.min(60, recipe.xpReward() * crafted));
				}
				SoundEvent sound = switch (recipe.station()) {
					case SALVAGE -> SoundEvents.GRINDSTONE_USE;
					case WORKSHOP -> SoundEvents.SMITHING_TABLE_USE;
					case DISTILLER -> SoundEvents.BREWING_STAND_BREW;
					case ALTAR -> SoundEvents.ENCHANTMENT_TABLE_USE;
					case SPIDERWEB -> SoundEvents.BEACON_ACTIVATE;
				};
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
						sound, SoundSource.BLOCKS, 0.75F, 1.0F + player.getRandom().nextFloat() * 0.15F);
			}
		});
	}
}
