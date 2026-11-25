package dev.noclip.backrooms.crafting.jei;

import dev.noclip.backrooms.crafting.StationRecipe;
import dev.noclip.backrooms.crafting.StationType;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * JEI Recipe Category showing the TRUE shaped grid for every Backrooms workstation:
 * - Salvage Bench (1 input -> up to 4 outputs)
 * - Almond Distiller / Anomaly Altar (3x3 shaped grid)
 * - Workshop Bench (5x5 shaped grid)
 * - Primary Spiderweb Crafting Table (7x7 concentric shaped grid)
 * - Ultimate Spiderweb Crafting Table (9x9 concentric shaped grid)
 */
public final class StationRecipeCategory implements IRecipeCategory<StationRecipe> {
	private final StationType stationType;
	private final IRecipeType<StationRecipe> recipeType;
	private final IDrawable icon;
	private final IDrawable arrow;
	private final int step;
	private final int width;
	private final int height;
	private final int arrowX;
	private final int arrowY;

	public StationRecipeCategory(StationType stationType, IRecipeType<StationRecipe> recipeType, IGuiHelper guiHelper) {
		this.stationType = stationType;
		this.recipeType = recipeType;
		this.icon = guiHelper.createDrawableItemLike(stationType.stationItem());
		this.arrow = guiHelper.getRecipeArrow();

		this.step = switch (stationType) {
			case ULTIMATE_SPIDERWEB -> 16;
			case SPIDERWEB -> 17;
			default -> 18;
		};
		int gridPxW = stationType.gridWidth() * step;
		int gridPxH = stationType.gridHeight() * step;
		int outW = stationType.outputSlotCount() > 1 ? 46 : 28;
		this.width = Math.max(136, 8 + gridPxW + 30 + outW + 6);
		this.height = Math.max(54, gridPxH + 8);
		this.arrowX = 8 + gridPxW + 4;
		this.arrowY = (this.height - 17) / 2;
	}

	@Override
	public IRecipeType<StationRecipe> getRecipeType() {
		return recipeType;
	}

	@Override
	public Component getTitle() {
		return stationType.title();
	}

	@Override
	public int getWidth() {
		return width;
	}

	@Override
	public int getHeight() {
		return height;
	}

	@Override
	public IDrawable getIcon() {
		return icon;
	}

	@Override
	public Identifier getIdentifier(StationRecipe recipe) {
		return recipe.id();
	}

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, StationRecipe recipe, IFocusGroup focuses) {
		int gw = stationType.gridWidth();
		int gh = stationType.gridHeight();
		int ox = recipe.centeredOffsetX();
		int oy = recipe.centeredOffsetY();
		int yBase = (height - gh * step) / 2;

		for (int row = 0; row < gh; row++) {
			for (int col = 0; col < gw; col++) {
				int px = col - ox;
				int py = row - oy;
				StationRecipe.ItemCount req = recipe.cellAt(px, py);
				var slot = builder.addInputSlot(6 + col * step, yBase + row * step);
				if (req != null) {
					ItemStack st = req.toStack();
					if (!st.isEmpty()) slot.add(st);
				}
				if (step >= 18) {
					slot.setStandardSlotBackground();
				}
			}
		}

		List<ItemStack> outputs = recipe.allOutputStacks();
		int outX = arrowX + 30;
		if (outputs.size() <= 1) {
			var outSlot = builder.addOutputSlot(outX, (height - 18) / 2).setOutputSlotBackground();
			if (!outputs.isEmpty()) outSlot.add(outputs.getFirst());
		} else {
			int outYBase = (height - 38) / 2;
			for (int i = 0; i < outputs.size() && i < 4; i++) {
				builder.addOutputSlot(outX + (i % 2) * 20, outYBase + (i / 2) * 20)
						.add(outputs.get(i))
						.setStandardSlotBackground();
			}
		}
	}

	@Override
	public void draw(StationRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphicsExtractor guiGraphics, double mouseX, double mouseY) {
		// Compact cell borders for 7x7 and 9x9 spiderweb grids + subtle center core highlight
		if (step < 18) {
			int gw = stationType.gridWidth();
			int gh = stationType.gridHeight();
			int yBase = (height - gh * step) / 2;
			int halfW = gw / 2;
			int halfH = gh / 2;
			for (int row = 0; row < gh; row++) {
				for (int col = 0; col < gw; col++) {
					int x = 6 + col * step;
					int y = yBase + row * step;
					int dist = Math.max(Math.abs(col - halfW), Math.abs(row - halfH));
					int bg = dist == 0 ? 0xFF8C5DB0 : (dist <= (gw >= 9 ? 2 : 1) ? 0xFF687E96 : 0xFF8B8B8B);
					guiGraphics.fill(x, y, x + 16, y + 16, 0xFF373737);
					guiGraphics.fill(x + 1, y + 1, x + 15, y + 15, bg);
				}
			}
		}
		arrow.draw(guiGraphics, arrowX, arrowY);
	}
}
