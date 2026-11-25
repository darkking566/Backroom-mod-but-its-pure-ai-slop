package dev.noclip.backrooms.crafting.jei;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.crafting.BackroomsCrafting;
import dev.noclip.backrooms.crafting.BackroomsRecipeRegistry;
import dev.noclip.backrooms.crafting.BackroomsStationMenu;
import dev.noclip.backrooms.crafting.BackroomsStationScreen;
import dev.noclip.backrooms.crafting.CraftStationPayload;
import dev.noclip.backrooms.crafting.StationRecipe;
import dev.noclip.backrooms.crafting.StationType;
import dev.noclip.backrooms.items.BackroomsItems;
import dev.noclip.backrooms.mixin.client.AbstractContainerScreenAccess;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.ItemLike;

/**
 * JEI Mod Plugin for Noclip: Backrooms.
 * Registers all 6 shaped workstation categories (1->4 Salvage, 3x3 Distiller/Altar, 5x5 Workshop,
 * 7x7 Primary & 9x9 Ultimate Spiderweb), full '+' recipe transfer into physical grids, Boss Core &
 * Monster Drop info, and GUI exclusion bounds for the RPG panel & Left Categorized Recipe Book.
 */
@JeiPlugin
public final class BackroomsJeiPlugin implements IModPlugin {
	private static final Identifier PLUGIN_UID = BackroomsMod.id("jei_plugin");
	private static final Map<StationType, IRecipeType<StationRecipe>> RECIPE_TYPES = new EnumMap<>(StationType.class);

	static {
		for (StationType type : StationType.values()) {
			RECIPE_TYPES.put(type, IRecipeType.create(type.categoryId(), StationRecipe.class));
		}
	}

	@Override
	public Identifier getPluginUid() {
		return PLUGIN_UID;
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
		for (StationType type : StationType.values()) {
			registration.addRecipeCategories(new StationRecipeCategory(type, RECIPE_TYPES.get(type), guiHelper));
		}
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		BackroomsRecipeRegistry.ensureLoaded();
		for (StationType type : StationType.values()) {
			List<StationRecipe> list = BackroomsRecipeRegistry.byStation(type);
			registration.addRecipes(RECIPE_TYPES.get(type), list);
		}

		// Boss Core acquisition notes inside JEI
		addInfo(registration, BackroomsItems.CORE_CRIMSON_HEART, "jei.noclip_backrooms.info.core_crimson_heart");
		addInfo(registration, BackroomsItems.CORE_ALPHA_FANG, "jei.noclip_backrooms.info.core_alpha_fang");
		addInfo(registration, BackroomsItems.CORE_STEAM_TURBINE, "jei.noclip_backrooms.info.core_steam_turbine");
		addInfo(registration, BackroomsItems.CORE_ARC_REACTOR, "jei.noclip_backrooms.info.core_arc_reactor");
		addInfo(registration, BackroomsItems.CORE_ACID_QUEEN, "jei.noclip_backrooms.info.core_acid_queen");
		addInfo(registration, BackroomsItems.CORE_MELANCHOLY_BELL, "jei.noclip_backrooms.info.core_melancholy_bell");
		addInfo(registration, BackroomsItems.CORE_ABYSSAL_SCALE, "jei.noclip_backrooms.info.core_abyssal_scale");
		addInfo(registration, BackroomsItems.CORE_STOPMOTION_GEAR, "jei.noclip_backrooms.info.core_stopmotion_gear");
		addInfo(registration, BackroomsItems.CORE_FATE_DIE, "jei.noclip_backrooms.info.core_fate_die");
		addInfo(registration, BackroomsItems.CORE_VOID_WEAVER, "jei.noclip_backrooms.info.core_void_weaver");

		// Key Monster / NPC drop notes inside JEI
		addInfo(registration, BackroomsItems.HOUND_FANG, "jei.noclip_backrooms.info.hound_drops");
		addInfo(registration, BackroomsItems.SMILER_REMNANT, "jei.noclip_backrooms.info.smiler_drops");
		addInfo(registration, BackroomsItems.THICK_DISGUISE_HIDE, "jei.noclip_backrooms.info.skin_stealer_drops");
		addInfo(registration, BackroomsItems.ACID_SAC, "jei.noclip_backrooms.info.deathmoth_drops");
		addInfo(registration, BackroomsItems.TANGLED_KNUCKLE, "jei.noclip_backrooms.info.clump_drops");
		addInfo(registration, BackroomsItems.SILENCE_CORE, "jei.noclip_backrooms.info.duller_drops");
		addInfo(registration, BackroomsItems.BLACK_FILAMENT, "jei.noclip_backrooms.info.bacteria_drops");
		addInfo(registration, BackroomsItems.YELLOW_PARTY_HIDE, "jei.noclip_backrooms.info.partygoer_drops");
		addInfo(registration, BackroomsItems.PASTEL_CHARM, "jei.noclip_backrooms.info.friendly_npc_drops");
		addInfo(registration, BackroomsItems.ASTRAL_PARCHMENT, "jei.noclip_backrooms.info.friendly_npc_drops");

		// GDD special items acquisition & function notes inside JEI
		addInfo(registration, BackroomsItems.NOCLIP_SHARD, "jei.noclip_backrooms.info.noclip_shard");
		addInfo(registration, BackroomsItems.FIRE_SALT, "jei.noclip_backrooms.info.fire_salt");
		addInfo(registration, BackroomsItems.RUSTED_OFFICE_SAFE, "jei.noclip_backrooms.info.geodes");
		addInfo(registration, BackroomsItems.ENCRYPTED_HARD_DRIVE, "jei.noclip_backrooms.info.geodes");
		addInfo(registration, BackroomsItems.HOMEWARD_ANCHOR, "jei.noclip_backrooms.info.homeward_anchor");
		addInfo(registration, BackroomsItems.FORBIDDEN_SCROLL_LUCK, "jei.noclip_backrooms.info.forbidden_scrolls");
		addInfo(registration, BackroomsItems.FORBIDDEN_SCROLL_PSY, "jei.noclip_backrooms.info.forbidden_scrolls");
		addInfo(registration, BackroomsItems.FORBIDDEN_SCROLL_TECH, "jei.noclip_backrooms.info.forbidden_scrolls");
	}

	private static void addInfo(IRecipeRegistration registration, ItemLike item, String langKey) {
		registration.addIngredientInfo(item, Component.translatable(langKey));
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		for (StationType type : StationType.values()) {
			registration.addCraftingStation(RECIPE_TYPES.get(type), type.stationItem());
		}
	}

	@Override
	public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
		IRecipeTransferHandlerHelper helper = registration.getTransferHelper();
		for (StationType type : StationType.values()) {
			IRecipeType<StationRecipe> recipeType = RECIPE_TYPES.get(type);
			MenuType<BackroomsStationMenu> menuType = BackroomsCrafting.menuType(type);
			registration.addRecipeTransferHandler(new IRecipeTransferHandler<BackroomsStationMenu, StationRecipe>() {
				@Override
				public Class<? extends BackroomsStationMenu> getContainerClass() {
					return BackroomsStationMenu.class;
				}

				@Override
				public Optional<MenuType<BackroomsStationMenu>> getMenuType() {
					return Optional.of(menuType);
				}

				@Override
				public IRecipeType<StationRecipe> getRecipeType() {
					return recipeType;
				}

				@Override
				public IRecipeTransferError transferRecipe(BackroomsStationMenu container, StationRecipe recipe,
						IRecipeSlotsView recipeSlots, Player player, boolean maxTransfer, boolean doTransfer) {
					if (container.stationType() != type || recipe.station() != type) {
						return helper.createInternalError();
					}
					if (!recipe.canCraftFromInventory(player)) {
						return helper.createUserErrorWithTooltip(Component.translatable("ui.noclip_backrooms.station.missing_materials"));
					}
					if (doTransfer) {
						ClientPlayNetworking.send(new CraftStationPayload(recipe.id(), maxTransfer));
					}
					return null;
				}
			}, recipeType);
		}
	}

	@Override
	public void registerGuiHandlers(IGuiHandlerRegistration registration) {
		registration.addGuiContainerHandler(InventoryScreen.class, new IGuiContainerHandler<>() {
			@Override
			public List<Rect2i> getGuiExtraAreas(InventoryScreen screen) {
				AbstractContainerScreenAccess access = (AbstractContainerScreenAccess) screen;
				int left = access.noclip_backrooms$leftPos();
				int width = access.noclip_backrooms$imageWidth();
				int top = access.noclip_backrooms$topPos();
				int panelW = 132;
				int panelH = 166;
				int px = (left >= panelW + 6)
						? (left - panelW - 4)
						: Math.min(screen.width - panelW - 4, left + width + 4);
				return List.of(new Rect2i(px, top, panelW, panelH));
			}
		});

		registration.addGuiContainerHandler(BackroomsStationScreen.class, new IGuiContainerHandler<>() {
			@Override
			public List<Rect2i> getGuiExtraAreas(BackroomsStationScreen screen) {
				return screen.extraGuiAreas();
			}
		});
	}
}
