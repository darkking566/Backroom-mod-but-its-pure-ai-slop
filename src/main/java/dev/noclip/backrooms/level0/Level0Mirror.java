package dev.noclip.backrooms.level0;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.noclip.backrooms.BackroomsMod;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Keeps every copy of Level 0's Stage the same as the Stage (Level0Space):
 * <ul>
 * <li>a block changed anywhere (a wall broken, a block placed, a lamp put out) is changed in every loaded copy at
 * once, and remembered, so a copy loaded later gets it too;</li>
 * <li>anything that leaves the centre Stage (a monster chasing someone over the edge, a dropped item, an arrow) is
 * moved back by whole Stages, as players are by StageLoop.</li>
 * </ul>
 * What is drawn of the copies' living things (players, monsters, items) on the other side of an edge is the client's
 * business (level0.Level0Ghosts).
 */
public final class Level0Mirror {
	/** Copies a Stage around the centre Stage, each way: the whole region (Level0Space.REGION). */
	private static final int REACH = Level0Space.REGION / 2;
	private static final ThreadLocal<Boolean> MIRRORING = ThreadLocal.withInitial(() -> false);

	private Level0Mirror() {
	}

	static void register() {
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
			if (level.dimension() == Level0.LEVEL_0) replay(level, chunk);
		});
	}

	/** A block changed in Level 0 (Level.setBlock, through mixin.LevelSetBlockMirror). */
	public static void changed(ServerLevel level, BlockPos pos, BlockState state) {
		if (level.dimension() != Level0.LEVEL_0 || MIRRORING.get()) return;
		int tx = Level0Space.template(pos.getX()), tz = Level0Space.template(pos.getZ());
		Changes.of(level).put(tx, pos.getY(), tz, state);
		MIRRORING.set(true);
		try {
			BlockPos.MutableBlockPos copy = new BlockPos.MutableBlockPos();
			for (int kx = -REACH; kx <= REACH; kx++) {
				for (int kz = -REACH; kz <= REACH; kz++) {
					copy.set(tx + kx * Level0Space.PERIOD, pos.getY(), tz + kz * Level0Space.PERIOD);
					if (copy.equals(pos) || !level.hasChunk(copy.getX() >> 4, copy.getZ() >> 4)) continue;
					if (level.getBlockState(copy).equals(state)) continue;
					// Exactly the same block, without the neighbour updates (those come through here too, from the original).
					level.setBlock(copy, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				}
			}
		} finally {
			MIRRORING.set(false);
		}
	}

	/** A Level 0 chunk loaded: put back every change made to the Stage at the blocks it holds. */
	private static void replay(ServerLevel level, LevelChunk chunk) {
		Changes changes = Changes.of(level);
		if (changes.isEmpty()) return;
		int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
		int baseX = Level0Space.template(minX), baseZ = Level0Space.template(minZ);
		for (long key : changes.chunksCovering(baseX, baseZ)) {
			for (Changes.Change change : changes.inChunk(key)) {
				int dx = Math.floorMod(change.x() - baseX, Level0Space.PERIOD), dz = Math.floorMod(change.z() - baseZ, Level0Space.PERIOD);
				if (dx > 15 || dz > 15) continue;
				BlockPos at = new BlockPos(minX + dx, change.y(), minZ + dz);
				if (!chunk.getBlockState(at).equals(change.state())) chunk.setBlockState(at, change.state(), 0);
			}
		}
	}

	/** Moves anything (but players, who StageLoop moves) that has left the centre Stage back into it. */
	static void keepThingsIn(ServerLevel level) {
		List<Entity> out = new ArrayList<>();
		for (Entity entity : level.getAllEntities()) {
			if (entity instanceof ServerPlayer || entity.isPassenger()) continue;
			int bx = Mth.floor(entity.getX()), bz = Mth.floor(entity.getZ());
			if (Level0Space.template(bx) != bx || Level0Space.template(bz) != bz) out.add(entity);
		}
		for (Entity entity : out) {
			int bx = Mth.floor(entity.getX()), bz = Mth.floor(entity.getZ());
			entity.teleportTo(level, entity.getX() + Level0Space.template(bx) - bx, entity.getY(), entity.getZ() + Level0Space.template(bz) - bz,
					Relative.union(Relative.DELTA, Relative.ROTATION), 0.0F, 0.0F, false);
		}
	}

	/** Every block changed in Level 0's Stages, at its place in the centre Stage, kept with the world. */
	static final class Changes extends SavedData {
		record Change(int x, int y, int z, BlockState state) {
			static final Codec<Change> CODEC = RecordCodecBuilder.create(i -> i.group(
					Codec.INT.fieldOf("x").forGetter(Change::x), Codec.INT.fieldOf("y").forGetter(Change::y),
					Codec.INT.fieldOf("z").forGetter(Change::z), BlockState.CODEC.fieldOf("state").forGetter(Change::state)).apply(i, Change::new));
		}

		static final Codec<Changes> CODEC = Change.CODEC.listOf().optionalFieldOf("changes", List.of())
				.xmap(Changes::new, Changes::all).codec();
		static final SavedDataType<Changes> TYPE = new SavedDataType<>(BackroomsMod.id("level0_mirror"), Changes::new, CODEC,
				DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

		/** By the centre Stage's chunk, then by block. */
		private final Long2ObjectOpenHashMap<Map<Long, Change>> byChunk = new Long2ObjectOpenHashMap<>();

		Changes() {
		}

		private Changes(List<Change> changes) {
			for (Change change : changes) store(change);
		}

		static Changes of(ServerLevel level) {
			return level.getDataStorage().computeIfAbsent(TYPE);
		}

		boolean isEmpty() {
			return byChunk.isEmpty();
		}

		void put(int x, int y, int z, BlockState state) {
			store(new Change(x, y, z, state));
			setDirty();
		}

		private void store(Change change) {
			byChunk.computeIfAbsent(ChunkPos.pack(change.x() >> 4, change.z() >> 4), k -> new HashMap<>())
					.put(BlockPos.asLong(change.x(), change.y(), change.z()), change);
		}

		Iterable<Change> inChunk(long key) {
			Map<Long, Change> changes = byChunk.get(key);
			return changes == null ? List.of() : changes.values();
		}

		/** The centre Stage's chunks that a 16 x 16 chunk starting at (baseX, baseZ) of the Stage overlaps (wrapping at its edge). */
		List<Long> chunksCovering(int baseX, int baseZ) {
			List<Long> keys = new ArrayList<>(4);
			for (int x : new int[] {baseX, wrap(baseX + 15)}) {
				for (int z : new int[] {baseZ, wrap(baseZ + 15)}) {
					long key = ChunkPos.pack(x >> 4, z >> 4);
					if (!keys.contains(key)) keys.add(key);
				}
			}
			// The chunk may straddle a Stage chunk boundary in the middle too.
			for (int x : new int[] {baseX, wrap(baseX + 8), wrap(baseX + 15)}) {
				for (int z : new int[] {baseZ, wrap(baseZ + 8), wrap(baseZ + 15)}) {
					long key = ChunkPos.pack(x >> 4, z >> 4);
					if (!keys.contains(key)) keys.add(key);
				}
			}
			return keys;
		}

		private static int wrap(int coord) {
			return Level0Space.template(coord);
		}

		private List<Change> all() {
			List<Change> out = new ArrayList<>();
			for (Map<Long, Change> changes : byChunk.values()) out.addAll(changes.values());
			return out;
		}
	}
}
