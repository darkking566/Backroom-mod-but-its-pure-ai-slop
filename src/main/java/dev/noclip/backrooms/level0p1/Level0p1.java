package dev.noclip.backrooms.level0p1;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.Sublevels;
import dev.noclip.backrooms.level0p1.robots.Robots;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Level 0.1, "Zenith Station" (Wikidot, by CutTheBirch): a vast steel-and-glass space station swallowed whole by
 * Level 0, a hollow sphere of decks round a huge empty middle. A sublevel of Level 0; see core.Sublevels.
 * Everything for this level lives in this package; the rest of the mod only calls {@link #register()}.
 */
public final class Level0p1 {
	public static final String KEY = "0.1";
	public static final ResourceKey<Level> LEVEL_0_1 = ResourceKey.create(Registries.DIMENSION, id("level_0.1"));

	private Level0p1() {
	}

	public static Identifier id(String path) {
		return BackroomsMod.id(path);
	}

	public static void register() {
		ZenithBlocks.register();
		ZenithItems.register();
		ZenithSounds.register();
		Robots.register();
		Registry.register(BuiltInRegistries.CHUNK_GENERATOR, id("level_0.1"), ZenithChunkGenerator.CODEC);
		// Leaving goes back to Level 0; a random cut-out usually drops you into Level 1 (the Wiki: "may also go to Level 0").
		Sublevels.register(KEY, "0", LEVEL_0_1, Map.of("1", 85, "0", 15), ZenithTravel::enter);
		ZenithDoors.register();
		ZenithClimate.register();
		ZenithAmbience.register();
		ZenithHallucinations.register();
		ZenithBlackouts.register();
		ZenithEntrance.register();
		ZenithRobots.register();
	}
}
