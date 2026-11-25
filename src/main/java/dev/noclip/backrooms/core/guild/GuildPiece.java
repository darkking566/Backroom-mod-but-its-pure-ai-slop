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
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * The guild hall: a timber hall of 24 by 18 with a steep roof and a chimney sending up a column of smoke, so it can be
 * seen from the village. Inside: the buyer and the supplier at counters on one side, the quartermaster and the Backrooms
 * workstations on the other, the guild master's desk and notice board at the back, beds and chests by the door.
 *
 * <p>Drawn in the piece's own frame: x 0..25 across, z 0..19 from the front (the side facing the village) to the back,
 * y 0 the floor. Directions here are as for a piece facing north (the front is south, +x is east, +z is north); the
 * piece turns them to its real facing.
 */
public final class GuildPiece extends StructurePiece {
	static final int WIDTH = 26, DEPTH = 20, HEIGHT = 17;
	/** The doorway's left cell (the door is x 12 and 13 of the front wall). */
	static final int DOOR_X = 12;
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

	GuildPalette palette() {
		return palette;
	}

	/** Where a cell of the hall's own frame (see the class comment) is in the world; for tests. */
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
		GuildPalette p = palette;
		// Clear the volume, then the floor on a foundation down to the ground.
		generateAirBox(level, box, 0, 1, 0, WIDTH - 1, HEIGHT - 1, DEPTH - 1);
		for (int x = 1; x <= 24; x++) {
			for (int z = 1; z <= 18; z++) {
				boolean edge = x == 1 || x == 24 || z == 1 || z == 18;
				placeBlock(level, (edge ? p.foundation : p.floor).defaultBlockState(), x, 0, z, box);
				fillColumnDown(level, p.foundation.defaultBlockState(), x, -1, z, box);
			}
		}
		for (int x = 10; x <= 15; x++) {
			placeBlock(level, p.foundation.defaultBlockState(), x, 0, 0, box);
			fillColumnDown(level, p.foundation.defaultBlockState(), x, -1, 0, box);
		}
		walls(level, box);
		roof(level, box);
		lights(level, box);
		furnish(level, box);
		people(level, box);
	}

	private void walls(WorldGenLevel level, BoundingBox box) {
		GuildPalette p = palette;
		for (int y = 1; y <= 6; y++) {
			for (int x = 1; x <= 24; x++) {
				for (int z : new int[] {1, 18}) {
					boolean pillar = x == 1 || x == 7 || x == 11 || x == 14 || x == 18 || x == 24;
					BlockState state;
					if (y == 6 && !pillar) state = axis(p.beam, Direction.Axis.X);
					else if (pillar) state = axis(p.pillar, Direction.Axis.Y);
					else if ((y == 2 || y == 3) && window(x, z == 1)) state = pane(Direction.Axis.X);
					else state = p.wall.defaultBlockState();
					placeBlock(level, state, x, y, z, box);
				}
			}
			for (int z = 2; z <= 17; z++) {
				for (int x : new int[] {1, 24}) {
					boolean pillar = z == 5 || z == 9 || z == 14;
					BlockState state = y == 6 && !pillar ? axis(p.beam, Direction.Axis.Z)
							: pillar ? axis(p.pillar, Direction.Axis.Y) : p.wall.defaultBlockState();
					placeBlock(level, state, x, y, z, box);
				}
			}
		}
		// The double door.
		for (int i = 0; i < 2; i++) {
			int x = DOOR_X + i;
			BlockState door = p.door.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH)
					.setValue(DoorBlock.HINGE, i == 0 ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT);
			placeBlock(level, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), x, 1, 1, box);
			placeBlock(level, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), x, 2, 1, box);
		}
	}

	/** Window cells of the front and back walls: none at the door, the notice board or the corners. */
	private static boolean window(int x, boolean front) {
		if (front) return x >= 3 && x <= 5 || x >= 8 && x <= 10 || x >= 15 && x <= 17 || x >= 20 && x <= 22;
		return x >= 3 && x <= 5 || x >= 20 && x <= 22;
	}

	private void roof(WorldGenLevel level, BoundingBox box) {
		GuildPalette p = palette;
		for (int i = 0; i <= 9; i++) {
			int y = 6 + i;
			for (int x = 0; x <= 25; x++) {
				placeBlock(level, stair(p.roof, Direction.NORTH), x, y, i, box);
				placeBlock(level, stair(p.roof, Direction.SOUTH), x, y, 19 - i, box);
			}
			// The gable ends, with a round window high up.
			for (int z = i + 1; z <= 18 - i; z++) {
				boolean glass = (y == 9 || y == 10) && (z == 9 || z == 10);
				for (int x : new int[] {1, 24}) {
					placeBlock(level, glass ? pane(Direction.Axis.Z) : p.wall.defaultBlockState(), x, y, z, box);
				}
			}
		}
		// Tie beams across the hall.
		for (int x : new int[] {5, 10, 15, 20}) {
			for (int z = 2; z <= 17; z++) placeBlock(level, axis(p.beam, Direction.Axis.Z), x, 6, z, box);
		}
		// The chimney at the back, a signal fire on top: its smoke rises far above the trees.
		placeBlock(level, Blocks.FURNACE.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH), 21, 1, 17, box);
		for (int y = 2; y <= 10; y++) placeBlock(level, Blocks.BRICKS.defaultBlockState(), 21, y, 17, box);
		placeBlock(level, Blocks.HAY_BLOCK.defaultBlockState(), 21, 11, 17, box);
		placeBlock(level, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.SIGNAL_FIRE, true), 21, 12, 17, box);
	}

	private void lights(WorldGenLevel level, BoundingBox box) {
		BlockState hanging = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
		for (int x : new int[] {5, 10, 15, 20}) {
			for (int z : new int[] {4, 9, 14}) placeBlock(level, hanging, x, 5, z, box);
		}
		// Lamp posts by the door outside.
		for (int x : new int[] {10, 15}) {
			placeBlock(level, palette.fence.defaultBlockState(), x, 1, 0, box);
			placeBlock(level, Blocks.LANTERN.defaultBlockState(), x, 2, 0, box);
		}
	}

	private void furnish(WorldGenLevel level, BoundingBox box) {
		GuildPalette p = palette;
		BlockState counterZ = axis(p.counter, Direction.Axis.Z), counterX = axis(p.counter, Direction.Axis.X);
		BlockState shelf = Blocks.BOOKSHELF.defaultBlockState(), barrel = Blocks.BARREL.defaultBlockState();
		BlockState lantern = Blocks.LANTERN.defaultBlockState();
		BlockState candles = Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true).setValue(CandleBlock.CANDLES, 3);

		// West: the buyer (front) and the supplier (back), behind one long counter split by a bookcase.
		for (int z = 2; z <= 17; z++) placeBlock(level, z == 9 ? shelf : counterZ, 5, 1, z, box);
		for (int x = 2; x <= 4; x++) {
			for (int y = 1; y <= 3; y++) placeBlock(level, shelf, x, y, 9, box);
		}
		placeBlock(level, lantern, 5, 2, 3, box);
		placeBlock(level, candles, 5, 2, 7, box);
		for (int z : new int[] {2, 3, 7, 8}) placeBlock(level, barrel, 2, 1, z, box);
		placeBlock(level, barrel, 2, 2, 2, box);
		placeBlock(level, Blocks.BREWING_STAND.defaultBlockState(), 5, 2, 15, box);
		placeBlock(level, lantern, 5, 2, 11, box);
		for (int z : new int[] {10, 11, 16, 17}) placeBlock(level, barrel, 2, 1, z, box);
		placeBlock(level, Blocks.COMPOSTER.defaultBlockState(), 2, 2, 17, box);
		sign(level, box, 2, 3, 5, Direction.EAST, Component.empty(), ShopRole.BUYER.title(),
				Component.translatable("guild.noclip_backrooms.sign.buyer"));
		sign(level, box, 2, 3, 13, Direction.EAST, Component.empty(), ShopRole.SUPPLIER.title(),
				Component.translatable("guild.noclip_backrooms.sign.supplier"));

		// East: the quartermaster (front), the workstations (back).
		for (int z = 2; z <= 9; z++) placeBlock(level, z == 9 ? shelf : counterZ, 20, 1, z, box);
		for (int x = 21; x <= 23; x++) {
			for (int y = 1; y <= 3; y++) placeBlock(level, shelf, x, y, 9, box);
		}
		placeBlock(level, Blocks.ANVIL.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH), 23, 1, 3, box);
		placeBlock(level, Blocks.SMITHING_TABLE.defaultBlockState(), 23, 1, 7, box);
		placeBlock(level, Blocks.GRINDSTONE.defaultBlockState().setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
				.setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH), 23, 1, 8, box);
		placeBlock(level, lantern, 20, 2, 3, box);
		placeBlock(level, candles, 20, 2, 8, box);
		sign(level, box, 23, 3, 5, Direction.WEST, Component.empty(), ShopRole.QUARTERMASTER.title(),
				Component.translatable("guild.noclip_backrooms.sign.quartermaster"));
		placeBlock(level, Blocks.CRAFTING_TABLE.defaultBlockState(), 23, 1, 10, box);
		Block[] stations = {BackroomsProps.SALVAGE_BENCH, BackroomsProps.WORKSHOP_BENCH, BackroomsProps.ALMOND_DISTILLER, BackroomsProps.ANOMALY_ALTAR};
		for (int i = 0; i < stations.length; i++) {
			placeBlock(level, stations[i].defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST), 23, 1, 11 + 2 * i, box);
		}
		for (int z : new int[] {12, 14, 16}) {
			placeBlock(level, barrel, 23, 1, z, box);
			placeBlock(level, lantern, 23, 2, z, box);
		}

		// By the door: four beds and two chests for the guild's members.
		for (int x : new int[] {7, 9, 16, 18}) {
			BlockState bed = Blocks.BED.yellow().defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
			placeBlock(level, bed.setValue(BedBlock.PART, BedPart.HEAD), x, 1, 2, box);
			placeBlock(level, bed.setValue(BedBlock.PART, BedPart.FOOT), x, 1, 3, box);
		}
		for (int x : new int[] {10, 15}) {
			placeBlock(level, Blocks.CHEST.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH), x, 1, 2, box);
		}

		// The yellow runner from the door to the master's desk, and the two long tables.
		for (int z = 2; z <= 14; z++) {
			for (int x = DOOR_X; x <= DOOR_X + 1; x++) placeBlock(level, Blocks.CARPET.yellow().defaultBlockState(), x, 1, z, box);
		}
		BlockState table = p.tableSlab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
		for (int z = 6; z <= 11; z++) {
			for (int x : new int[] {8, 9, 16, 17}) placeBlock(level, table, x, 1, z, box);
		}
		for (int z : new int[] {7, 10}) {
			placeBlock(level, stair(p.chair, Direction.WEST), 7, 1, z, box);
			placeBlock(level, stair(p.chair, Direction.EAST), 10, 1, z, box);
			placeBlock(level, stair(p.chair, Direction.WEST), 15, 1, z, box);
			placeBlock(level, stair(p.chair, Direction.EAST), 18, 1, z, box);
		}
		placeBlock(level, candles, 8, 2, 7, box);
		placeBlock(level, Blocks.POTTED_FERN.defaultBlockState(), 9, 2, 10, box);
		placeBlock(level, Blocks.POTTED_FERN.defaultBlockState(), 16, 2, 7, box);
		placeBlock(level, candles, 17, 2, 10, box);

		// The master's desk and the notice board behind it.
		for (int x = 10; x <= 15; x++) placeBlock(level, counterX, x, 1, 15, box);
		placeBlock(level, lantern, 10, 2, 15, box);
		placeBlock(level, candles, 15, 2, 15, box);
		placeBlock(level, Blocks.LECTERN.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH), 8, 1, 16, box);
		placeBlock(level, Blocks.CARTOGRAPHY_TABLE.defaultBlockState(), 17, 1, 16, box);
		BlockState banner = Blocks.WALL_BANNER.yellow().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH);
		placeBlock(level, banner, 9, 3, 17, box);
		placeBlock(level, banner, 16, 3, 17, box);
		for (int i = 0; i < 4; i++) {
			sign(level, box, 11 + i, 3, 17, Direction.SOUTH, Component.translatable("guild.noclip_backrooms.notice." + i + ".1"),
					Component.translatable("guild.noclip_backrooms.notice." + i + ".2"),
					Component.translatable("guild.noclip_backrooms.notice." + i + ".3"),
					Component.translatable("guild.noclip_backrooms.notice." + i + ".4"));
		}

		// Outside: the name over the door, banners either side.
		sign(level, box, DOOR_X, 3, 0, Direction.SOUTH, Component.empty(), Component.translatable("guild.noclip_backrooms.name"));
		sign(level, box, DOOR_X + 1, 3, 0, Direction.SOUTH, Component.empty(), Component.translatable("guild.noclip_backrooms.motto"));
		placeBlock(level, banner, DOOR_X - 1, 3, 0, box);
		placeBlock(level, banner, DOOR_X + 2, 3, 0, box);
	}

	/** The merchants and the master, each spawned once, in the chunk they stand in. */
	private void people(WorldGenLevel level, BoundingBox box) {
		spawn(level, box, ShopRole.BUYER, 3, 5);
		spawn(level, box, ShopRole.SUPPLIER, 3, 13);
		spawn(level, box, ShopRole.QUARTERMASTER, 22, 5);
		spawn(level, box, ShopRole.MASTER, DOOR_X + 0.5, 16);
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
		BlockPos centre = boundingBox.getCenter();
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
	}

	private static BlockState axis(Block block, Direction.Axis axis) {
		BlockState state = block.defaultBlockState();
		return state.hasProperty(RotatedPillarBlock.AXIS) ? state.setValue(RotatedPillarBlock.AXIS, axis) : state;
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
