package dev.noclip.backrooms.level1;

import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.RemoveStatusEffectsConsumeEffect;

/** What wanderers pick up in the parking structure: almond water, alloy and phosphor powder. */
public final class Level1Items {
	/**
	 * The Backrooms' water: it steadies the mind. Drinking it clears the nausea, darkness and blindness
	 * that places like the Red Rooms bring on, and mends a little.
	 */
	public static final Item ALMOND_WATER = register("almond_water", new Item.Properties().stacksTo(16)
			.food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.6F).alwaysEdible().build(),
					Consumables.defaultDrink()
							.onConsume(new RemoveStatusEffectsConsumeEffect(HolderSet.direct(MobEffects.NAUSEA, MobEffects.DARKNESS, MobEffects.BLINDNESS)))
							.onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0)))
							// Sharper eyes than before, the wiki says.
							.onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0)))
							.build())
			.usingConvertsTo(Items.GLASS_BOTTLE), Item::new);
	/** Scrap metal worth something to the settlements; four make an iron ingot. */
	public static final Item ALLOY = register("alloy", new Item.Properties(), Item::new);
	/** Dab it on a wall, floor or ceiling to leave a mark that glows in the dark. */
	public static final Item PHOSPHOR_POWDER = register("phosphor_powder", new Item.Properties(), PhosphorPowderItem::new);
	/** Cloth and tape from the crates: eaten like food, it mends two hearts. */
	public static final Item BANDAGE = register("bandage", new Item.Properties().stacksTo(16)
			.food(new FoodProperties.Builder().nutrition(0).saturationModifier(0F).alwaysEdible().build(),
					Consumables.defaultFood()
							.onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.INSTANT_HEALTH, 1, 0)))
							.build()), Item::new);
	/** Found in debris: opens crates without cutting your hands. */
	public static final Item CROWBAR = register("crowbar", new Item.Properties().stacksTo(1), Item::new);
	/** Glows when something hostile is near. */
	public static final Item FRIEND_ORB = register("friend_orb", new Item.Properties().stacksTo(1), FriendOrbItem::new);
	/** Shoves things back with a jet of water. */
	public static final Item SQUIRT_GUN = register("squirt_gun", new Item.Properties().stacksTo(1), SquirtGunItem::new);
	/** An escape from certain death: a long jump, and no exits afterwards for a while. */
	public static final Item TELEPORT_BERRY = register("teleport_berry", new Item.Properties().stacksTo(8)
			.food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.2F).alwaysEdible().build(),
					Consumables.defaultFood().build()), TeleportBerryItem::new);
	/** The Ariane Circle's card for arriving anywhere new. */
	public static final Item CLIPP_CARD = register("clipp_card", new Item.Properties().stacksTo(1), ClippCardItem::new);
	/** What a smiler looks like: only ever shown floating in the dark, never handed out. */
	static final Item SMILER_FACE = register("smiler_face", new Item.Properties().stacksTo(1), Item::new);

	private Level1Items() {
	}

	static void register() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> output.accept(ALMOND_WATER));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> output.accept(ALLOY));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.accept(PHOSPHOR_POWDER);
			output.accept(BANDAGE);
			output.accept(CROWBAR);
			output.accept(FRIEND_ORB);
			output.accept(SQUIRT_GUN);
			output.accept(TELEPORT_BERRY);
			output.accept(CLIPP_CARD);
		});
	}

	private static Item register(String name, Item.Properties properties, Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Level1.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}
}
