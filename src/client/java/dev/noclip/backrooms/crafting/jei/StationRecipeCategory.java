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
 * JEI Recipe Category for each of the 4 Backrooms workstations (Salvage Bench, Workshop Bench,
 * Almond Distiller, Anomaly Altar). Displays up to 6 multi-count inputs, a JEI arrow, and up to 4 outputs.
 */
public final class StationRecipeCategory implements IRecipeCategory<StationRecipe> {
	private static final int WIDTH = 156;
	private static final int HEIGHT = 64;

	private final StationType stationType;
	private final IRecipeType<StationRecipe> recipeType;
	private final IDrawable icon;
	private final IDrawable arrow;

	public StationRecipeCategory(StationType stationType, IRecipeType<StationRecipe> recipeType, IGuiHelper guiHelper) {
		this.stationType = stationType;
		this.recipeType = recipeType;
		this.icon = guiHelper.createDrawableItemLike(stationType.stationItem());
		this.arrow = guiHelper.getRecipeArrow();
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
		return WIDTH;
	}

	@Override
	public int getHeight() {
		return HEIGHT;
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
		// Left 3x2 or 3x3 input grid (up to 9 ingredients with exact counts for Spiderweb recipes)
		List<StationRecipe.ItemCount> inputs = recipe.inputs();
		boolean compact = inputs.size() > 6;
		int maxInputs = compact ? 9 : 6;
		int step = compact ? 19 : 20;
		int yStart = compact ? 4 : 12;
		for (int i = 0; i < inputs.size() && i < maxInputs; i++) {
			int col = i % 3;
			int row = i / 3;
			ItemStack stack = inputs.get(i).toStack();
			if (!stack.isEmpty()) {
				builder.addInputSlot(6 + col * step, yStart + row * step)
						.add(stack)
						.setStandardSlotBackground();
			}
		}

		// Right output slot(s): 1 centered output or 2x2 grid for salvage multi-outputs
		List<ItemStack> outputs = recipe.allOutputStacks();
		if (outputs.size() == 1) {
			builder.addOutputSlot(114, 22)
					.add(outputs.getFirst())
					.setOutputSlotBackground();
		} else {
			for (int i = 0; i < outputs.size() && i < 4; i++) {
				int col = i % 2;
				int row = i / 2;
				builder.addOutputSlot(106 + col * 20, 12 + row * 20)
						.add(outputs.get(i))
						.setStandardSlotBackground();
			}
		}
	}

	@Override
	public void draw(StationRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphicsExtractor guiGraphics, double mouseX, double mouseY) {
		arrow.draw(guiGraphics, 74, 23);
	}
}
