package dev.noclip.backrooms.level0;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.LevelExits;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Level 0, "the Lobby". Everything for this level lives in this package; the rest of the mod only
 * calls {@link #register()} and refers to {@link #LEVEL_0}.
 */
public final class Level0 {
	public static final ResourceKey<Level> LEVEL_0 = ResourceKey.create(Registries.DIMENSION, BackroomsMod.id("level_0"));

	private Level0() {
	}

	public static void register() {
		ModBlocks.register();
		ModItems.register();
		ModSounds.register();
		Registry.register(BuiltInRegistries.CHUNK_GENERATOR, BackroomsMod.id("level_0"), Level0ChunkGenerator.CODEC);
		Noclip.register();
		Level0Ambience.register();
		RedRoomEffects.register();
		RedRoomLoop.register();
		EdgeShift.register();
		ShadowHallucination.register();
		PitEffects.register();
		FlashlightLight.register();
		StaircaseBreaker.register();
		FuseBoxEvents.register();
		// Temporarily withdrawn until Claude finishes prop modeling and per-floor redistribution.
		// Level0Props.register();
		StageLoop.register();
		Level0Mirror.register();
		Level0MicroEvents.register();
		// How you arrive here through an exit from another level (see core.LevelExits).
		LevelExits.open("0", Level0Arrival::arrive);
	}
}
