package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.LevelExits;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Level 1, "the Habitable Zone": an endless concrete parking structure crossed by blocks of tight corridors.
 * Everything for this level lives in this package; the rest of the mod only calls {@link #register()}
 * and refers to {@link #LEVEL_1}.
 */
public final class Level1 {
	public static final String MOD_ID = BackroomsMod.MOD_ID;
	public static final ResourceKey<Level> LEVEL_1 = ResourceKey.create(Registries.DIMENSION, id("level_1"));

	private Level1() {
	}

	public static Identifier id(String path) {
		return BackroomsMod.id(path);
	}

	public static void register() {
		Level1Blocks.register();
		Level1Items.register();
		Level1Sounds.register();
		Registry.register(BuiltInRegistries.CHUNK_GENERATOR, id("level_1"), Level1ChunkGenerator.CODEC);
		// How you arrive here through an exit from another level (see core.LevelExits).
		LevelExits.open("1", Level1Travel::enter);
		FlickeringWalls.register();
		Blackouts.register();
		Crates.register();
		Markers.register();
		Gardens.register();
		Residents.register();
		Warps.register();
		Thresholds.register();
		Patrols.register();
		Workers.register();
		Smoke.register();
		Locks.register();
		HubRoute.register();
		Strays.register();
		Marauders.register();
		Smilers.register();
		SkinStealers.register();
		Clumps.register();
		Dullers.register();
		Hounds.register();
		Level1Ambience.register();
	}
}
