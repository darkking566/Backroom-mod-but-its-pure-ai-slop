package dev.noclip.backrooms.core.guild;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * The path from the guild's door toward the village, following the ground: a trodden path (sandstone in the desert)
 * with a lamp post every few blocks and a sign at the village end. It lays itself on whatever ground it finds, so its
 * box sits at the top of the world, out of the way of the terrain shaping that the hall asks for.
 */
public final class GuildRoadPiece extends StructurePiece {
	private final int startX, startZ, length;
	private final Direction toward, across;
	private final boolean sand;

	GuildRoadPiece(int startX, int startZ, Direction toward, Direction across, int length, boolean sand, int topY) {
		super(Guilds.ROAD, 0, box(startX, startZ, toward, across, length, topY));
		this.startX = startX;
		this.startZ = startZ;
		this.toward = toward;
		this.across = across;
		this.length = length;
		this.sand = sand;
	}

	GuildRoadPiece(CompoundTag tag) {
		super(Guilds.ROAD, tag);
		startX = tag.getIntOr("SX", 0);
		startZ = tag.getIntOr("SZ", 0);
		length = tag.getIntOr("Len", 0);
		toward = Direction.from2DDataValue(tag.getIntOr("Dir", 0));
		across = Direction.from2DDataValue(tag.getIntOr("Across", 0));
		sand = tag.getBooleanOr("Sand", false);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putInt("SX", startX);
		tag.putInt("SZ", startZ);
		tag.putInt("Len", length);
		tag.putInt("Dir", toward.get2DDataValue());
		tag.putInt("Across", across.get2DDataValue());
		tag.putBoolean("Sand", sand);
	}

	private static BoundingBox box(int x, int z, Direction toward, Direction across, int length, int topY) {
		int x1 = x + toward.getStepX() * (length - 1), z1 = z + toward.getStepZ() * (length - 1);
		int ax = across.getStepX(), az = across.getStepZ();
		return new BoundingBox(Math.min(x, x1) - 3 + Math.min(0, ax * 3), topY, Math.min(z, z1) - 3 + Math.min(0, az * 3),
				Math.max(x, x1) + 3 + Math.max(0, ax * 3), topY, Math.max(z, z1) + 3 + Math.max(0, az * 3));
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
			BoundingBox box, ChunkPos chunk, BlockPos pivot) {
		for (int t = 0; t < length; t++) {
			// Four wide, the door's two cells in the middle; the edges ragged.
			for (int w = -1; w <= 2; w++) {
				boolean edge = w == -1 || w == 2;
				if (edge && (hash(t, w) & 3) == 0) continue;
				if (t == length - 1 && edge || t >= length - 2 && (hash(t, w) & 1) == 0) continue;
				pave(level, box, cell(t, w));
			}
			if (t % 9 == 4) post(level, box, cell(t, t % 18 == 4 ? -2 : 3));
		}
		signpost(level, box, cell(length - 1, -2));
	}

	private BlockPos cell(int t, int w) {
		return new BlockPos(startX + toward.getStepX() * t + across.getStepX() * w, 0, startZ + toward.getStepZ() * t + across.getStepZ() * w);
	}

	private static int hash(int t, int w) {
		int h = t * 734287 + w * 912931;
		h ^= h >>> 13;
		return h * 0x5bd1e995 >>> 16;
	}

	/** The block the path lies on, or null where it should not go (water, a roof, a field). */
	private static BlockPos ground(WorldGenLevel level, BoundingBox box, BlockPos cell) {
		if (cell.getX() < box.minX() || cell.getX() > box.maxX() || cell.getZ() < box.minZ() || cell.getZ() > box.maxZ()) return null;
		BlockPos top = new BlockPos(cell.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cell.getX(), cell.getZ()) - 1, cell.getZ());
		BlockState state = level.getBlockState(top);
		boolean soil = state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.PODZOL)
				|| state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.MYCELIUM) || state.is(BlockTags.SAND) || state.is(Blocks.SNOW_BLOCK);
		return soil && level.getBlockState(top.above()).isAir() ? top : null;
	}

	private void pave(WorldGenLevel level, BoundingBox box, BlockPos cell) {
		BlockPos at = ground(level, box, cell);
		if (at == null) return;
		BlockState path = sand || level.getBlockState(at).is(BlockTags.SAND) ? Blocks.SMOOTH_SANDSTONE.defaultBlockState() : Blocks.DIRT_PATH.defaultBlockState();
		level.setBlock(at, path, 2);
	}

	private void post(WorldGenLevel level, BoundingBox box, BlockPos cell) {
		BlockPos at = ground(level, box, cell);
		if (at == null) return;
		BlockState post = sand ? Blocks.SANDSTONE_WALL.defaultBlockState() : Blocks.STONE_BRICK_WALL.defaultBlockState();
		level.setBlock(at.above(), post, 2);
		level.setBlock(at.above(2), post, 2);
		level.setBlock(at.above(3), Blocks.LANTERN.defaultBlockState(), 2);
	}

	/** At the village end, facing the village: which way the guild is. */
	private void signpost(WorldGenLevel level, BoundingBox box, BlockPos cell) {
		BlockPos at = ground(level, box, cell);
		if (at == null) return;
		BlockPos pos = at.above();
		level.setBlock(pos, Blocks.DARK_OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, RotationSegment.convertToSegment(toward)), 2);
		if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) return;
		SignText text = new SignText().setColor(DyeColor.BLACK)
				.setMessage(0, Component.translatable("guild.noclip_backrooms.road.1"))
				.setMessage(1, Component.translatable("guild.noclip_backrooms.name"))
				.setMessage(2, Component.translatable("guild.noclip_backrooms.road.3"));
		CompoundTag tag = new CompoundTag();
		SignText.DIRECT_CODEC.encodeStart(RegistryOps.create(NbtOps.INSTANCE, level.registryAccess()), text).ifSuccess(t -> tag.put("front_text", t));
		sign.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
	}
}
