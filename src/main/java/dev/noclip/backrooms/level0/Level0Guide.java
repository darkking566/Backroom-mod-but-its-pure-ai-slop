package dev.noclip.backrooms.level0;

import com.mojang.math.Transformation;
import dev.noclip.backrooms.core.story.StoryBlocks;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lays the first trip's cable in Level 0 (docs/企劃書/1-遊戲玩法/README.md, "第一趟：跟著纜線走"): along the route
 * GuidePlan finds from where a newcomer landed, with writing on the walls beside it, reels of cable now and then, what
 * three of the four researchers left behind, and the glowing door at the end. Laid in the world as it is, after
 * generation: Level 0's generator does not change, and Level0Mirror copies every block of it into the Stage's copies.
 */
public final class Level0Guide {
	private static final Logger LOGGER = LoggerFactory.getLogger(Level0Guide.class);
	public static final String WRITING_TAG = "noclip_guide_writing", RELIC_TAG = "noclip_guide_relic";
	private static final int FLOOR_Y = Level0ChunkGenerator.FLOOR_Y, STAND_Y = FLOOR_Y + 1;
	/** Where along the cable (fraction of its length) the researchers' things lie: notes 1 to 3, the helmet, the radio. */
	private static final double[] NOTES_AT = {0.22, 0.5, 0.78};
	private static final double HELMET_AT = 0.38, RADIO_AT = 0.64;
	/** Tries at a route if the world turns out to differ from the plan (the maze shifted, something was built). */
	private static final int TRIES = 3;
	private static final Direction[] SIDE_DIRECTION = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

	/** What the last cable laid had along it: its length in blocks, pieces of writing, and things left behind (for tests). */
	public record Laid(int cable, int writing, int relics) {
	}

	private static Laid last = new Laid(0, 0, 0);

	private Level0Guide() {
	}

	/**
	 * Lays a cable from {@code start} in Level 0, then tells {@code laid} where its door stands (lower half), or null if
	 * no route was found. The route is looked for off the server thread (it asks the maze about some 100,000 columns);
	 * the world is checked and changed on it.
	 */
	public static void lay(ServerLevel level0, BlockPos start, java.util.function.Consumer<BlockPos> laid) {
		long seed = Level0ChunkGenerator.mazeSeed(level0.getChunkSource().randomState());
		int sx = Level0Space.template(start.getX()), sz = Level0Space.template(start.getZ());
		attempt(level0, seed, sx, sz, new HashSet<>(), 0, System.nanoTime(), laid);
	}

	private static void attempt(ServerLevel level0, long seed, int sx, int sz, Set<Long> blocked, int tries, long began,
			java.util.function.Consumer<BlockPos> laid) {
		Set<Long> avoid = Set.copyOf(blocked);
		java.util.concurrent.CompletableFuture.supplyAsync(() -> GuidePlan.plan(ground(seed, avoid), sx, sz), net.minecraft.util.Util.backgroundExecutor())
				.whenCompleteAsync((plan, failure) -> {
					if (failure != null) LOGGER.error("Looking for the first trip's cable failed", failure);
					if (plan == null) {
						LOGGER.warn("No route for the first trip's cable from {} {}", sx, sz);
						laid.accept(null);
						return;
					}
					// The plan knows the maze as generated; the world may have been changed since. Check before laying anything.
					long wrong = firstDifference(level0, plan);
					if (wrong != Long.MIN_VALUE) {
						blocked.add(wrong);
						if (tries + 1 < TRIES) attempt(level0, seed, sx, sz, blocked, tries + 1, began, laid);
						else laid.accept(null);
						return;
					}
					place(level0, plan, seed ^ (((long) sx << 32) | (sz & 0xFFFFFFFFL)));
					LOGGER.info("Laid the first trip's cable: {} blocks from {} {} to {} {} ({} ms)", plan.path().size(), sx, sz,
							plan.doorX(), plan.doorZ(), (System.nanoTime() - began) / 1_000_000);
					laid.accept(new BlockPos(Level0Space.template(plan.doorX()), STAND_Y, Level0Space.template(plan.doorZ())));
				}, level0.getServer());
	}

	/** Level 0 as generated, less the columns found to differ from it. */
	static GuidePlan.Ground ground(long seed, Set<Long> blocked) {
		return new GuidePlan.Ground() {
			@Override
			public boolean walkable(int x, int z) {
				if (blocked.contains(key(x, z))) return false;
				int tx = Level0Space.template(x), tz = Level0Space.template(z);
				if (Maze.zoneAt(seed, tx, tz) == Maze.Zone.RED_ROOM || ManilaRoomPlan.roomAtBlock(seed, tx, tz) != null) return false;
				BlockState floor = Level0ChunkGenerator.worldBlockAt(seed, x, FLOOR_Y, z);
				return floor.isSolid() && Level0ChunkGenerator.worldBlockAt(seed, x, STAND_Y, z).isAir()
						&& Level0ChunkGenerator.worldBlockAt(seed, x, STAND_Y + 1, z).isAir();
			}

			@Override
			public boolean wall(int x, int z) {
				for (int y = STAND_Y; y <= STAND_Y + 2; y++) {
					if (!Level0ChunkGenerator.worldBlockAt(seed, x, y, z).isSolidRender()) return false;
				}
				return true;
			}
		};
	}

	/** The first column of the route where the world is not as planned, or Long.MIN_VALUE if there is none. */
	private static long firstDifference(ServerLevel level, GuidePlan.Plan plan) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int[] p : plan.path()) {
			if (!open(level, pos, p[0], p[1])) return key(p[0], p[1]);
		}
		if (!open(level, pos, plan.doorX(), plan.doorZ())) return key(plan.doorX(), plan.doorZ());
		return Long.MIN_VALUE;
	}

	private static boolean open(ServerLevel level, BlockPos.MutableBlockPos pos, int x, int z) {
		int tx = Level0Space.template(x), tz = Level0Space.template(z);
		level.getChunk(pos.set(tx, FLOOR_Y, tz));
		return level.getBlockState(pos.set(tx, FLOOR_Y, tz)).isSolid()
				&& level.getBlockState(pos.set(tx, STAND_Y, tz)).isAir()
				&& level.getBlockState(pos.set(tx, STAND_Y + 1, tz)).isAir();
	}

	private static void place(ServerLevel level, GuidePlan.Plan plan, long salt) {
		List<int[]> path = plan.path();
		BlockState cable = StoryBlocks.GUIDE_CABLE.defaultBlockState();
		for (int i = 0; i < path.size(); i++) {
			int[] p = path.get(i);
			int[] next = i + 1 < path.size() ? path.get(i + 1) : new int[] {plan.doorX(), plan.doorZ()};
			Direction ahead = toward(p, next), behind = i > 0 ? toward(p, path.get(i - 1)) : null;
			level.setBlock(at(p[0], STAND_Y, p[1]), cable.setValue(StoryBlocks.Cable.RUN, StoryBlocks.Run.joining(ahead, behind)), Block.UPDATE_CLIENTS);
		}
		for (int[] reel : plan.reels()) level.setBlock(at(reel[0], STAND_Y, reel[1]), dev.noclip.backrooms.level2.Level2Blocks.CABLE_REEL.defaultBlockState(), Block.UPDATE_CLIENTS);

		Direction face = SIDE_DIRECTION[(plan.doorBack() + 2) % 4];
		BlockState door = StoryBlocks.GUIDE_DOOR.defaultBlockState().setValue(StoryBlocks.Door.FACING, face);
		level.setBlock(at(plan.doorX(), STAND_Y, plan.doorZ()), door.setValue(StoryBlocks.Door.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_ALL);
		level.setBlock(at(plan.doorX(), STAND_Y + 1, plan.doorZ()), door.setValue(StoryBlocks.Door.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);

		java.util.Random random = new java.util.Random(salt);
		List<GuidePlan.Face> walls = plan.writing();
		List<String> lines = GuidePlan.lines(walls.size(), random);
		for (int i = 0; i < lines.size(); i++) write(level, walls.get(i), lines.get(i), lines.size() < 2 ? 1.0 : i / (double) (lines.size() - 1), random);

		for (int n = 0; n < NOTES_AT.length; n++) drop(level, along(path, NOTES_AT[n]), note(n));
		drop(level, along(path, HELMET_AT), helmet());
		drop(level, along(path, RADIO_AT), new ItemStack(dev.noclip.backrooms.core.economy.Economy.MEG_RADIO));
		last = new Laid(path.size(), lines.size(), NOTES_AT.length + 2);
	}

	public static Laid last() {
		return last;
	}

	/**
	 * Writing on a wall, as a text display flat on its face. Near the landing it is scrawled in dried red, crooked and
	 * shaky; toward the door it is straight and in black marker, as if whoever wrote it had calmed down.
	 */
	private static void write(ServerLevel level, GuidePlan.Face wall, String line, double along, java.util.Random random) {
		int[] out = GuidePlan.side(wall.side());
		Direction facing = SIDE_DIRECTION[wall.side()];
		double x = Level0Space.template(wall.x()) + 0.5 + out[0] * 0.52, z = Level0Space.template(wall.z()) + 0.5 + out[1] * 0.52;
		double y = STAND_Y + 1.15 + (random.nextDouble() - 0.5) * 0.5 * (1 - along);
		float tilt = (float) Math.toRadians((random.nextDouble() * 2 - 1) * 14 * (1 - along));
		float scale = (float) (1.15 + 0.35 * (1 - along) * random.nextDouble());
		Display.TextDisplay text = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, level);
		int colour = along < 0.5 ? 0x6B1C14 : 0x1E1E1E;
		text.setText(Component.translatable("graffiti.noclip_backrooms." + line).withColor(colour));
		text.setBackgroundColor(0);
		text.setLineWidth(80);
		text.setBillboardConstraints(Display.BillboardConstraints.FIXED);
		text.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateZ(tilt), new Vector3f(scale, scale, scale), new Quaternionf()));
		text.snapTo(x, y, z, facing.toYRot(), 0);
		text.addTag(WRITING_TAG);
		level.addFreshEntity(text);
	}

	private static void drop(ServerLevel level, int[] p, ItemStack stack) {
		ItemEntity item = new ItemEntity(level, Level0Space.template(p[0]) + 0.5, STAND_Y + 0.1, Level0Space.template(p[1]) + 0.5, stack, 0, 0, 0);
		item.setUnlimitedLifetime();
		item.addTag(RELIC_TAG);
		level.addFreshEntity(item);
	}

	/** One of the three researchers' notes (the fourth's is never found). */
	static ItemStack note(int n) {
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		String key = "book.noclip_backrooms.cable_note." + n;
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
				Filterable.passThrough(Component.translatable(key + ".title").getString()), "M.E.G.", 0,
				List.of(Filterable.passThrough(Component.translatable(key + ".page1")), Filterable.passThrough(Component.translatable(key + ".page2"))),
				true));
		book.set(DataComponents.ITEM_NAME, Component.translatable(key + ".title"));
		return book;
	}

	private static ItemStack helmet() {
		ItemStack helmet = new ItemStack(Items.LEATHER_HELMET);
		helmet.set(DataComponents.DYED_COLOR, new DyedItemColor(0xD9A520));
		helmet.set(DataComponents.ITEM_NAME, Component.translatable("item.noclip_backrooms.researcher_helmet"));
		return helmet;
	}

	private static int[] along(List<int[]> path, double fraction) {
		return path.get(Math.min(path.size() - 1, (int) Math.round(fraction * (path.size() - 1))));
	}

	private static Direction toward(int[] from, int[] to) {
		int dx = Integer.signum(to[0] - from[0]), dz = Integer.signum(to[1] - from[1]);
		return dx > 0 ? Direction.EAST : dx < 0 ? Direction.WEST : dz > 0 ? Direction.SOUTH : Direction.NORTH;
	}

	private static BlockPos at(int x, int y, int z) {
		return new BlockPos(Level0Space.template(x), y, Level0Space.template(z));
	}

	private static long key(int x, int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}

	/** Whether a maze cell, or the ring of blocks round it, has the first trip's cable on it: Edge Shift leaves it be. */
	static boolean cabled(ServerLevel level, int minX, int minZ) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = minX - 2; x < minX + Maze.CELL + 2; x++) {
			for (int z = minZ - 2; z < minZ + Maze.CELL + 2; z++) {
				pos.set(x, STAND_Y, z);
				if (!level.isLoaded(pos)) continue;
				BlockState state = level.getBlockState(pos);
				if (state.is(StoryBlocks.GUIDE_CABLE) || state.is(StoryBlocks.GUIDE_DOOR)) return true;
			}
		}
		return false;
	}
}
