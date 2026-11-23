package dev.noclip.backrooms.monsters;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.monsters.hound.Hound;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

/**
 * Monsters can turn up on more than one level, so they live here, one package each (monsters/hound, ...).
 * Which level spawns a monster, and when, is up to that level: Level 1's blackouts bring hounds (level1.Hounds).
 * The look of each monster comes from a box rig exported from Blender (assets/noclip_backrooms/monsters/*.json).
 */
public final class Monsters {
	public static final EntityType<Hound> HOUND = register("hound",
			EntityType.Builder.of(Hound::new, MobCategory.MONSTER).sized(0.95F, 1.05F).eyeHeight(0.8F).clientTrackingRange(10));
	public static final Item HOUND_SPAWN_EGG = registerEgg("hound_spawn_egg", HOUND);

	public static final SoundEvent HOUND_GROWL = sound("hound.growl");
	public static final SoundEvent HOUND_ROAR = sound("hound.roar");
	public static final SoundEvent HOUND_HURT = sound("hound.hurt");
	public static final SoundEvent HOUND_DEATH = sound("hound.death");

	private Monsters() {
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(HOUND, Hound.createAttributes());
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> output.accept(HOUND_SPAWN_EGG));
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, BackroomsMod.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	private static Item registerEgg(String name, EntityType<?> type) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BackroomsMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new SpawnEggItem(new Item.Properties().spawnEgg(type).setId(key)));
	}

	private static SoundEvent sound(String path) {
		Identifier id = BackroomsMod.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
