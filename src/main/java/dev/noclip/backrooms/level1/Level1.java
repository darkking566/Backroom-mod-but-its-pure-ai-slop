package dev.noclip.backrooms.level1;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Level 1, "the Habitable Zone": an endless concrete parking structure crossed by blocks of tight corridors.
 * Everything for this level lives in this package; the rest of the mod only calls {@link #register()}.
 */
public final class Level1 {
	public static final String MOD_ID = "noclip_backrooms";
	public static final ResourceKey<Level> LEVEL_0 = ResourceKey.create(Registries.DIMENSION, id("level_0"));
	public static final ResourceKey<Level> LEVEL_1 = ResourceKey.create(Registries.DIMENSION, id("level_1"));

	private Level1() {
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static void register() {
		Level1Blocks.register();
		Level1Sounds.register();
		Registry.register(BuiltInRegistries.CHUNK_GENERATOR, id("level_1"), Level1ChunkGenerator.CODEC);
		FlickeringWalls.register();
		Blackouts.register();
		Crates.register();
		Level1Ambience.register();
	}
}
