package dev.noclip.backrooms.core.story;

import com.mojang.serialization.Codec;
import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.Home;
import dev.noclip.backrooms.core.floors.Floors;
import dev.noclip.backrooms.core.radio.Radio;
import dev.noclip.backrooms.level0.Level0;
import dev.noclip.backrooms.level0.Level0Guide;
import dev.noclip.backrooms.level0.Level0Space;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * A newcomer's first trip (docs/企劃書/1-遊戲玩法/README.md, "第一趟：跟著纜線走"). Falling into Level 0 for the first
 * time, there is a cable on the floor from where they land to a glowing door; it is the only way out that opens for
 * them: every other exit says there is nothing on the other side, and the cable goes on. Through the door is the M.E.G.
 * institute's receiving room. Once they have been there, Level 0 is as it is for everyone.
 *
 * Who has been to the institute is counted for each player. Someone who has got home alive before this came in (they
 * were issued a radio) has been. A player put into Level 0 by a command has no cable and is not held to it.
 */
public final class FirstTrip {
	/** Whether the player has got to the institute. */
	public static final AttachmentType<Boolean> DONE = AttachmentRegistry.create(BackroomsMod.id("first_trip_done"),
			builder -> builder.persistent(Codec.BOOL).copyOnDeath());
	/** The door at the end of the player's cable (lower half), while they are on their first trip. */
	public static final AttachmentType<BlockPos> DOOR = AttachmentRegistry.create(BackroomsMod.id("first_trip_door"),
			builder -> builder.persistent(BlockPos.CODEC).copyOnDeath());
	/** A cable this near the landing (across the torus) is the one to follow; further, it is another trip's. */
	private static final int SAME_STRETCH = 200;
	private static final int REFUSE_EVERY = 40;
	private static final Map<UUID, Integer> LAST_REFUSED = new HashMap<>();
	/** Newcomers whose cable is still being laid: already held to it. */
	private static final java.util.Set<UUID> LAYING = java.util.concurrent.ConcurrentHashMap.newKeySet();

	private FirstTrip() {
	}

	public static void register() {
		StoryBlocks.register();
		// Out of the Backrooms alive, by the door or any other way: they have been to the institute.
		Floors.onHome((player, run) -> {
			player.setAttached(DONE, true);
			player.removeAttached(DOOR);
			Missions.onPlayerHome(player);
		});
	}

	/** Whether the player has been to the institute (and so may use any exit). */
	public static boolean done(ServerPlayer player) {
		return Boolean.TRUE.equals(player.getAttached(DONE)) || Boolean.TRUE.equals(player.getAttached(Radio.ISSUED));
	}

	/** Whether the player is on their first trip, with a cable to follow, in Level 0. */
	public static boolean following(ServerPlayer player) {
		return player.level().dimension() == Level0.LEVEL_0 && (player.hasAttached(DOOR) || LAYING.contains(player.getUUID())) && !done(player);
	}

	/**
	 * Called by every way out of Level 0 before it takes the player anywhere: on their first trip it does not, and
	 * says so. Returns whether the player was turned back.
	 */
	public static boolean refuse(ServerPlayer player) {
		if (!following(player)) return false;
		int now = player.level().getServer().getTickCount();
		Integer last = LAST_REFUSED.get(player.getUUID());
		if (last == null || now < last || now - last >= REFUSE_EVERY) {
			LAST_REFUSED.put(player.getUUID(), now);
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.first_trip.refuse").withStyle(ChatFormatting.GRAY));
		}
		return true;
	}

	/**
	 * The player fell into Level 0 from outside (through the ground, waking up lost, sent back). On their first trip,
	 * they get a cable: the one already laid near them, or a new one from where they are.
	 */
	public static void fellIn(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		// After the arrival has settled (the Stage loop has put them in the middle Stage).
		server.schedule(new TickTask(server.getTickCount() + 1, () -> {
			if (player.isRemoved() || player.hasDisconnected() || done(player)) return;
			if (!(player.level() instanceof ServerLevel level0) || level0.dimension() != Level0.LEVEL_0) return;
			BlockPos door = nearbyDoor(level0, player, player.getAttached(DOOR));
			for (ServerPlayer other : server.getPlayerList().getPlayers()) {
				if (door != null) break;
				if (other != player && other.level() == level0) door = nearbyDoor(level0, player, other.getAttached(DOOR));
			}
			if (door != null) {
				player.setAttached(DOOR, door);
				return;
			}
			if (!LAYING.add(player.getUUID())) return;
			Level0Guide.lay(level0, player.blockPosition(), laid -> {
				LAYING.remove(player.getUUID());
				if (laid == null || done(player)) player.removeAttached(DOOR);
				else player.setAttached(DOOR, laid);
			});
		}));
	}

	/** The door, if it stands in Level 0 within reach of the player. */
	private static BlockPos nearbyDoor(ServerLevel level0, ServerPlayer player, BlockPos door) {
		if (door == null) return null;
		double dx = Level0Space.nearest(door.getX() - player.getX()), dz = Level0Space.nearest(door.getZ() - player.getZ());
		if (dx * dx + dz * dz > SAME_STRETCH * SAME_STRETCH) return null;
		level0.getChunk(door);
		return level0.getBlockState(door).is(StoryBlocks.GUIDE_DOOR) ? door : null;
	}

	/** The player opened an existing glowing door: always takes them back to the M.E.G. institute. */
	static void throughDoor(ServerPlayer player) {
		if (player.isRemoved() || player.hasDisconnected() || !player.isAlive()) return;
		if (!Home.inBackrooms(player.level().dimension())) return;
		player.level().playSound(null, player.blockPosition(), SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.9F, 0.9F);
		Home.send(player);
	}
}
