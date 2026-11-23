package dev.noclip.backrooms;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class BackroomsMod implements ModInitializer {
	static final String MOD_ID = "noclip_backrooms";
	static final ResourceKey<Level> LEVEL_0 = ResourceKey.create(Registries.DIMENSION, id("level_0"));

	static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModBlocks.register();
		ModItems.register();
		ModSounds.register();
		Registry.register(BuiltInRegistries.CHUNK_GENERATOR, id("level_0"), Level0ChunkGenerator.CODEC);
		Noclip.register();
		Level0Ambience.register();
		RedRoomEffects.register();
		RedRoomLoop.register();
		EdgeShift.register();
		ShadowHallucination.register();
		PitEffects.register();
		FlashlightLight.register();
	}
}
