package dev.noclip.backrooms.core.guild;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.noclip.backrooms.BackroomsMod;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The stone guilds beside the Overworld's villages are gone (docs/project/1-gameplay/README.md, 2026-11-26: M.E.G.'s
 * institute in the Frontrooms took their place). New worlds no longer get them; in older worlds each one is pulled down
 * as its chunks load: the hall to its floor, which turns back to grass (sand in the desert), the road's path and lamp
 * posts back to ground. Whatever players left in its chests drops where the chests stood; its people leave.
 */
public final class GuildRemoval {
	private GuildRemoval() {
	}

	public static void register() {
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
			if (level.dimension() != Level.OVERWORLD) return;
			MinecraftServer server = level.getServer();
			server.schedule(new TickTask(server.getTickCount(), () -> loaded(level, chunk)));
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (level.dimension() == Level.OVERWORLD && entity.entityTags().contains(Guilds.MEMBER_TAG)) entity.discard();
		});
	}

	private static void loaded(ServerLevel level, ChunkAccess chunk) {
		Data data = Data.get(level);
		Structure guild = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(BackroomsMod.id("guild"));
		if (guild != null) {
			StructureStart start = chunk.getStartForStructure(guild);
			long origin = chunk.getPos().pack();
			if (start != null && start.isValid() && !data.sites.containsKey(origin)) {
				List<Box> boxes = new ArrayList<>();
				boolean desert = false;
				for (var piece : start.getPieces()) {
					BoundingBox b = piece.getBoundingBox();
					boolean hall = piece instanceof GuildPiece;
					if (hall) desert = ((GuildPiece) piece).palette() == GuildPalette.DESERT;
					boxes.add(new Box(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ(), hall));
				}
				data.sites.put(origin, new Site(boxes, desert, new HashSet<>()));
				data.setDirty();
				// Its other chunks may be loaded already.
				for (Box box : boxes) {
					for (int cx = box.minX >> 4; cx <= box.maxX >> 4; cx++) {
						for (int cz = box.minZ >> 4; cz <= box.maxZ >> 4; cz++) {
							if (level.hasChunk(cx, cz)) clear(level, data, cx, cz);
						}
					}
				}
			}
		}
		clear(level, data, chunk.getPos().x(), chunk.getPos().z());
	}

	/** Pulls down whatever of a guild stands in this chunk, once. */
	private static void clear(ServerLevel level, Data data, int cx, int cz) {
		long key = ChunkPos.pack(cx, cz);
		for (Site site : data.sites.values()) {
			if (site.done.contains(key)) continue;
			boolean touches = false;
			for (Box box : site.boxes) {
				int x0 = Math.max(box.minX, cx << 4), x1 = Math.min(box.maxX, (cx << 4) + 15);
				int z0 = Math.max(box.minZ, cz << 4), z1 = Math.min(box.maxZ, (cz << 4) + 15);
				if (x0 > x1 || z0 > z1) continue;
				touches = true;
				if (box.hall) hall(level, box, x0, x1, z0, z1, site.desert);
				else road(level, box, x0, x1, z0, z1);
			}
			if (touches) {
				site.done.add(key);
				data.setDirty();
			}
		}
	}

	/** Pulls down a whole hall at once (for tests): everything over its floor goes, the floor turns to ground. */
	public static void demolish(ServerLevel level, BoundingBox b, boolean desert) {
		Box box = new Box(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ(), true);
		hall(level, box, b.minX(), b.maxX(), b.minZ(), b.maxZ(), desert);
	}

	private static void hall(ServerLevel level, Box box, int x0, int x1, int z0, int z1, boolean desert) {
		BlockState ground = desert ? Blocks.SAND.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				for (int y = box.maxY; y >= box.minY; y--) {
					pos.set(x, y, z);
					if (level.getBlockState(pos).isAir()) continue;
					spill(level, pos);
					level.setBlock(pos, y == box.minY ? ground : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
	}

	private static void road(ServerLevel level, Box box, int x0, int x1, int z0, int z1) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				for (int y = box.minY; y <= box.maxY; y++) {
					pos.set(x, y, z);
					BlockState state = level.getBlockState(pos);
					if (state.is(Blocks.DIRT_PATH)) {
						level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
					} else if (state.is(Blocks.SMOOTH_SANDSTONE) && level.getBlockState(pos.above()).isAir()) {
						level.setBlock(pos, Blocks.SAND.defaultBlockState(), Block.UPDATE_CLIENTS);
					} else if (state.is(Blocks.STONE_BRICK_WALL) || state.is(Blocks.SANDSTONE_WALL) || state.is(Blocks.LANTERN)
							|| state.is(BlockTags.ALL_SIGNS)) {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
		}
	}

	/** Anything players stored in the guild comes out where it was. */
	private static void spill(ServerLevel level, BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof Container container && !container.isEmpty()) {
			Containers.dropContents(level, pos, container);
		}
	}

	record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean hall) {
		static final Codec<Box> CODEC = Codec.INT.listOf().xmap(
				l -> new Box(l.get(0), l.get(1), l.get(2), l.get(3), l.get(4), l.get(5), l.get(6) != 0),
				b -> List.of(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, b.hall ? 1 : 0));
	}

	record Site(List<Box> boxes, boolean desert, Set<Long> done) {
		static final Codec<Site> CODEC = RecordCodecBuilder.create(i -> i.group(
				Box.CODEC.listOf().fieldOf("boxes").forGetter(Site::boxes),
				Codec.BOOL.optionalFieldOf("desert", false).forGetter(Site::desert),
				Codec.LONG.listOf().xmap(l -> (Set<Long>) new HashSet<>(l), s -> List.copyOf(s)).optionalFieldOf("done", Set.of()).forGetter(Site::done))
				.apply(i, (b, d, n) -> new Site(b, d, new HashSet<>(n))));
	}

	static final class Data extends SavedData {
		static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.unboundedMap(Codec.STRING, Site.CODEC).optionalFieldOf("sites", Map.of()).forGetter(d -> {
					Map<String, Site> out = new HashMap<>();
					d.sites.forEach((k, v) -> out.put(Long.toString(k), v));
					return out;
				})).apply(i, Data::new));
		static final SavedDataType<Data> TYPE = new SavedDataType<>(BackroomsMod.id("guild_removal"), Data::new, CODEC,
				DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
		final Map<Long, Site> sites = new HashMap<>();

		Data() {
		}

		Data(Map<String, Site> saved) {
			saved.forEach((k, v) -> sites.put(Long.parseLong(k), v));
		}

		static Data get(ServerLevel level) {
			return level.getDataStorage().computeIfAbsent(TYPE);
		}
	}
}
