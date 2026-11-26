package dev.noclip.backrooms.core.trip;

import dev.noclip.backrooms.core.Home;
import dev.noclip.backrooms.core.floors.Floors;
import dev.noclip.backrooms.monsters.Monsters;
import dev.noclip.backrooms.monsters.hound.Hound;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Waiting to be picked up (docs/project/1-gameplay/README.md, "撤離接應倒數"). Getting out is never a click: whoever
 * calls for extraction (from a base they settled at, at an exit door, with a homeward anchor) has to hold out where
 * they are for 30 to 60 seconds, longer the deeper the trip has gone, while the signal draws whatever is near and,
 * twice, brings more out of the dark. Wander off, change level or die and the pickup is off.
 */
public final class Extraction {
	/** How often the signal calls the creatures near it, and how far it carries. */
	private static final int CALL_EVERY = 40;
	private static final double CALL_RANGE = 40;
	private static final Map<UUID, Run> RUNS = new HashMap<>();

	private static final class Run {
		final ResourceKey<Level> level;
		final Vec3 at;
		final double radius;
		final int total;
		final int floor;
		final ServerBossEvent bar;
		int left;

		Run(ServerPlayer player, Vec3 at, double radius, int total, int floor) {
			this.level = player.level().dimension();
			this.at = at;
			this.radius = radius;
			this.total = total;
			this.left = total;
			this.floor = floor;
			this.bar = new ServerBossEvent(UUID.randomUUID(), Component.translatable("bossbar.noclip_backrooms.extraction", total / 20),
					BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);
			bar.addPlayer(player);
		}
	}

	private Extraction() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Extraction::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Run run : RUNS.values()) run.bar.removeAllPlayers();
			RUNS.clear();
		});
	}

	/** Seconds to hold out on a trip that has come this many floors. */
	public static int seconds(int floor) {
		return Mth.clamp(30 + 6 * (floor - 1), 30, 60);
	}

	/** Calls for a pickup where the player stands; they must stay within {@code radius} of it. */
	public static boolean start(ServerPlayer player, Vec3 at, double radius) {
		if (!Home.inBackrooms(player.level().dimension())) return false;
		if (RUNS.containsKey(player.getUUID())) {
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.extraction.already").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		int floor = Math.max(1, Floors.of(player).floor());
		int seconds = seconds(floor);
		RUNS.put(player.getUUID(), new Run(player, at, radius, seconds * 20, floor));
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.extraction.started", seconds).withStyle(ChatFormatting.GOLD));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 1.0F, 0.6F);
		return true;
	}

	public static boolean running(ServerPlayer player) {
		return RUNS.containsKey(player.getUUID());
	}

	/** For the GM command and tests: the pickup arrives now. */
	public static void finishNow(ServerPlayer player) {
		Run run = RUNS.get(player.getUUID());
		if (run != null) run.left = 1;
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<Map.Entry<UUID, Run>> it = RUNS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Run> entry = it.next();
			Run run = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			String broken = player == null || player.hasDisconnected() ? "" : !player.isAlive() ? "message.noclip_backrooms.extraction.dead"
					: player.level().dimension() != run.level ? "message.noclip_backrooms.extraction.left_level"
					: player.position().distanceTo(run.at) > run.radius ? "message.noclip_backrooms.extraction.wandered" : null;
			if (broken != null) {
				run.bar.removeAllPlayers();
				it.remove();
				if (player != null && !broken.isEmpty()) player.sendSystemMessage(Component.translatable(broken).withStyle(ChatFormatting.RED));
				continue;
			}
			run.left--;
			run.bar.setProgress(1.0F - (float) run.left / run.total);
			run.bar.setName(Component.translatable("bossbar.noclip_backrooms.extraction", (run.left + 19) / 20));
			ServerLevel level = (ServerLevel) player.level();
			if (run.left % CALL_EVERY == 0) call(level, player);
			if (run.left == run.total * 2 / 3 || run.left == run.total / 3) wave(level, player, run);
			if (run.left % 100 == 0 && run.left > 0) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.8F, 1.4F);
			}
			if (run.left <= 0) {
				run.bar.removeAllPlayers();
				it.remove();
				player.sendSystemMessage(Component.translatable("message.noclip_backrooms.extraction.done").withStyle(ChatFormatting.GREEN));
				Home.send(player);
			}
		}
	}

	/** The signal carries: everything hostile near the player comes for them. */
	private static void call(ServerLevel level, ServerPlayer player) {
		AABB around = player.getBoundingBox().inflate(CALL_RANGE);
		for (Monster monster : level.getEntitiesOfClass(Monster.class, around, m -> m.isAlive() && m.getTarget() == null)) {
			monster.setTarget(player);
		}
	}

	/** More come out of the dark: hounds, more of them the deeper the trip. */
	private static void wave(ServerLevel level, ServerPlayer player, Run run) {
		int count = 1 + run.floor / 3;
		var random = player.getRandom();
		for (int i = 0; i < count; i++) {
			for (int attempt = 0; attempt < 24; attempt++) {
				double angle = random.nextDouble() * Math.PI * 2, distance = 14 + random.nextDouble() * 10;
				BlockPos feet = BlockPos.containing(player.getX() + Math.cos(angle) * distance, player.getY(), player.getZ() + Math.sin(angle) * distance);
				if (!level.isLoaded(feet)) continue;
				if (level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()) continue;
				Hound hound = Monsters.HOUND.create(level, EntitySpawnReason.EVENT);
				if (hound == null) break;
				hound.snapTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, random.nextFloat() * 360, 0);
				if (!level.noCollision(hound)) continue;
				hound.setTarget(player);
				level.addFreshEntity(hound);
				break;
			}
		}
		player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.extraction.wave").withStyle(ChatFormatting.RED));
	}
}
