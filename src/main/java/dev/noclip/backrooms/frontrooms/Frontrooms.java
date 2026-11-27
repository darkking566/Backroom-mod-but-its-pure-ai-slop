package dev.noclip.backrooms.frontrooms;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.Home;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * The Frontrooms (docs/project/1-gameplay/README.md, "獨立維度：前室"): M.E.G.'s quarantine institute and a patch of
 * ordinary country around it, walled in. Everyone who gets out of the Backrooms is brought here, and stays until one of
 * the endings; time runs here. Everything for it lives in this package; the rest of the mod only calls
 * {@link #register()} and refers to {@link Home#FRONTROOMS}.
 */
public final class Frontrooms {
	public static final ResourceKey<Level> FRONTROOMS = Home.FRONTROOMS;

	private Frontrooms() {
	}

	public static Identifier id(String path) {
		return BackroomsMod.id(path);
	}

	public static void register() {
		FrontroomsBlocks.register();
		InstituteProps.register();
		TownLife.register();
		Registry.register(BuiltInRegistries.CHUNK_GENERATOR, id("frontrooms"), FrontroomsChunkGenerator.CODEC);
		Institute.register();
	}
}
