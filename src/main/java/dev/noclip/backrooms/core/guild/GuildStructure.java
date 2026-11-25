package dev.noclip.backrooms.core.guild;

import com.mojang.serialization.MapCodec;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * The adventurers' guild next to a village. Its structure set copies the villages' placement exactly (same salt and
 * spacing), so it is asked about the very chunks a village starts in; it checks that a village would stand there, then
 * picks a spot just outside the village's reach (village houses keep within 80 blocks of its start) on dry, fairly
 * level ground, with the door facing the village and a path leading to it.
 */
public final class GuildStructure extends Structure {
	public static final MapCodec<GuildStructure> CODEC = simpleCodec(GuildStructure::new);
	/** From the village's start to the guild's front wall. */
	private static final int NEAR = 88, FAR = 96;
	private static final int STEEP = 10;

	public GuildStructure(StructureSettings settings) {
		super(settings);
	}

	@Override
	public StructureType<?> type() {
		return Guilds.GUILD;
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		int vx = context.chunkPos().getMinBlockX() + 8, vz = context.chunkPos().getMinBlockZ() + 8;
		if (!context.validBiome().test(biome(context, vx, vz))) return Optional.empty();
		WorldgenRandom random = context.random();
		int first = random.nextInt(4);
		Site best = null;
		for (int attempt = 0; attempt < 8; attempt++) {
			Direction side = Direction.from2DDataValue((first + attempt) % 4);
			Site site = Site.find(context, vx, vz, side, NEAR + random.nextInt(FAR - NEAR + 1), random.nextInt(33) - 16);
			if (site != null && (best == null || site.slope < best.slope)) best = site;
			if (best != null && best.slope <= 4) break;
		}
		if (best == null || best.slope > STEEP) return Optional.empty();
		Site site = best;
		GuildPiece hall = site.hall(GuildPalette.of(biome(context, site.frontX, site.frontZ)));
		BlockPos centre = hall.getBoundingBox().getCenter();
		return Optional.of(new GenerationStub(new BlockPos(centre.getX(), site.floor, centre.getZ()), builder -> {
			builder.addPiece(hall);
			int length = Math.clamp(site.distance - 48, 8, 48);
			Direction toward = site.side.getOpposite();
			// The path starts just outside the front step, under the door's left cell.
			int sx = site.frontX + toward.getStepX(), sz = site.frontZ + toward.getStepZ();
			builder.addPiece(new GuildRoadPiece(sx, sz, toward, site.across(), length, hall.palette() == GuildPalette.DESERT,
					context.heightAccessor().getMaxY() - 1));
		}));
	}

	private static Holder<Biome> biome(GenerationContext context, int x, int z) {
		int y = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
		return context.biomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), context.randomState().sampler());
	}

	/** A place for the hall: its facing, its front (the door's left cell) and its floor height. */
	private record Site(Direction side, int distance, int frontX, int frontZ, int minX, int minZ, int floor, int slope) {
		static Site find(GenerationContext context, int vx, int vz, Direction side, int distance, int lateral) {
			Direction across = across(side);
			int fx = vx + side.getStepX() * distance + across.getStepX() * lateral;
			int fz = vz + side.getStepZ() * distance + across.getStepZ() * lateral;
			GuildPiece probe = GuildPiece.facingFront(fx, 0, fz, side, GuildPalette.PLAINS);
			int minX = probe.getBoundingBox().minX(), minZ = probe.getBoundingBox().minZ();
			int[][] spots = {{4, 9}, {25, 9}, {4, 24}, {25, 24}, {14, 16}, {14, 1}};
			int[] heights = new int[spots.length];
			for (int i = 0; i < spots.length; i++) {
				int x = probe.worldX(spots[i][0], spots[i][1]), z = probe.worldZ(spots[i][0], spots[i][1]);
				int surface = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
				int ground = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
				if (surface != ground) return null; // water
				heights[i] = ground;
			}
			int[] sorted = heights.clone();
			Arrays.sort(sorted);
			return new Site(side, distance, fx, fz, minX, minZ, sorted[sorted.length / 2], sorted[sorted.length - 1] - sorted[0]);
		}

		/** The direction of the hall's own +x, along its front wall. */
		static Direction across(Direction side) {
			return side.getAxis() == Direction.Axis.Z ? Direction.EAST : Direction.SOUTH;
		}

		Direction across() {
			return across(side);
		}

		GuildPiece hall(GuildPalette palette) {
			return new GuildPiece(minX, floor, minZ, side, palette);
		}
	}
}
