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
 * moved back by whole Stages, as players are by StageLoop;</li>
 * <li>what a block entity holds (a chest's things, a sign's words) is copied to the same block in every loaded copy
 * when it changes, and remembered for copies loaded later (mixin.BlockEntityMirror);</li>
 * <li>sounds and particles near one edge of the Stage are played again where the copy across the other edge shows the
 * same place, for whoever is near it (mixin.ServerLevelMirrorEffects).</li>
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
			if (level.dimension() != Level0.LEVEL_0) return;
			replay(level, chunk);
			replayContents(level, chunk);
		});
	}

	/** Whether sounds and particles in this level are played again in the Stage's copies. */
	public static boolean echoes(ServerLevel level) {
		return level.dimension() == Level0.LEVEL_0;
	}

	/**
	 * The copies of (x, z) one Stage over, each way, that someone is within {@code range} of: where a sound or particles
	 * at (x, z) would also be heard or seen, across an edge.
	 */
	public static List<net.minecraft.world.phys.Vec3> copiesHeard(ServerLevel level, double x, double z, double range) {
		List<net.minecraft.world.phys.Vec3> out = new ArrayList<>();
		for (int kx = -1; kx <= 1; kx++) {
			for (int kz = -1; kz <= 1; kz++) {
				if (kx == 0 && kz == 0) continue;
				double cx = x + kx * Level0Space.PERIOD, cz = z + kz * Level0Space.PERIOD;
				for (ServerPlayer player : level.players()) {
					double dx = player.getX() - cx, dz = player.getZ() - cz;
					if (dx * dx + dz * dz <= range * range) {
						out.add(new net.minecraft.world.phys.Vec3(cx, 0, cz));
						break;
					}
				}
			}
		}
		return out;
	}

	/** A block entity's contents changed in Level 0 (BlockEntity.setChanged, through mixin.BlockEntityMirror). */
	public static void contentsChanged(ServerLevel level, net.minecraft.world.level.block.entity.BlockEntity changed) {
		if (level.dimension() != Level0.LEVEL_0 || MIRRORING.get()) return;
		BlockPos pos = changed.getBlockPos();
		var registries = level.registryAccess();
		net.minecraft.nbt.CompoundTag data = changed.saveWithoutMetadata(registries);
		int tx = Level0Space.template(pos.getX()), tz = Level0Space.template(pos.getZ());
		Contents.of(level).put(tx, pos.getY(), tz, data);
		MIRRORING.set(true);
		try {
			BlockPos.MutableBlockPos copy = new BlockPos.MutableBlockPos();
			for (int kx = -REACH; kx <= REACH; kx++) {
				for (int kz = -REACH; kz <= REACH; kz++) {
					copy.set(tx + kx * Level0Space.PERIOD, pos.getY(), tz + kz * Level0Space.PERIOD);
					if (copy.equals(pos) || !level.hasChunk(copy.getX() >> 4, copy.getZ() >> 4)) continue;
					fill(level, copy.immutable(), changed.getType(), data);
				}
			}
		} finally {
			MIRRORING.set(false);
		}
	}

	/** Gives the block entity at {@code at}, if it is one of the same kind, what {@code data} says it holds. */
	private static void fill(ServerLevel level, BlockPos at, net.minecraft.world.level.block.entity.BlockEntityType<?> type,
			net.minecraft.nbt.CompoundTag data) {
		net.minecraft.world.level.block.entity.BlockEntity entity = level.getBlockEntity(at);
		if (entity == null || entity.getType() != type) return;
		entity.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(
				net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), data));
		entity.setChanged();
		BlockState state = level.getBlockState(at);
		level.sendBlockUpdated(at, state, state, Block.UPDATE_CLIENTS);
	}

	/** A Level 0 chunk loaded: give its block entities what their originals hold now. */
	private static void replayContents(ServerLevel level, LevelChunk chunk) {
		Contents contents = Contents.of(level);
		if (contents.held.isEmpty()) return;
		boolean dirty = false;
		for (var entity : List.copyOf(chunk.getBlockEntities().values())) {
			BlockPos pos = entity.getBlockPos();
			net.minecraft.nbt.CompoundTag data = contents.held.get(BlockPos.asLong(Level0Space.template(pos.getX()), pos.getY(),
					Level0Space.template(pos.getZ())));
			if (data == null) continue;
			MIRRORING.set(true);
			try {
				entity.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(
						net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), data));
				dirty = true;
			} finally {
				MIRRORING.set(false);
			}
		}
		// Mark the chunk directly: BlockEntity.setChanged() calls level.getChunkAt(), which deadlocks inside CHUNK_LOAD.
		if (dirty) chunk.markUnsaved();
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
		MIRRORING.set(true);
		try {
			for (long key : changes.chunksCovering(baseX, baseZ)) {
				for (Changes.Change change : changes.inChunk(key)) {
					int dx = Math.floorMod(change.x() - baseX, Level0Space.PERIOD), dz = Math.floorMod(change.z() - baseZ, Level0Space.PERIOD);
					if (dx > 15 || dz > 15) continue;
					BlockPos at = new BlockPos(minX + dx, change.y(), minZ + dz);
					if (!chunk.getBlockState(at).equals(change.state())) chunk.setBlockState(at, change.state(), 0);
				}
			}
		} finally {
			MIRRORING.set(false);
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

	/** What each changed block entity of Level 0's Stages holds, at its place in the centre Stage, kept with the world. */
	static final class Contents extends SavedData {
		record Held(int x, int y, int z, net.minecraft.nbt.CompoundTag data) {
			static final Codec<Held> CODEC = RecordCodecBuilder.create(i -> i.group(
					Codec.INT.fieldOf("x").forGetter(Held::x), Codec.INT.fieldOf("y").forGetter(Held::y),
					Codec.INT.fieldOf("z").forGetter(Held::z), net.minecraft.nbt.CompoundTag.CODEC.fieldOf("data").forGetter(Held::data))
					.apply(i, Held::new));
		}

		static final Codec<Contents> CODEC = Held.CODEC.listOf().optionalFieldOf("contents", List.of())
				.xmap(Contents::new, Contents::all).codec();
		static final SavedDataType<Contents> TYPE = new SavedDataType<>(BackroomsMod.id("level0_mirror_contents"), Contents::new, CODEC,
				DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
		final Map<Long, net.minecraft.nbt.CompoundTag> held = new HashMap<>();

		Contents() {
		}

		private Contents(List<Held> all) {
			for (Held h : all) held.put(BlockPos.asLong(h.x(), h.y(), h.z()), h.data());
		}

		static Contents of(ServerLevel level) {
			return level.getDataStorage().computeIfAbsent(TYPE);
		}

		void put(int x, int y, int z, net.minecraft.nbt.CompoundTag data) {
			held.put(BlockPos.asLong(x, y, z), data);
			setDirty();
		}

		private List<Held> all() {
			List<Held> out = new ArrayList<>();
			for (var e : held.entrySet()) {
				BlockPos p = BlockPos.of(e.getKey());
				out.add(new Held(p.getX(), p.getY(), p.getZ(), e.getValue()));
			}
			return out;
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
