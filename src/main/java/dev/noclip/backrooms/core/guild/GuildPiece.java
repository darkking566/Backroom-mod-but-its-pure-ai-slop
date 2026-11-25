package dev.noclip.backrooms.core.guild;

import dev.noclip.backrooms.core.people.People;
import dev.noclip.backrooms.core.people.Person;
import dev.noclip.backrooms.core.trade.ShopRole;
import dev.noclip.backrooms.props.BackroomsProps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * The guild hall. A two-storey stone hall (24 by 18) under a hipped roof with dormers and two chimneys, one sending up
 * a column of smoke that can be seen from the village; in front, a gate tower with a pyramid roof, a double door under
 * an arch and the guild's name; either side of the tower a colonnade, and flower beds before it.
 *
 * <p>Inside, through the tower: the buyer and the supplier at counters on one side, the quartermaster and the Backrooms
 * workstations on the other, the guild master's desk and notice board at the back, beds and chests by the door.
 *
 * <p>Drawn in the piece's own frame: x 0..29 across, z 0..26 from the front (the side facing the village) to the back,
 * y 0 the ground floor. Directions here are as for a piece facing north (the front is south, +x is east, +z is north);
 * the piece turns them to its real facing. The hall's furniture is drawn in the hall's frame, OX and OZ in from that.
 */
public final class GuildPiece extends StructurePiece {
	static final int WIDTH = 30, DEPTH = 27, HEIGHT = 24;
	/** The door's left cell (the door is x 14 and 15 of the tower's front). */
	static final int DOOR_X = 14;
	/** The hall's own frame (its furniture) starts here. */
	private static final int OX = 2, OZ = 7;
	/** The hall's walls; their top. */
	private static final int X0 = 3, X1 = 26, Z0 = 8, Z1 = 25, TOP = 8;
	/** The gate tower's walls; their top. */
	private static final int TX0 = 11, TX1 = 18, TZ0 = 1, TOWER_TOP = 16;
	private final GuildPalette palette;
	private int spawned;

	GuildPiece(int minX, int y, int minZ, Direction facing, GuildPalette palette) {
		super(Guilds.HALL, 0, makeBoundingBox(minX, y, minZ, facing, WIDTH, HEIGHT, DEPTH));
		setOrientation(facing);
		this.palette = palette;
	}

	GuildPiece(CompoundTag tag) {
		super(Guilds.HALL, tag);
		GuildPalette[] all = GuildPalette.values();
		palette = all[Math.clamp(tag.getIntOr("Palette", 0), 0, all.length - 1)];
		spawned = tag.getIntOr("Spawned", 0);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putInt("Palette", palette.ordinal());
		tag.putInt("Spawned", spawned);
	}

	/**
	 * A hall whose door's left cell, at the front edge of its yard, is (fx, fz), standing on the side {@code side} of
	 * whoever looks at it: the door faces back the other way.
	 */
	static GuildPiece facingFront(int fx, int floorY, int fz, Direction side, GuildPalette palette) {
		int d = DEPTH - 1;
		int minX, minZ;
		switch (side) {
			case NORTH -> { minX = fx - DOOR_X; minZ = fz - d; }
			case SOUTH -> { minX = fx - DOOR_X; minZ = fz; }
			case WEST -> { minX = fx - d; minZ = fz - DOOR_X; }
			default -> { minX = fx; minZ = fz - DOOR_X; }
		}
		return new GuildPiece(minX, floorY, minZ, side, palette);
	}

	/** Builds a hall at once in a live world, its door toward {@code side}'s opposite; for /backrooms guild place and tests. */
	public static GuildPiece placeNow(ServerLevel level, BlockPos front, Direction side, GuildPalette palette) {
		GuildPiece hall = facingFront(front.getX(), front.getY(), front.getZ(), side, palette);
		BoundingBox b = hall.getBoundingBox();
		BoundingBox all = new BoundingBox(b.minX(), level.getMinY() + 1, b.minZ(), b.maxX(), level.getMaxY(), b.maxZ());
		hall.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), level.getRandom(), all,
				ChunkPos.containing(b.getCenter()), b.getCenter());
		return hall;
	}

	GuildPalette palette() {
		return palette;
	}

	/** Where a cell of the piece's frame (see the class comment) is in the world; for tests. */
	public BlockPos at(int x, int y, int z) {
		return getWorldPos(x, y, z).immutable();
	}

	/** Where a cell of the piece's frame is in the world (x and z only). */
	int worldX(int x, int z) {
		return getWorldX(x, z);
	}

	int worldZ(int x, int z) {
		return getWorldZ(x, z);
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
			BoundingBox box, ChunkPos chunk, BlockPos pivot) {
		generateAirBox(level, box, 0, 1, 0, WIDTH - 1, HEIGHT - 1, DEPTH - 1);
		ground(level, box);
		hallWalls(level, box);
		hallRoof(level, box);
		tower(level, box);
		colonnades(level, box);
		yard(level, box);
		lights(level, box);
		furnish(level, box);
		people(level, box);
	}

	private boolean inTower(int x, int z) {
		return x >= TX0 && x <= TX1 && z >= TZ0 && z <= Z0;
	}

	/** The ground floor everywhere, on a foundation down to the ground: floors inside, paths and flower beds outside. */
	private void ground(WorldGenLevel level, BoundingBox box) {
		GuildPalette p = palette;
		for (int x = X0 - 1; x <= X1 + 1; x++) {
			for (int z = 0; z <= Z1 + 1; z++) {
				boolean hall = x >= X0 && x <= X1 && z >= Z0 && z <= Z1;
				boolean hallEdge = hall && (x == X0 || x == X1 || z == Z0 || z == Z1);
				boolean tower = inTower(x, z) && z < Z0;
				boolean towerEdge = tower && (x == TX0 || x == TX1 || z == TZ0);
				boolean porch = !tower && z >= 4 && z < Z0 && x >= X0 && x <= X1;
				boolean bed = bed(x, z);
				Block block = hallEdge || towerEdge ? p.foundation : hall || tower || porch ? p.floor : bed ? p.soil : p.foundation;
				placeBlock(level, block.defaultBlockState(), x, 0, z, box);
				fillColumnDown(level, p.foundation.defaultBlockState(), x, -1, z, box);
			}
		}
	}

	private static boolean bed(int x, int z) {
		return z >= 1 && z <= 3 && (x >= 4 && x <= 9 || x >= 20 && x <= 25);
	}

	/** Two storeys of wall: a dark plinth, tall windows below a band, small windows above, a band at the top. */
	private void hallWalls(WorldGenLevel level, BoundingBox box) {
		GuildPalette p = palette;
		for (int y = 1; y <= TOP; y++) {
			for (int x = X0; x <= X1; x++) {
				for (int z : new int[] {Z0, Z1}) {
					boolean pillar = x == X0 || x == 7 || x == TX0 || x == TX1 || x == 22 || x == X1;
					BlockState state;
					if (z == Z0 && x > TX0 && x < TX1) state = archway(x, y);
					else if (y == 5 || y == TOP) state = axis(p.beam, Direction.Axis.X);
					else if (pillar) state = axis(p.pillar, Direction.Axis.Y);
					else if (y == 1) state = p.foundation.defaultBlockState();
					else if (window(x, y, z == Z0)) state = pane(Direction.Axis.X);
					else state = p.wallAt(x, y, z).defaultBlockState();
					placeBlock(level, state, x, y, z, box);
				}
			}
			for (int z = Z0 + 1; z < Z1; z++) {
				for (int x : new int[] {X0, X1}) {
					boolean pillar = z == 12 || z == 17 || z == 21;
					BlockState state;
					if (y == 5 || y == TOP) state = axis(p.beam, Direction.Axis.Z);
					else if (pillar) state = axis(p.pillar, Direction.Axis.Y);
					else if (y == 1) state = p.foundation.defaultBlockState();
					else if ((y == 6 || y == 7) && z != 9 && z != 24) state = pane(Direction.Axis.Z);
					else state = p.wallAt(x, y, z).defaultBlockState();
					placeBlock(level, state, x, y, z, box);
				}
			}
		}
		// A stepped plinth along the sides and the back.
		for (int z = Z0; z <= Z1; z++) {
			placeBlock(level, stair(p.chair, Direction.EAST), X0 - 1, 1, z, box);
			placeBlock(level, stair(p.chair, Direction.WEST), X1 + 1, 1, z, box);
		}
		for (int x = X0; x <= X1; x++) placeBlock(level, stair(p.chair, Direction.SOUTH), x, 1, Z1 + 1, box);
	}

	/** The hall's front wall inside the tower: a wide rounded arch through to the hall. */
	private BlockState archway(int x, int y) {
		if (x >= 13 && x <= 16 && y <= 3) return Blocks.AIR.defaultBlockState();
		if (y == 4 && (x == 14 || x == 15)) return Blocks.AIR.defaultBlockState();
		if (y == 4 && x == 13) return stair(palette.chair, Direction.WEST).setValue(StairBlock.HALF, Half.TOP);
		if (y == 4 && x == 16) return stair(palette.chair, Direction.EAST).setValue(StairBlock.HALF, Half.TOP);
		return y == 5 || y == TOP ? axis(palette.beam, Direction.Axis.X) : palette.wallAt(x, y, Z0).defaultBlockState();
	}

	/** Window cells of the front and back walls: tall ones below the band, short ones above; none behind the notice board. */
	private static boolean window(int x, int y, boolean front) {
		boolean bay = x >= 4 && x <= 6 || x >= 8 && x <= 10 || x >= 19 && x <= 21 || x >= 23 && x <= 25;
		if (front) return bay && (y >= 2 && y <= 4 || y == 6 || y == 7);
		if (y >= 2 && y <= 4) return x >= 8 && x <= 10 || x >= 19 && x <= 21;
		return (y == 6 || y == 7) && (bay || x >= 12 && x <= 17) && x != 6 && x != 23;
	}

	/** A hipped roof, two dormers on each slope, the ridge capped; two chimneys, one with the signal fire. */
	private void hallRoof(WorldGenLevel level, BoundingBox box) {
		GuildPalette p = palette;
		hip(level, box, X0 - 1, X1 + 1, Z0 - 1, Z1 + 1, TOP + 1, true);
		for (int a : new int[] {4, 23}) dormer(level, box, a, false);
		for (int a : new int[] {8, 19}) dormer(level, box, a, true);
		// The chimneys, against the back wall: a furnace and a smoker at their feet.
		placeBlock(level, Blocks.FURNACE.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH), 23, 1, Z1 - 1, box);
		placeBlock(level, Blocks.SMOKER.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH), 6, 1, Z1 - 1, box);
		for (int y = 2; y <= 13; y++) {
			placeBlock(level, Blocks.BRICKS.defaultBlockState(), 23, y, Z1 - 1, box);
			placeBlock(level, Blocks.BRICKS.defaultBlockState(), 6, y, Z1 - 1, box);
		}
		placeBlock(level, Blocks.HAY_BLOCK.defaultBlockState(), 23, 14, Z1 - 1, box);
		placeBlock(level, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.SIGNAL_FIRE, true), 23, 15, Z1 - 1, box);
		placeBlock(level, Blocks.CAMPFIRE.defaultBlockState(), 6, 14, Z1 - 1, box);
	}

	/**
	 * A hipped roof over x0..x1 by z0..z1 (overhang included) starting at y0: each course one step in on every side, the
	 * corners turned, until the ridge; then a cap of slabs. The hall's roof leaves the tower's ground alone.
	 */
	private void hip(WorldGenLevel level, BoundingBox box, int x0, int x1, int z0, int z1, int y0, boolean hall) {
		GuildPalette p = palette;
		for (int i = 0; x0 + i <= x1 - i && z0 + i <= z1 - i; i++) {
			int ax = x0 + i, bx = x1 - i, az = z0 + i, bz = z1 - i, y = y0 + i;
			for (int x = ax; x <= bx; x++) {
				for (int z = az; z <= bz; z++) {
					boolean front = z == az, back = z == bz, left = x == ax, right = x == bx;
					if (!(front || back || left || right)) continue;
					if (hall && inTower(x, z)) continue;
					BlockState state;
					if (front && back) state = slab(p.roofSlab, SlabType.BOTTOM);
					else if (front && left) state = corner(Direction.NORTH, StairsShape.OUTER_RIGHT);
					else if (front && right) state = corner(Direction.NORTH, StairsShape.OUTER_LEFT);
					else if (back && left) state = corner(Direction.SOUTH, StairsShape.OUTER_LEFT);
					else if (back && right) state = corner(Direction.SOUTH, StairsShape.OUTER_RIGHT);
					else if (front) state = stair(p.roof, Direction.NORTH);
					else if (back) state = stair(p.roof, Direction.SOUTH);
					else if (left) state = stair(p.roof, Direction.EAST);
					else state = stair(p.roof, Direction.WEST);
					placeBlock(level, state, x, y, z, box);
				}
			}
			// The ridge: when two rows are left, cap them with slabs.
			if (bz - az == 1 && bx - ax >= 2) {
				for (int x = ax + 1; x < bx; x++) {
					placeBlock(level, slab(p.roofSlab, SlabType.BOTTOM), x, y + 1, az, box);
					placeBlock(level, slab(p.roofSlab, SlabType.BOTTOM), x, y + 1, bz, box);
				}
			}
		}
	}

	private BlockState corner(Direction facing, StairsShape shape) {
		return stair(palette.roof, facing).setValue(StairBlock.SHAPE, shape);
	}

	/** A small gabled window standing out of the roof above the wall, three wide from x = a; on the back slope if asked. */
	private void dormer(WorldGenLevel level, BoundingBox box, int a, boolean back) {
		GuildPalette p = palette;
		int face = back ? Z1 : Z0, out = back ? 1 : -1;
		for (int y = TOP + 1; y <= TOP + 2; y++) {
			placeBlock(level, p.wallAt(a, y, face).defaultBlockState(), a, y, face, box);
			placeBlock(level, pane(Direction.Axis.X), a + 1, y, face, box);
			placeBlock(level, p.wallAt(a + 2, y, face).defaultBlockState(), a + 2, y, face, box);
			placeBlock(level, Blocks.AIR.defaultBlockState(), a + 1, y, face - out, box);
		}
		placeBlock(level, p.wallAt(a + 1, TOP + 3, face).defaultBlockState(), a + 1, TOP + 3, face, box);
		for (int k = -1; k <= 1; k++) {
			int z = face - out * k;
			placeBlock(level, stair(p.roof, Direction.EAST), a, TOP + 3, z, box);
			placeBlock(level, stair(p.roof, Direction.WEST), a + 2, TOP + 3, z, box);
			placeBlock(level, slab(p.roofSlab, SlabType.BOTTOM), a + 1, TOP + 4, z, box);
		}
		placeBlock(level, slab(p.roofSlab, SlabType.TOP), a + 1, TOP + 3, face + out, box);
		placeBlock(level, p.roofBlock.defaultBlockState(), a + 1, TOP + 3, face - out, box);
	}

	/** The gate tower: a vaulted porch inside the door, a lit room above, a pyramid roof. */
	private void tower(WorldGenLevel level, BoundingBox box) {
		GuildPalette p = palette;
		for (int y = 1; y <= TOWER_TOP; y++) {
			for (int x = TX0; x <= TX1; x++) {
				for (int z = TZ0; z <= Z0; z++) {
					boolean wall = x == TX0 || x == TX1 || z == TZ0 || z == Z0 && y > TOP;
					if (!wall) {
						if (z < Z0) {
							BlockState inside = y == 7 ? p.beam.defaultBlockState() : Blocks.AIR.defaultBlockState();
							placeBlock(level, inside, x, y, z, box);
						}
						continue;
					}
					boolean corner = (x == TX0 || x == TX1) && (z == TZ0 || z == Z0);
					BlockState state;
					if (y == 5 || y == TOP || y == TOWER_TOP) state = axis(p.beam, x == TX0 || x == TX1 ? Direction.Axis.Z : Direction.Axis.X);
					else if (corner) state = axis(p.pillar, Direction.Axis.Y);
					else if (y == 1) state = p.foundation.defaultBlockState();
					else if (z == TZ0 && (x == DOOR_X || x == DOOR_X + 1) && y >= 10 && y <= 12) state = pane(Direction.Axis.X);
					else if ((x == TX0 || x == TX1) && (z == 4 || z == 5) && y >= 10 && y <= 12) state = pane(Direction.Axis.Z);
					else state = p.wallAt(x, y, z).defaultBlockState();
					placeBlock(level, state, x, y, z, box);
				}
			}
		}
		// The double door under a rounded arch.
		for (int i = 0; i < 2; i++) {
			int x = DOOR_X + i;
			BlockState door = p.door.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH)
					.setValue(DoorBlock.HINGE, i == 0 ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT);
			placeBlock(level, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), x, 1, TZ0, box);
			placeBlock(level, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), x, 2, TZ0, box);
			placeBlock(level, stair(p.chair, i == 0 ? Direction.WEST : Direction.EAST).setValue(StairBlock.HALF, Half.TOP), x, 3, TZ0, box);
		}
		hip(level, box, TX0 - 1, TX1 + 1, TZ0 - 1, Z0 + 1, TOWER_TOP + 1, false);
		// The guild's name over the door, its banners high on the tower.
		sign(level, box, DOOR_X, 4, TZ0 - 1, Direction.SOUTH, Component.empty(), Component.translatable("guild.noclip_backrooms.name"));
		sign(level, box, DOOR_X + 1, 4, TZ0 - 1, Direction.SOUTH, Component.empty(), Component.translatable("guild.noclip_backrooms.motto"));
		BlockState banner = Blocks.WALL_BANNER.yellow().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH);
		placeBlock(level, banner, TX0 + 1, 13, TZ0 - 1, box);
		placeBlock(level, banner, TX1 - 1, 13, TZ0 - 1, box);
		// The porch's lantern, and one in the room above that shows through the windows at night.
		placeBlock(level, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), DOOR_X, 6, 4, box);
		placeBlock(level, Blocks.LANTERN.defaultBlockState(), DOOR_X + 1, 8, 4, box);
		placeBlock(level, Blocks.POTTED_FERN.defaultBlockState(), TX0 + 1, 1, TZ0 + 1, box);
		placeBlock(level, Blocks.POTTED_FERN.defaultBlockState(), TX1 - 1, 1, TZ0 + 1, box);
	}

	/** Either side of the tower, a roofed walk on stone columns along the front of the hall, with benches. */
	private void colonnades(WorldGenLevel level, BoundingBox box) {
		GuildPalette p = palette;
		BlockState hanging = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
		for (int[] span : new int[][] {{X0, TX0 - 1}, {TX1 + 1, X1}}) {
			for (int x = span[0]; x <= span[1]; x++) {
				placeBlock(level, stair(p.roof, Direction.NORTH), x, 5, 3, box);
				for (int z = 4; z < Z0; z++) placeBlock(level, p.roofBlock.defaultBlockState(), x, 5, z, box);
			}
		}
		for (int x : new int[] {3, 6, 9, 20, 23, 26}) {
			for (int y = 1; y <= 4; y++) placeBlock(level, axis(p.pillar, Direction.Axis.Y), x, y, 4, box);
		}
		for (int x : new int[] {5, 8, 21, 24}) placeBlock(level, hanging, x, 4, 6, box);
		for (int x : new int[] {4, 5, 24, 25}) placeBlock(level, stair(p.chair, Direction.NORTH), x, 1, Z0 - 1, box);
	}

	/** Flower beds before the colonnades (sand with dry plants in the desert), lamp posts at the corners and the door. */
	private void yard(WorldGenLevel level, BoundingBox box) {
		GuildPalette p = palette;
		Block[] flowers = {Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.AZURE_BLUET, Blocks.OXEYE_DAISY, Blocks.ALLIUM,
				Blocks.RED_TULIP, Blocks.WHITE_TULIP};
		for (int x = 0; x < WIDTH; x++) {
			for (int z = 0; z < DEPTH; z++) {
				if (!bed(x, z)) continue;
				int h = (x * 31 + z * 17) * 0x9E3779B1 >>> 24;
				Block plant;
				if (p.soil == Blocks.SAND) plant = (x + z) % 3 == 0 ? Blocks.CACTUS : h % 3 == 0 ? Blocks.DEAD_BUSH : null;
				else plant = h % 4 == 0 ? null : flowers[h % flowers.length];
				if (plant != null) placeBlock(level, plant.defaultBlockState(), x, 1, z, box);
			}
		}
		for (int x : new int[] {X0, X1, DOOR_X - 2, DOOR_X + 3}) {
			placeBlock(level, p.post.defaultBlockState(), x, 1, 0, box);
			placeBlock(level, p.post.defaultBlockState(), x, 2, 0, box);
			placeBlock(level, Blocks.LANTERN.defaultBlockState(), x, 3, 0, box);
		}
	}

	/** Beams across the hall at the top of the walls, lanterns hanging from them on chains. */
	private void lights(WorldGenLevel level, BoundingBox box) {
		BlockState hanging = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
		for (int x : new int[] {7, 12, 17, 22}) {
			for (int z = Z0 + 1; z < Z1; z++) placeBlock(level, axis(palette.beam, Direction.Axis.Z), x, TOP, z, box);
			for (int z : new int[] {11, 16, 21}) {
				placeBlock(level, Blocks.IRON_CHAIN.defaultBlockState(), x, TOP - 1, z, box);
				placeBlock(level, hanging, x, TOP - 2, z, box);
			}
		}
	}

	/** A block in the hall's frame (its furniture keeps the layout it was first drawn in). */
	private void put(WorldGenLevel level, BlockState state, int x, int y, int z, BoundingBox box) {
		placeBlock(level, state, x + OX, y, z + OZ, box);
	}

	private void furnish(WorldGenLevel level, BoundingBox box) {
		GuildPalette p = palette;
		BlockState counterZ = axis(p.counter, Direction.Axis.Z), counterX = axis(p.counter, Direction.Axis.X);
		BlockState shelf = Blocks.BOOKSHELF.defaultBlockState(), barrel = Blocks.BARREL.defaultBlockState();
		BlockState lantern = Blocks.LANTERN.defaultBlockState();
		BlockState candles = Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true).setValue(CandleBlock.CANDLES, 3);

		// West: the buyer (front) and the supplier (back), behind one long counter split by a bookcase.
		for (int z = 2; z <= 17; z++) put(level, z == 9 ? shelf : counterZ, 5, 1, z, box);
		for (int x = 2; x <= 4; x++) {
			for (int y = 1; y <= 3; y++) put(level, shelf, x, y, 9, box);
		}
		put(level, lantern, 5, 2, 3, box);
		put(level, candles, 5, 2, 7, box);
		for (int z : new int[] {2, 3, 7, 8}) put(level, barrel, 2, 1, z, box);
		put(level, barrel, 2, 2, 2, box);
		put(level, Blocks.BREWING_STAND.defaultBlockState(), 5, 2, 15, box);
		put(level, lantern, 5, 2, 11, box);
		for (int z : new int[] {10, 11, 15, 16}) put(level, barrel, 2, 1, z, box);
		put(level, Blocks.COMPOSTER.defaultBlockState(), 2, 2, 16, box);
		sign(level, box, 2 + OX, 3, 5 + OZ, Direction.EAST, Component.empty(), ShopRole.BUYER.title(),
				Component.translatable("guild.noclip_backrooms.sign.buyer"));
		sign(level, box, 2 + OX, 3, 13 + OZ, Direction.EAST, Component.empty(), ShopRole.SUPPLIER.title(),
				Component.translatable("guild.noclip_backrooms.sign.supplier"));

		// East: the quartermaster (front), the workstations (back).
		for (int z = 2; z <= 9; z++) put(level, z == 9 ? shelf : counterZ, 20, 1, z, box);
		for (int x = 21; x <= 23; x++) {
			for (int y = 1; y <= 3; y++) put(level, shelf, x, y, 9, box);
		}
		put(level, Blocks.ANVIL.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH), 23, 1, 3, box);
		put(level, Blocks.SMITHING_TABLE.defaultBlockState(), 23, 1, 7, box);
		put(level, Blocks.GRINDSTONE.defaultBlockState().setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
				.setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH), 23, 1, 8, box);
		put(level, lantern, 20, 2, 3, box);
		put(level, candles, 20, 2, 8, box);
		sign(level, box, 23 + OX, 3, 5 + OZ, Direction.WEST, Component.empty(), ShopRole.QUARTERMASTER.title(),
				Component.translatable("guild.noclip_backrooms.sign.quartermaster"));
		put(level, Blocks.CRAFTING_TABLE.defaultBlockState(), 23, 1, 10, box);
		Block[] stations = {BackroomsProps.SALVAGE_BENCH, BackroomsProps.WORKSHOP_BENCH, BackroomsProps.ALMOND_DISTILLER, BackroomsProps.ANOMALY_ALTAR};
		for (int i = 0; i < stations.length; i++) {
			put(level, stations[i].defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST), 23, 1, 11 + 2 * i, box);
		}
		for (int z : new int[] {12, 14, 16}) {
			put(level, barrel, 23, 1, z, box);
			put(level, lantern, 23, 2, z, box);
		}

		// By the door: four beds and two chests for the guild's members.
		for (int x : new int[] {7, 9, 16, 18}) {
			BlockState bed = Blocks.BED.yellow().defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
			put(level, bed.setValue(BedBlock.PART, BedPart.HEAD), x, 1, 2, box);
			put(level, bed.setValue(BedBlock.PART, BedPart.FOOT), x, 1, 3, box);
		}
		for (int x : new int[] {10, 15}) {
			put(level, Blocks.CHEST.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH), x, 1, 2, box);
		}

		// The yellow runner from the tower's door to the master's desk, and the two long tables.
		for (int z = TZ0 + 1; z <= 14 + OZ; z++) {
			for (int x = DOOR_X; x <= DOOR_X + 1; x++) placeBlock(level, Blocks.CARPET.yellow().defaultBlockState(), x, 1, z, box);
		}
		BlockState table = p.tableSlab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
		for (int z = 6; z <= 11; z++) {
			for (int x : new int[] {8, 9, 16, 17}) put(level, table, x, 1, z, box);
		}
		for (int z : new int[] {7, 10}) {
			put(level, stair(p.chair, Direction.WEST), 7, 1, z, box);
			put(level, stair(p.chair, Direction.EAST), 10, 1, z, box);
			put(level, stair(p.chair, Direction.WEST), 15, 1, z, box);
			put(level, stair(p.chair, Direction.EAST), 18, 1, z, box);
		}
		put(level, candles, 8, 2, 7, box);
		put(level, Blocks.POTTED_FERN.defaultBlockState(), 9, 2, 10, box);
		put(level, Blocks.POTTED_FERN.defaultBlockState(), 16, 2, 7, box);
		put(level, candles, 17, 2, 10, box);

		// The master's desk and the notice board behind it.
		for (int x = 10; x <= 15; x++) put(level, counterX, x, 1, 15, box);
		put(level, lantern, 10, 2, 15, box);
		put(level, candles, 15, 2, 15, box);
		put(level, Blocks.LECTERN.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH), 8, 1, 16, box);
		put(level, Blocks.CARTOGRAPHY_TABLE.defaultBlockState(), 17, 1, 16, box);
		BlockState banner = Blocks.WALL_BANNER.yellow().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH);
		put(level, banner, 9, 3, 17, box);
		put(level, banner, 16, 3, 17, box);
		for (int i = 0; i < 4; i++) {
			sign(level, box, 11 + i + OX, 3, 17 + OZ, Direction.SOUTH, Component.translatable("guild.noclip_backrooms.notice." + i + ".1"),
					Component.translatable("guild.noclip_backrooms.notice." + i + ".2"),
					Component.translatable("guild.noclip_backrooms.notice." + i + ".3"),
					Component.translatable("guild.noclip_backrooms.notice." + i + ".4"));
		}
	}

	/** The merchants and the master, each spawned once, in the chunk they stand in (hall frame). */
	private void people(WorldGenLevel level, BoundingBox box) {
		spawn(level, box, ShopRole.BUYER, 3 + OX, 5 + OZ);
		spawn(level, box, ShopRole.SUPPLIER, 3 + OX, 13 + OZ);
		spawn(level, box, ShopRole.QUARTERMASTER, 22 + OX, 5 + OZ);
		spawn(level, box, ShopRole.MASTER, DOOR_X + 0.5, 16 + OZ);
	}

	private void spawn(WorldGenLevel level, BoundingBox box, ShopRole role, double x, int z) {
		int bit = 1 << role.ordinal();
		int x0 = (int) Math.floor(x);
		BlockPos at = getWorldPos(x0, 1, z).immutable();
		if ((spawned & bit) != 0 || !box.isInside(at)) return;
		Person person = People.type(role.look()).create(level.getLevel(), EntitySpawnReason.STRUCTURE);
		if (person == null) return;
		// The fraction of a cell along the piece's own x, turned into the world.
		double fx = x - x0;
		BlockPos next = getWorldPos(x0 + 1, 1, z);
		double wx = at.getX() + 0.5 + (next.getX() - at.getX()) * fx, wz = at.getZ() + 0.5 + (next.getZ() - at.getZ()) * fx;
		BlockPos centre = getWorldPos(DOOR_X, 1, 13 + OZ);
		float yaw = (float) Math.toDegrees(Math.atan2(-(centre.getX() + 0.5 - wx), centre.getZ() + 0.5 - wz));
		person.snapTo(wx, at.getY(), wz, yaw, 0);
		person.setYHeadRot(yaw);
		person.yBodyRot = yaw;
		person.setNoAi(true);
		person.setInvulnerable(true);
		person.setPersistenceRequired();
		person.addTag(role.tag());
		person.addTag(Guilds.MEMBER_TAG);
		person.setCustomName(role.title());
		person.setCustomNameVisible(true);
		level.addFreshEntityWithPassengers(person);
		spawned |= bit;
	}

	/**
	 * A wall sign with text. A sign's own setter tells a live world about the change, and there is none yet while the
	 * world is being made, so the text goes in the way a saved sign's would.
	 */
	private void sign(WorldGenLevel level, BoundingBox box, int x, int y, int z, Direction facing, Component... lines) {
		placeBlock(level, palette.wallSign.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing), x, y, z, box);
		BlockPos pos = getWorldPos(x, y, z).immutable();
		if (!box.isInside(pos) || !(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) return;
		SignText text = new SignText().setColor(DyeColor.BLACK);
		for (int i = 0; i < lines.length && i < 4; i++) text = text.setMessage(i, lines[i]);
		CompoundTag tag = new CompoundTag();
		SignText.DIRECT_CODEC.encodeStart(RegistryOps.create(NbtOps.INSTANCE, level.registryAccess()), text).ifSuccess(t -> tag.put("front_text", t));
		sign.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
		// Built in a live world (the place command), players need telling.
		if (level instanceof ServerLevel live) live.sendBlockUpdated(pos, sign.getBlockState(), sign.getBlockState(), 3);
	}

	private static BlockState axis(Block block, Direction.Axis axis) {
		BlockState state = block.defaultBlockState();
		return state.hasProperty(RotatedPillarBlock.AXIS) ? state.setValue(RotatedPillarBlock.AXIS, axis) : state;
	}

	private static BlockState slab(Block block, SlabType type) {
		BlockState state = block.defaultBlockState();
		return state.hasProperty(SlabBlock.TYPE) ? state.setValue(SlabBlock.TYPE, type) : state;
	}

	private static BlockState stair(Block block, Direction facing) {
		return block.defaultBlockState().setValue(StairBlock.FACING, facing);
	}

	/** A glass pane joined to its neighbours along a wall running on this axis. */
	private static BlockState pane(Direction.Axis along) {
		BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
		return along == Direction.Axis.X
				? pane.setValue(CrossCollisionBlock.EAST, true).setValue(CrossCollisionBlock.WEST, true)
				: pane.setValue(CrossCollisionBlock.NORTH, true).setValue(CrossCollisionBlock.SOUTH, true);
	}
}
