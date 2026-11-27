package dev.noclip.backrooms.frontrooms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.noclip.backrooms.core.Home;
import dev.noclip.backrooms.core.guild.Guilds;
import dev.noclip.backrooms.core.people.People;
import dev.noclip.backrooms.core.people.Person;
import dev.noclip.backrooms.core.quota.Quota;
import dev.noclip.backrooms.core.trade.ShopRole;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The M.E.G. institute as it runs: it receives everyone who gets out of the Backrooms, opens its blast doors as the team
 * meets its quotas (1: the medical wing, 3: the armoury and workshop, 5: B1 and the transport pads), keeps its people at
 * their counters, cannot be broken into (only its work areas take building), and keeps everyone inside the quarantine
 * wall. B2's hatch opens only to a high-grade keypad hacker.
 */
public final class Institute {
	static final String TAG = "noclip_backrooms_institute";
	/** Who stands where, and which way they look (yaw: 0 south, 90 west, 180 north, -90 east). */
	private static final List<Post> POSTS = List.of(
			new Post('k', ShopRole.MASTER, 0), new Post('y', ShopRole.BUYER, -90), new Post('u', ShopRole.SUPPLIER, 90),
			new Post('n', ShopRole.MEDIC, 180), new Post('q', ShopRole.QUARTERMASTER, 0));
	private static final AABB BUILDING = new AABB(InstituteLayout.MIN_X, InstituteLayout.BOTTOM, InstituteLayout.MIN_Z,
			InstituteLayout.MAX_X + 1, InstituteLayout.TOP + 1, InstituteLayout.MAX_Z + 1);

	private record Post(char spot, ShopRole role, float yaw) {
	}

	private Institute() {
	}

	static void register() {
		Home.setReceiver(Institute::receive);
		Quota.setPads(Institute::onPad);
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
			if (level.dimension() != Home.FRONTROOMS) return;
			ChunkPos pos = chunk.getPos();
			if (pos.getMaxBlockX() < InstituteLayout.MIN_X || pos.getMinBlockX() > InstituteLayout.MAX_X
					|| pos.getMaxBlockZ() < InstituteLayout.MIN_Z || pos.getMinBlockZ() > InstituteLayout.MAX_Z) return;
			MinecraftServer server = level.getServer();
			server.schedule(new TickTask(server.getTickCount(), () -> syncDoors(level)));
			server.schedule(new TickTask(server.getTickCount(), () -> linkParts(level, pos)));
		});
		Quota.onClearance(Institute::clearanceRose);
		// Someone touched by the Backrooms does not stay in the Overworld (older worlds, from before the institute).
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.player;
			if (player.level().dimension() != Level.OVERWORLD || !Home.entered(player)
					|| dev.noclip.backrooms.core.ending.Endings.isFree(player)
					|| player.isCreative() || player.isSpectator()) return;
			server.schedule(new TickTask(server.getTickCount(), () -> {
				if (player.hasDisconnected() || player.level().dimension() != Level.OVERWORLD) return;
				player.sendSystemMessage(Component.translatable("message.noclip_backrooms.institute.taken").withStyle(ChatFormatting.DARK_AQUA));
				receive(player);
			}));
		});
		ServerTickEvents.END_SERVER_TICK.register(Institute::tick);
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
				level.dimension() != Home.FRONTROOMS || player.isCreative() || !protectedBlock(pos.getX(), pos.getY(), pos.getZ()));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.dimension() != Home.FRONTROOMS || player.isCreative() || !(player.getItemInHand(hand).getItem() instanceof BlockItem)) {
				return InteractionResult.PASS;
			}
			BlockPos clicked = hit.getBlockPos();
			BlockPos target = level.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(hit.getDirection());
			if (!protectedBlock(target.getX(), target.getY(), target.getZ())) return InteractionResult.PASS;
			// Lockers, pads, doors and workstations still answer a click; only building onto the institute is refused.
			Block block = level.getBlockState(clicked).getBlock();
			if (!plain(block)) return InteractionResult.PASS;
			if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendOverlayMessage(Component.translatable("message.noclip_backrooms.institute.no_building").withStyle(ChatFormatting.GRAY));
			}
			return InteractionResult.FAIL;
		});
	}

	/**
	 * The generator lays the objects' blocks; here each part block of a multi-block object learns which block draws it
	 * (and breaks with it), for the parts in a chunk that has just loaded.
	 */
	private static void linkParts(ServerLevel level, ChunkPos chunk) {
		for (InstituteFurnishing.Place place : InstituteFurnishing.places()) {
			if (!place.type().multi()) continue;
			int floorY = InstituteLayout.floorY(place.floor()) + 1;
			BlockPos master = new BlockPos(place.x(), floorY, place.z());
			for (int[] cell : InstituteFurnishing.cells(place)) {
				if (cell[0] >> 4 != chunk.x() || cell[2] >> 4 != chunk.z()) continue;
				BlockPos at = new BlockPos(cell[0], floorY + cell[1], cell[2]);
				if (at.equals(master) || !level.isLoaded(at)) continue;
				if (level.getBlockEntity(at) instanceof dev.noclip.backrooms.props.PropBlockEntity part && part.masterPos().equals(at)) {
					part.setMasterPos(master);
				}
			}
		}
	}

	/** The building and the grounds' works (towers, mast, roads, the gate): M.E.G.'s, not to be broken or built on. */
	static boolean protectedBlock(int x, int y, int z) {
		return InstituteLayout.building(x, y, z) || FrontroomsCampus.structure(x, y, z);
	}

	/** The institute's walls, floors and fittings, which answer no click. */
	private static boolean plain(Block block) {
		return FrontroomsBlocks.ALL.contains(block) && block != FrontroomsBlocks.MEG_LOCKER && block != FrontroomsBlocks.TRANSPORT_PAD
				&& block != FrontroomsBlocks.ARCHIVE_HATCH && block != FrontroomsBlocks.ARCHIVE_FILE && block != FrontroomsBlocks.BLAST_DOOR
				|| block == net.minecraft.world.level.block.Blocks.GLASS || block == net.minecraft.world.level.block.Blocks.BOOKSHELF
				|| block == net.minecraft.world.level.block.Blocks.IRON_BARS;
	}

	static ServerLevel level(MinecraftServer server) {
		return server.getLevel(Home.FRONTROOMS);
	}

	/** Where people arrive: on the lobby's floor, facing the commander's desk. */
	public static Vec3 arrival() {
		InstituteLayout.Spot a = InstituteLayout.spot('a');
		return new Vec3(a.x() + 0.5, InstituteLayout.FLOOR + 1, a.z() + 0.5);
	}

	/** Someone got out: M.E.G. takes them in, to the quarantine lobby. */
	public static void receive(ServerPlayer player) {
		ServerLevel level = level(player.level().getServer());
		if (level == null) return;
		Vec3 at = arrival();
		level.getChunk(BlockPos.containing(at));
		player.stopRiding();
		player.resetFallDistance();
		player.teleport(new TeleportTransition(level, at, Vec3.ZERO, 180, 0, TeleportTransition.PLAY_PORTAL_SOUND));
	}

	private static void clearanceRose(MinecraftServer server, int clearance) {
		ServerLevel level = level(server);
		if (level != null) syncDoors(level);
		if (clearance == 1 || clearance == 3 || clearance == 5) {
			Component wing = Component.translatable("institute.noclip_backrooms.wing." + clearance);
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				player.sendSystemMessage(Component.translatable("message.noclip_backrooms.institute.wing_opened", wing).withStyle(ChatFormatting.AQUA));
			}
		}
	}

	/** Puts the blast doors, their lamps and the archive hatch in the state the team's quotas say. */
	public static void syncDoors(ServerLevel level) {
		int clearance = Quota.clearance(level.getServer());
		for (InstituteLayout.Door door : InstituteLayout.doors()) {
			boolean open = clearance >= door.requires();
			Direction.Axis axis = door.alongX() ? Direction.Axis.X : Direction.Axis.Z;
			for (int part = 0; part < 2; part++) {
				BlockPos pos = new BlockPos(door.x(), door.y() + part, door.z());
				if (!level.isLoaded(pos)) continue;
				BlockState want = FrontroomsBlocks.BLAST_DOOR.defaultBlockState()
						.setValue(BlastDoorBlock.HALF, part == 0 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER)
						.setValue(BlastDoorBlock.AXIS, axis).setValue(BlastDoorBlock.OPEN, open).setValue(BlastDoorBlock.REQUIRES, door.requires());
				if (level.getBlockState(pos) != want) level.setBlock(pos, want, Block.UPDATE_ALL);
			}
			BlockPos lamp = new BlockPos(door.x(), door.y() + 2, door.z());
			if (level.isLoaded(lamp)) {
				BlockState want = FrontroomsBlocks.DOOR_LAMP.defaultBlockState().setValue(DoorLampBlock.REQUIRES, door.requires())
						.setValue(DoorLampBlock.UNLOCKED, open);
				if (level.getBlockState(lamp) != want) level.setBlock(lamp, want, Block.UPDATE_ALL);
			}
		}
		InstituteLayout.Spot hatch = InstituteLayout.hatch();
		BlockPos at = new BlockPos(hatch.x(), InstituteLayout.B1_FLOOR, hatch.z());
		if (level.isLoaded(at) && State.get(level).archive && level.getBlockState(at).is(FrontroomsBlocks.ARCHIVE_HATCH)
				&& !level.getBlockState(at).getValue(InstituteBlocks.Hatch.OPEN)) {
			level.setBlock(at, level.getBlockState(at).setValue(InstituteBlocks.Hatch.OPEN, true), Block.UPDATE_ALL);
		}
	}

	/** A high-grade hacker got B2's hatch open: it stays open, for everyone. */
	static void openArchive(ServerLevel level) {
		State state = State.get(level);
		state.archive = true;
		state.setDirty();
		syncDoors(level);
	}

	public static boolean archiveOpen(MinecraftServer server) {
		ServerLevel level = level(server);
		return level != null && State.get(level).archive;
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = level(server);
		if (level == null) return;
		if (server.getTickCount() % 10 == 0) {
			// Past the quarantine wall there is nothing; whoever gets out there is brought back in.
			for (ServerPlayer player : level.players()) {
				if (player.isSpectator() || player.isCreative()) continue;
				int x = player.getBlockX(), z = player.getBlockZ();
				if (FrontroomsTerrain.radius(x, z) > FrontroomsTerrain.WALL_OUT + 2 || player.getY() < 1) {
					player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.institute.boundary").withStyle(ChatFormatting.RED));
					receive(player);
				}
			}
		}
		if (server.getTickCount() % 40 == 0) staff(level);
	}

	/** Puts anyone missing back at their post, once their part of the building and its entities are loaded. */
	static void staff(ServerLevel level) {
		for (Post post : POSTS) {
			InstituteLayout.Spot spot = InstituteLayout.spot(post.spot());
			BlockPos at = new BlockPos(spot.x(), InstituteLayout.FLOOR + 1, spot.z());
			if (!level.isLoaded(at) || !level.areEntitiesLoaded(ChunkPos.pack(at))) continue;
			String tag = post.role().tag();
			if (!level.getEntitiesOfClass(Person.class, BUILDING, p -> p.entityTags().contains(tag) && p.entityTags().contains(TAG)).isEmpty()) continue;
			Person person = People.type(post.role().look()).create(level, EntitySpawnReason.STRUCTURE);
			if (person == null) continue;
			person.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, post.yaw(), 0);
			person.setYHeadRot(post.yaw());
			person.yBodyRot = post.yaw();
			person.setNoAi(true);
			person.setInvulnerable(true);
			person.setPersistenceRequired();
			person.addTag(tag);
			person.addTag(Guilds.MEMBER_TAG);
			person.addTag(TAG);
			person.setCustomName(post.role().title());
			person.setCustomNameVisible(true);
			level.addFreshEntity(person);
		}
	}

	/** Whether the player stands on or right beside one of the institute's transport pads (B1 or 1F lobby). */
	public static boolean onPad(Player player) {
		if (player.level().dimension() != Home.FRONTROOMS) return false;
		for (InstituteLayout.Spot pad : InstituteLayout.pads()) {
			if (Math.abs(player.getX() - (pad.x() + 0.5)) < 2.5 && Math.abs(player.getZ() - (pad.z() + 0.5)) < 2.5
					&& Math.abs(player.getY() - (InstituteLayout.B1_FLOOR + 1)) < 2) return true;
		}
		for (InstituteLayout.Spot pad : InstituteLayout.lobbyPads()) {
			if (Math.abs(player.getX() - (pad.x() + 0.5)) < 2.5 && Math.abs(player.getZ() - (pad.z() + 0.5)) < 2.5
					&& Math.abs(player.getY() - (InstituteLayout.FLOOR + 1)) < 2) return true;
		}
		return false;
	}

	/** Whether an entity of the institute (as opposed to the same role somewhere in the Backrooms). */
	public static boolean ofInstitute(net.minecraft.world.entity.Entity entity) {
		return entity.level().dimension() == Home.FRONTROOMS && entity.entityTags().contains(TAG);
	}

	/** What the institute remembers: whether B2's archive has been broken into. */
	static final class State extends SavedData {
		static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.BOOL.optionalFieldOf("archive", false).forGetter(s -> s.archive)).apply(i, State::new));
		static final SavedDataType<State> TYPE = new SavedDataType<>(Frontrooms.id("institute"), State::new, CODEC,
				DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
		boolean archive;

		State() {
			this(false);
		}

		State(boolean archive) {
			this.archive = archive;
		}

		static State get(ServerLevel level) {
			return level.getDataStorage().computeIfAbsent(TYPE);
		}
	}

	static boolean isFrontrooms(Level level) {
		return level.dimension() == Home.FRONTROOMS;
	}
}
