package dev.noclip.backrooms.level4;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.LevelExits;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Level 4, "Abandoned Office" (Backrooms Chinese Wiki): open-plan modern office bays, charcoal carpet with
 * desk indentations, blacked-out vs Entity 2 trap windows, rainy Almond Water atriums, M.E.G. Base Omega,
 * Amor Incrementum, and T.B.D. Everything for this level lives in this package.
 */
public final class Level4 {
	public static final String MOD_ID = BackroomsMod.MOD_ID;
	public static final ResourceKey<Level> LEVEL_4 = ResourceKey.create(Registries.DIMENSION, id("level_4"));

	private Level4() {
	}

	public static Identifier id(String path) {
		return BackroomsMod.id(path);
	}

	public static void register() {
		Level4Blocks.register();
		Level4Items.register();
		Level4Sounds.register();
		Registry.register(BuiltInRegistries.CHUNK_GENERATOR, id("level_4"), Level4ChunkGenerator.CODEC);
		// Opens entrances already pointing to "4" (Level 2 marked doors and Level 3 elevators).
		LevelExits.open("4", Level4Travel::enter);
		Level4Bases.register();
		Level4Wanderers.register();
		Level4Windows.register();
		Level4VanishingExits.register();
		Level4Monsters.register();
		Level4Events.register();
		Level4Props.register();
	}
}
