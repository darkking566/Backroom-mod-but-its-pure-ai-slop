package dev.noclip.backrooms.core.trip;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.Home;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Where the team falls into the Backrooms from outside (docs/project/1-gameplay/README.md, "多人時同一片後室"). The
 * first to fall into a level opens a stretch of it, somewhere new; everyone who falls into that level after them (dying,
 * deployed, dropped at dawn, the sand) lands in the same stretch. Once nobody of the team is in the Backrooms any more,
 * the stretches close, and the next fall opens a new one.
 */
public final class Expedition {
	/** How far out a stretch may be, and how near the middle it may not. */
	static final int SPREAD = 1_500_000, KEEP_OUT = 50_000;
	/** How long nobody of the team may be in the Backrooms before their stretches close (a quick trip home keeps them). */
	static final int CLOSE_AFTER = 600;
	private static int emptySince = -1;

	private Expedition() {
	}

	public static void register() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> emptySince = -1);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 != 0) return;
			var players = server.getPlayerList().getPlayers();
			if (players.isEmpty()) return;
			for (ServerPlayer player : players) {
				if (Home.inBackrooms(player.level().dimension())) {
					emptySince = -1;
					return;
				}
			}
			if (emptySince < 0) emptySince = server.getTickCount();
			if (server.getTickCount() - emptySince < CLOSE_AFTER) return;
			Data data = Data.get(server.overworld());
			if (!data.zones.isEmpty()) {
				data.zones.clear();
				data.setDirty();
			}
		});
	}

	/** The stretch of a level the team falls into, as {x, z}; opens one if there is none. */
	public static int[] zone(MinecraftServer server, String level) {
		Data data = Data.get(server.overworld());
		Long at = data.zones.get(level);
		if (at == null) {
			RandomSource random = server.overworld().getRandom();
			int x, z;
			do {
				x = random.nextInt(2 * SPREAD + 1) - SPREAD;
				z = random.nextInt(2 * SPREAD + 1) - SPREAD;
			} while (Math.abs(x) < KEEP_OUT && Math.abs(z) < KEEP_OUT);
			at = ((long) x << 32) | (z & 0xFFFFFFFFL);
			data.zones.put(level, at);
			data.setDirty();
		}
		return new int[] {(int) (at >> 32), (int) (long) at};
	}

	/** Whether the team has a stretch of this level open (for tests). */
	public static boolean open(MinecraftServer server, String level) {
		return Data.get(server.overworld()).zones.containsKey(level);
	}

	static final class Data extends SavedData {
		static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("zones", Map.of()).forGetter(d -> d.zones)).apply(i, Data::new));
		static final SavedDataType<Data> TYPE = new SavedDataType<>(BackroomsMod.id("expedition"), Data::new, CODEC,
				DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
		final Map<String, Long> zones;

		Data() {
			this(Map.of());
		}

		Data(Map<String, Long> zones) {
			this.zones = new HashMap<>(zones);
		}

		static Data get(ServerLevel overworld) {
			return overworld.getDataStorage().computeIfAbsent(TYPE);
		}
	}
}
