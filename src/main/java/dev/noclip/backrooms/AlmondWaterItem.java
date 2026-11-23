package dev.noclip.backrooms;

import net.minecraft.core.HolderSet;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.RemoveStatusEffectsConsumeEffect;

/**
 * Almond Water: a sweet almond-scented Backrooms staple that restores hunger, heals briefly,
 * and clears psychological strain (nausea, darkness, slowness, mining fatigue, blindness).
 */
final class AlmondWaterItem extends Item {
	private static final FoodProperties FOOD = new FoodProperties(4, 4.8F, true);
	private static final Consumable CONSUMABLE = Consumables.defaultDrink()
			.sound(SoundEvents.HONEY_DRINK)
			.onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0)))
			.onConsume(new RemoveStatusEffectsConsumeEffect(HolderSet.direct(
					MobEffects.NAUSEA,
					MobEffects.DARKNESS,
					MobEffects.SLOWNESS,
					MobEffects.MINING_FATIGUE,
					MobEffects.BLINDNESS)))
			.build();

	AlmondWaterItem(Properties properties) {
		super(properties.stacksTo(16).usingConvertsTo(Items.GLASS_BOTTLE).food(FOOD, CONSUMABLE));
	}
}
