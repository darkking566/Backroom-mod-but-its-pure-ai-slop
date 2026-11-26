package dev.noclip.backrooms.core.trip;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.props.BackroomsProps;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Persistent Overworld storage for reversible Backrooms Seepage (natural ground blocks temporarily
 * turned into damp yellow Backrooms carpet) and per-player glowing Backrooms Rift entrances.
 *
 * <p>NEVER alters player-built structures, wood planks, stone bricks, beds, chests, or any block
 * entity — only whitelisted natural ground blocks with clear air above, and records the exact
 * original {@link BlockState} so every block restores cleanly when stabilized.
 */
public final class SeepageData extends SavedData {
	public static final int MAX_BLOCKS_PER_PLAYER = 48;

	private static final Set<Block> NATURAL_GROUND = Set.of(
			Blocks.GRASS_BLOCK,
			Blocks.DIRT,
			Blocks.COARSE_DIRT,
			Blocks.ROOTED_DIRT,
			Blocks.PODZOL,
			Blocks.MYCELIUM,
			Blocks.SAND,
			Blocks.RED_SAND,
			Blocks.GRAVEL,
			Blocks.STONE,
			Blocks.ANDESITE,
			Blocks.DIORITE,
			Blocks.GRANITE,
			Blocks.DEEPSLATE,
			Blocks.TUFF,
			Blocks.SNOW_BLOCK,
			Blocks.MUD,
			Blocks.CLAY,
			Blocks.SANDSTONE,
			Blocks.RED_SANDSTONE
	);

	public record SeepedBlock(long pos, String owner, BlockState original) {
		static final Codec<SeepedBlock> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.fieldOf("pos").forGetter(SeepedBlock::pos),
				Codec.STRING.fieldOf("owner").forGetter(SeepedBlock::owner),
				BlockState.CODEC.fieldOf("original").forGetter(SeepedBlock::original)
		).apply(i, SeepedBlock::new));
	}

	public record RiftRecord(String owner, long pos) {
		static final Codec<RiftRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("owner").forGetter(RiftRecord::owner),
				Codec.LONG.fieldOf("pos").forGetter(RiftRecord::pos)
		).apply(i, RiftRecord::new));
	}

	private static final Codec<SeepageData> CODEC = RecordCodecBuilder.create(i -> i.group(
			SeepedBlock.CODEC.listOf().optionalFieldOf("blocks", List.of()).forGetter(SeepageData::serializeBlocks),
			RiftRecord.CODEC.listOf().optionalFieldOf("rifts", List.of()).forGetter(SeepageData::serializeRifts)
	).apply(i, SeepageData::fromSerialized));

	public static final SavedDataType<SeepageData> TYPE = new SavedDataType<>(
			BackroomsMod.id("overworld_seepage"),
			SeepageData::new,
			CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Map<Long, SeepedBlock> blocks = new HashMap<>();
	private final Map<String, Long> rifts = new HashMap<>();

	public SeepageData() {
	}

	public static SeepageData get(ServerLevel overworld) {
		return overworld.getDataStorage().computeIfAbsent(TYPE);
	}

	/** Strict whitelist check: ONLY natural ground blocks (never player buildings, containers, or beds). */
	public static boolean isSeepableGround(BlockState state) {
		return NATURAL_GROUND.contains(state.getBlock());
	}

	private static Block carpetBlock() {
		Block b = BuiltInRegistries.BLOCK.getValue(BackroomsMod.id("carpet"));
		return b == Blocks.AIR ? Blocks.WOOL.yellow() : b;
	}

	public int countFor(UUID owner) {
		String id = owner.toString();
		int c = 0;
		for (SeepedBlock sb : blocks.values()) {
			if (sb.owner.equals(id)) c++;
		}
		return c;
	}

	public BlockPos riftFor(UUID owner) {
		Long packed = rifts.get(owner.toString());
		return packed == null ? null : BlockPos.of(packed);
	}

	/** Attempts to seep 1 natural surface block near {@code center} into damp yellow Backrooms carpet. */
	public BlockPos trySeepNear(ServerLevel overworld, UUID owner, BlockPos center, int minRadius, int maxRadius, RandomSource random) {
		if (countFor(owner) >= MAX_BLOCKS_PER_PLAYER) return null;
		Block carpet = carpetBlock();
		String id = owner.toString();

		for (int attempt = 0; attempt < 24; attempt++) {
			int dx = random.nextInt(maxRadius * 2 + 1) - maxRadius;
			int dz = random.nextInt(maxRadius * 2 + 1) - maxRadius;
			int distSq = dx * dx + dz * dz;
			if (distSq < minRadius * minRadius || distSq > maxRadius * maxRadius) continue;

			int tx = center.getX() + dx;
			int tz = center.getZ() + dz;
			// Search vertically near player's elevation so we seep around their yard/camp
			for (int dy = 3; dy >= -4; dy--) {
				BlockPos pos = new BlockPos(tx, center.getY() + dy, tz);
				if (!overworld.isLoaded(pos)) continue;
				long key = pos.asLong();
				if (blocks.containsKey(key)) break;

				BlockState state = overworld.getBlockState(pos);
				if (!isSeepableGround(state)) continue;
				if (overworld.getBlockEntity(pos) != null) continue;
				// Require clear air directly above (never touch ground under chests, beds, doors, or walls!)
				BlockPos above = pos.above();
				BlockState aboveState = overworld.getBlockState(above);
				if (!aboveState.isAir() || overworld.getBlockEntity(above) != null) continue;

				overworld.setBlock(pos, carpet.defaultBlockState(), Block.UPDATE_ALL);
				blocks.put(key, new SeepedBlock(key, id, state));
				setDirty();
				return pos;
			}
		}
		return null;
	}

	/** Ensures a glowing Backrooms Rift exists in open ground near {@code center} so the player can step back in. */
	public BlockPos ensureRiftNear(ServerLevel overworld, UUID owner, BlockPos center, RandomSource random) {
		String id = owner.toString();
		Long existing = rifts.get(id);
		if (existing != null) {
			BlockPos oldPos = BlockPos.of(existing);
			if (overworld.isLoaded(oldPos) && overworld.getBlockState(oldPos).is(BackroomsProps.BACKROOMS_RIFT)
					&& oldPos.closerThan(center, 48.0)) {
				return oldPos;
			}
			clearRift(overworld, owner);
		}

		for (int attempt = 0; attempt < 40; attempt++) {
			int dx = random.nextInt(25) - 12;
			int dz = random.nextInt(25) - 12;
			int distSq = dx * dx + dz * dz;
			if (distSq < 36 || distSq > 256) continue; // 6..16 blocks away

			int tx = center.getX() + dx;
			int tz = center.getZ() + dz;
			for (int dy = 4; dy >= -4; dy--) {
				BlockPos pos = new BlockPos(tx, center.getY() + dy, tz);
				if (!overworld.isLoaded(pos)) continue;
				BlockState below = overworld.getBlockState(pos.below());
				if (below.getCollisionShape(overworld, pos.below()).isEmpty()) continue;
				if (!overworld.getBlockState(pos).isAir() || !overworld.getBlockState(pos.above()).isAir()) continue;
				// Outdoors only: never inside the player's house or a cave.
				if (!overworld.canSeeSky(pos)) continue;

				overworld.setBlock(pos, BackroomsProps.BACKROOMS_RIFT.defaultBlockState(), Block.UPDATE_ALL);
				rifts.put(id, pos.asLong());
				setDirty();
				return pos;
			}
		}
		return null;
	}

	/** Restores up to {@code maxCount} seeped blocks owned by {@code owner} (nearest first) back to their exact original BlockState. */
	public int restoreFor(ServerLevel overworld, UUID owner, BlockPos center, int maxCount) {
		String id = owner.toString();
		List<SeepedBlock> owned = new ArrayList<>();
		for (SeepedBlock sb : blocks.values()) {
			if (sb.owner.equals(id)) owned.add(sb);
		}
		if (owned.isEmpty()) return 0;
		owned.sort(Comparator.comparingDouble(sb -> BlockPos.of(sb.pos).distSqr(center)));

		Block carpet = carpetBlock();
		int restored = 0;
		for (SeepedBlock sb : owned) {
			if (restored >= maxCount) break;
			BlockPos pos = BlockPos.of(sb.pos);
			// Reading the block loads its chunk if the player is far away: the record is dropped below, so this is the
			// only chance to put the ground back.
			if (overworld.getBlockState(pos).is(carpet)) {
				overworld.setBlock(pos, sb.original, Block.UPDATE_ALL);
			}
			blocks.remove(sb.pos);
			restored++;
		}
		if (restored > 0) setDirty();
		return restored;
	}

	/** Restores ALL seeped ground blocks AND closes the player's Backrooms Rift cleanly. */
	public int clearAllFor(ServerLevel overworld, UUID owner, BlockPos center) {
		int restored = restoreFor(overworld, owner, center, Integer.MAX_VALUE);
		clearRift(overworld, owner);
		return restored;
	}

	public boolean clearRift(ServerLevel overworld, UUID owner) {
		Long packed = rifts.remove(owner.toString());
		if (packed == null) return false;
		BlockPos pos = BlockPos.of(packed);
		// Loads the chunk if needed, so no rift is ever left behind once its record is gone.
		if (overworld.getBlockState(pos).is(BackroomsProps.BACKROOMS_RIFT)) {
			overworld.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
		setDirty();
		return true;
	}

	/** Removes stale records if a player manually broke a seeped carpet block. */
	public void pruneIfBroken(ServerLevel overworld, BlockPos pos) {
		if (blocks.remove(pos.asLong()) != null) {
			setDirty();
		}
	}

	private List<SeepedBlock> serializeBlocks() {
		return new ArrayList<>(blocks.values());
	}

	private List<RiftRecord> serializeRifts() {
		List<RiftRecord> out = new ArrayList<>(rifts.size());
		for (var e : rifts.entrySet()) {
			out.add(new RiftRecord(e.getKey(), e.getValue()));
		}
		return out;
	}

	private static SeepageData fromSerialized(List<SeepedBlock> rawBlocks, List<RiftRecord> rawRifts) {
		SeepageData data = new SeepageData();
		for (SeepedBlock sb : rawBlocks) {
			data.blocks.put(sb.pos, sb);
		}
		for (RiftRecord rr : rawRifts) {
			data.rifts.put(rr.owner, rr.pos);
		}
		return data;
	}
}
