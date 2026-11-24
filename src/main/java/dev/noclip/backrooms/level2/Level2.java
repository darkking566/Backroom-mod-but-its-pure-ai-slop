package dev.noclip.backrooms.level2;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.LevelExits;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Level 2, "Abandoned Utility Halls" (Wikidot; "Pipe Dreams" on the Fandom wiki): endless, tight maintenance
 * tunnels of chalky concrete and old brick, one wall lined floor to ceiling with pipes and machinery, lit by
 * sparse lights on series circuits that are often dead. Everything for this level lives in this package; the rest
 * of the mod only calls {@link #register()} and refers to {@link #LEVEL_2}.
 */
public final class Level2 {
	public static final String MOD_ID = BackroomsMod.MOD_ID;
	public static final ResourceKey<Level> LEVEL_2 = ResourceKey.create(Registries.DIMENSION, id("level_2"));

	private Level2() {
	}

	public static Identifier id(String path) {
		return BackroomsMod.id(path);
	}

	public static void register() {
		Level2Blocks.register();
		Level2Sounds.register();
		Registry.register(BuiltInRegistries.CHUNK_GENERATOR, id("level_2"), Level2ChunkGenerator.CODEC);
		// How you arrive here through an exit from another level (see core.LevelExits): Level 1's stairs down lead here.
		LevelExits.open("2", Level2Travel::enter);
		Level2Heat.register();
		Level2Windows.register();
		Level2Hounds.register();
		Level2Monsters.register();
		Level2Bases.register();
		Level2Ambience.register();
	}
}
