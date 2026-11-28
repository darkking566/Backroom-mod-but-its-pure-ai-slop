package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.LevelExits;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.TickTask;
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
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
			if (level.dimension() != LEVEL_1 || chunk.getBlockEntitiesPos().isEmpty()) return;
			List<BlockPos> vehicleMasters = new ArrayList<>();
			for (BlockPos bePos : chunk.getBlockEntitiesPos()) {
				if (chunk.getBlockState(bePos).getBlock() instanceof Level1VehicleBlock) {
					vehicleMasters.add(bePos.immutable());
				}
			}
			if (!vehicleMasters.isEmpty()) {
				var server = level.getServer();
				server.schedule(new TickTask(server.getTickCount(), () -> Level1VehicleBlock.populateChunkParts(level, vehicleMasters)));
			}
		});
		// How you arrive here through an exit from another level (see core.LevelExits).
		LevelExits.open("1", Level1Travel::enter);
		FlickeringWalls.register();
		Blackouts.register();
		Level1PoiEvents.register();
		Crates.register();
		Markers.register();
		Gardens.register();
		Residents.register();
		Warps.register();
		Thresholds.register();
		// M.E.G. escort patrols disabled per playtest feedback.
		// Patrols.register();
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
		Level1Arms.register();
		Wretches.register();
		Shrieks.register();
		Crawlers.register();
		ShepherdSnakes.register();
		Nguithrxurhs.register();
		Hounds.register();
		Level1Ambience.register();
	}
}
