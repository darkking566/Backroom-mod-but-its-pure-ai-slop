package dev.noclip.backrooms.tools;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Tiered Backrooms dismantling & breaching tool (Tier 1 to Tier 4).
 * Displays its breaking tier and specialized authority in tooltips.
 */
public final class BackroomsToolItem extends Item {
	private final int tier;
	private final String loreKey;

	public BackroomsToolItem(Properties properties, int tier, String loreKey) {
		super(properties);
		this.tier = tier;
		this.loreKey = loreKey;
	}

	public int tier() {
		return tier;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> adder, TooltipFlag flag) {
		ChatFormatting color = switch (tier) {
			case 1 -> ChatFormatting.WHITE;
			case 2 -> ChatFormatting.AQUA;
			case 3 -> ChatFormatting.LIGHT_PURPLE;
			case 4 -> ChatFormatting.GOLD;
			default -> ChatFormatting.GRAY;
		};
		adder.accept(Component.translatable("tooltip.noclip_backrooms.tool_tier", tier).withStyle(color));
		if (loreKey != null && !loreKey.isEmpty()) {
			adder.accept(Component.translatable(loreKey).withStyle(ChatFormatting.GRAY));
		}
	}
}
