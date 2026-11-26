package dev.noclip.backrooms.monsters;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.monsters.arms.Arms;
import dev.noclip.backrooms.monsters.bacteria.Bacteria;
import dev.noclip.backrooms.monsters.beastlevel5.BeastOfLevel5;
import dev.noclip.backrooms.monsters.bonethief.BoneThief;
import dev.noclip.backrooms.monsters.clump.Clump;
import dev.noclip.backrooms.monsters.crawler.Crawler;
import dev.noclip.backrooms.monsters.deathmoth.Deathmoth;
import dev.noclip.backrooms.monsters.duller.Duller;
import dev.noclip.backrooms.monsters.faceling.Faceling;
import dev.noclip.backrooms.monsters.hound.Hound;
import dev.noclip.backrooms.monsters.nguithrxurh.Nguithrxurh;
import dev.noclip.backrooms.monsters.partygoer.Partygoer;
import dev.noclip.backrooms.monsters.partypooper.Partypooper;
import dev.noclip.backrooms.monsters.shepherdsnake.ShepherdSnake;
import dev.noclip.backrooms.monsters.shriek.Shriek;
import dev.noclip.backrooms.monsters.skinstealer.SkinStealer;
import dev.noclip.backrooms.monsters.smiler.Smiler;
import dev.noclip.backrooms.monsters.windowentity.WindowEntity;
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
			EntityType.Builder.of(SkinStealer::new, MobCategory.MONSTER).sized(0.68F, 2.75F).eyeHeight(2.5F).clientTrackingRange(10));
	public static final Item SKIN_STEALER_SPAWN_EGG = registerEgg("skin_stealer_spawn_egg", SKIN_STEALER);
	public static final EntityType<Clump> CLUMP = register("clump",
			EntityType.Builder.of(Clump::new, MobCategory.MONSTER).sized(1.05F, 0.95F).eyeHeight(0.55F).clientTrackingRange(10));
	public static final Item CLUMP_SPAWN_EGG = registerEgg("clump_spawn_egg", CLUMP);
	public static final EntityType<Duller> DULLER = register("duller",
			EntityType.Builder.of(Duller::new, MobCategory.MONSTER).sized(0.7F, 3.65F).eyeHeight(3.3F).clientTrackingRange(10));
	public static final Item DULLER_SPAWN_EGG = registerEgg("duller_spawn_egg", DULLER);
	public static final EntityType<Arms> ARMS = register("arms",
			EntityType.Builder.of(Arms::new, MobCategory.MONSTER).sized(1.15F, 1.85F).eyeHeight(1.2F).clientTrackingRange(10));
	public static final Item ARMS_SPAWN_EGG = registerEgg("arms_spawn_egg", ARMS);
	public static final EntityType<Wretch> WRETCH = register("wretch",
			EntityType.Builder.of(Wretch::new, MobCategory.MONSTER).sized(0.65F, 1.95F).eyeHeight(1.68F).clientTrackingRange(10));
	public static final Item WRETCH_SPAWN_EGG = registerEgg("wretch_spawn_egg", WRETCH);
	public static final EntityType<Shriek> SHRIEK = register("shriek",
			EntityType.Builder.of(Shriek::new, MobCategory.MONSTER).sized(1.45F, 1.85F).eyeHeight(1.3F).clientTrackingRange(10));
	public static final Item SHRIEK_SPAWN_EGG = registerEgg("shriek_spawn_egg", SHRIEK);
	public static final EntityType<Crawler> CRAWLER = register("crawler",
			EntityType.Builder.of(Crawler::new, MobCategory.MONSTER).sized(1.25F, 0.9F).eyeHeight(0.55F).clientTrackingRange(10));
	public static final Item CRAWLER_SPAWN_EGG = registerEgg("crawler_spawn_egg", CRAWLER);
	public static final EntityType<ShepherdSnake> SHEPHERD_SNAKE = register("shepherd_snake",
			EntityType.Builder.of(ShepherdSnake::new, MobCategory.MONSTER).sized(2.4F, 3.8F).eyeHeight(3.4F).clientTrackingRange(16));
	public static final Item SHEPHERD_SNAKE_SPAWN_EGG = registerEgg("shepherd_snake_spawn_egg", SHEPHERD_SNAKE);
	public static final EntityType<Nguithrxurh> NGUITHRXURH = register("nguithrxurh",
			EntityType.Builder.of(Nguithrxurh::new, MobCategory.MONSTER).sized(1.65F, 1.05F).eyeHeight(0.62F).clientTrackingRange(12));
	public static final Item NGUITHRXURH_SPAWN_EGG = registerEgg("nguithrxurh_spawn_egg", NGUITHRXURH);
	public static final EntityType<Deathmoth> DEATHMOTH = register("deathmoth",
			EntityType.Builder.of(Deathmoth::new, MobCategory.MONSTER).sized(1.25F, 1.45F).eyeHeight(0.95F).clientTrackingRange(12));
	public static final Item DEATHMOTH_SPAWN_EGG = registerEgg("deathmoth_spawn_egg", DEATHMOTH);
	public static final EntityType<Faceling> FACELING = register("faceling",
			EntityType.Builder.of(Faceling::new, MobCategory.CREATURE).sized(0.65F, 2.1F).eyeHeight(1.85F).clientTrackingRange(10));
	public static final Item FACELING_SPAWN_EGG = registerEgg("faceling_spawn_egg", FACELING);
	public static final EntityType<Partygoer> PARTYGOER = register("partygoer",
			EntityType.Builder.of(Partygoer::new, MobCategory.MONSTER).sized(0.75F, 2.35F).eyeHeight(2.05F).clientTrackingRange(12));
	public static final Item PARTYGOER_SPAWN_EGG = registerEgg("partygoer_spawn_egg", PARTYGOER);
	public static final EntityType<Bacteria> BACTERIA = register("bacteria",
			EntityType.Builder.of(Bacteria::new, MobCategory.MONSTER).sized(0.8F, 2.68F).eyeHeight(2.38F).clientTrackingRange(14));
	public static final Item BACTERIA_SPAWN_EGG = registerEgg("bacteria_spawn_egg", BACTERIA);
	public static final EntityType<BoneThief> BONE_THIEF = register("bone_thief",
			EntityType.Builder.of(BoneThief::new, MobCategory.MONSTER).sized(1.6F, 1.4F).eyeHeight(0.85F).clientTrackingRange(12));
	public static final Item BONE_THIEF_SPAWN_EGG = registerEgg("bone_thief_spawn_egg", BONE_THIEF);
	public static final EntityType<Partypooper> PARTYPOOPER = register("partypooper",
			EntityType.Builder.of(Partypooper::new, MobCategory.CREATURE).sized(0.68F, 2.04F).eyeHeight(1.78F).clientTrackingRange(10));
	public static final Item PARTYPOOPER_SPAWN_EGG = registerEgg("partypooper_spawn_egg", PARTYPOOPER);
	public static final EntityType<BeastOfLevel5> BEAST_LEVEL5 = register("beast_level5",
			EntityType.Builder.of(BeastOfLevel5::new, MobCategory.MONSTER).sized(0.95F, 2.65F).eyeHeight(2.38F).clientTrackingRange(16));
	public static final Item BEAST_LEVEL5_SPAWN_EGG = registerEgg("beast_level5_spawn_egg", BEAST_LEVEL5);
	public static final EntityType<WindowEntity> WINDOW_ENTITY = register("window_entity",
			EntityType.Builder.of(WindowEntity::new, MobCategory.MONSTER).sized(0.75F, 2.15F).eyeHeight(1.88F).clientTrackingRange(12));
	public static final Item WINDOW_ENTITY_SPAWN_EGG = registerEgg("window_entity_spawn_egg", WINDOW_ENTITY);

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
		FabricDefaultAttributeRegistry.register(SHRIEK, Shriek.createAttributes());
		FabricDefaultAttributeRegistry.register(CRAWLER, Crawler.createAttributes());
		FabricDefaultAttributeRegistry.register(SHEPHERD_SNAKE, ShepherdSnake.createAttributes());
		FabricDefaultAttributeRegistry.register(NGUITHRXURH, Nguithrxurh.createAttributes());
		FabricDefaultAttributeRegistry.register(DEATHMOTH, Deathmoth.createAttributes());
		FabricDefaultAttributeRegistry.register(FACELING, Faceling.createAttributes());
		FabricDefaultAttributeRegistry.register(PARTYGOER, Partygoer.createAttributes());
		FabricDefaultAttributeRegistry.register(BACTERIA, Bacteria.createAttributes());
		FabricDefaultAttributeRegistry.register(BONE_THIEF, BoneThief.createAttributes());
		FabricDefaultAttributeRegistry.register(PARTYPOOPER, Partypooper.createAttributes());
		FabricDefaultAttributeRegistry.register(BEAST_LEVEL5, BeastOfLevel5.createAttributes());
		FabricDefaultAttributeRegistry.register(WINDOW_ENTITY, WindowEntity.createAttributes());
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> {
			output.accept(HOUND_SPAWN_EGG);
			output.accept(SMILER_SPAWN_EGG);
			output.accept(SKIN_STEALER_SPAWN_EGG);
			output.accept(CLUMP_SPAWN_EGG);
			output.accept(DULLER_SPAWN_EGG);
			output.accept(ARMS_SPAWN_EGG);
			output.accept(WRETCH_SPAWN_EGG);
			output.accept(SHRIEK_SPAWN_EGG);
			output.accept(CRAWLER_SPAWN_EGG);
			output.accept(SHEPHERD_SNAKE_SPAWN_EGG);
			output.accept(NGUITHRXURH_SPAWN_EGG);
			output.accept(DEATHMOTH_SPAWN_EGG);
			output.accept(FACELING_SPAWN_EGG);
			output.accept(PARTYGOER_SPAWN_EGG);
			output.accept(BACTERIA_SPAWN_EGG);
			output.accept(BONE_THIEF_SPAWN_EGG);
			output.accept(PARTYPOOPER_SPAWN_EGG);
			output.accept(BEAST_LEVEL5_SPAWN_EGG);
			output.accept(WINDOW_ENTITY_SPAWN_EGG);
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
