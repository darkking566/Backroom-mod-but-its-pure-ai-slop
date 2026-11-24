package dev.noclip.backrooms.monsters;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.monsters.arms.Arms;
import dev.noclip.backrooms.monsters.clump.Clump;
import dev.noclip.backrooms.monsters.duller.Duller;
import dev.noclip.backrooms.monsters.hound.Hound;
import dev.noclip.backrooms.monsters.skinstealer.SkinStealer;
import dev.noclip.backrooms.monsters.smiler.Smiler;
import dev.noclip.backrooms.monsters.wretch.Wretch;
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
	public static final EntityType<Smiler> SMILER = register("smiler",
			EntityType.Builder.of(Smiler::new, MobCategory.MONSTER).sized(0.75F, 2.05F).eyeHeight(1.72F).clientTrackingRange(10));
	public static final Item SMILER_SPAWN_EGG = registerEgg("smiler_spawn_egg", SMILER);
	public static final EntityType<SkinStealer> SKIN_STEALER = register("skin_stealer",
			EntityType.Builder.of(SkinStealer::new, MobCategory.MONSTER).sized(0.7F, 2.15F).eyeHeight(1.92F).clientTrackingRange(10));
	public static final Item SKIN_STEALER_SPAWN_EGG = registerEgg("skin_stealer_spawn_egg", SKIN_STEALER);
	public static final EntityType<Clump> CLUMP = register("clump",
			EntityType.Builder.of(Clump::new, MobCategory.MONSTER).sized(1.05F, 0.95F).eyeHeight(0.55F).clientTrackingRange(10));
	public static final Item CLUMP_SPAWN_EGG = registerEgg("clump_spawn_egg", CLUMP);
	public static final EntityType<Duller> DULLER = register("duller",
			EntityType.Builder.of(Duller::new, MobCategory.MONSTER).sized(0.65F, 2.25F).eyeHeight(1.98F).clientTrackingRange(10));
	public static final Item DULLER_SPAWN_EGG = registerEgg("duller_spawn_egg", DULLER);
	public static final EntityType<Arms> ARMS = register("arms",
			EntityType.Builder.of(Arms::new, MobCategory.MONSTER).sized(1.15F, 1.85F).eyeHeight(1.2F).clientTrackingRange(10));
	public static final Item ARMS_SPAWN_EGG = registerEgg("arms_spawn_egg", ARMS);
	public static final EntityType<Wretch> WRETCH = register("wretch",
			EntityType.Builder.of(Wretch::new, MobCategory.MONSTER).sized(0.65F, 1.95F).eyeHeight(1.68F).clientTrackingRange(10));
	public static final Item WRETCH_SPAWN_EGG = registerEgg("wretch_spawn_egg", WRETCH);

	public static final SoundEvent HOUND_GROWL = sound("hound.growl");
	public static final SoundEvent HOUND_ROAR = sound("hound.roar");
	public static final SoundEvent HOUND_HURT = sound("hound.hurt");
	public static final SoundEvent HOUND_DEATH = sound("hound.death");

	private Monsters() {
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(HOUND, Hound.createAttributes());
		FabricDefaultAttributeRegistry.register(SMILER, Smiler.createAttributes());
		FabricDefaultAttributeRegistry.register(SKIN_STEALER, SkinStealer.createAttributes());
		FabricDefaultAttributeRegistry.register(CLUMP, Clump.createAttributes());
		FabricDefaultAttributeRegistry.register(DULLER, Duller.createAttributes());
		FabricDefaultAttributeRegistry.register(ARMS, Arms.createAttributes());
		FabricDefaultAttributeRegistry.register(WRETCH, Wretch.createAttributes());
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> {
			output.accept(HOUND_SPAWN_EGG);
			output.accept(SMILER_SPAWN_EGG);
			output.accept(SKIN_STEALER_SPAWN_EGG);
			output.accept(CLUMP_SPAWN_EGG);
			output.accept(DULLER_SPAWN_EGG);
			output.accept(ARMS_SPAWN_EGG);
			output.accept(WRETCH_SPAWN_EGG);
		});
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
