package dev.noclip.backrooms.core.rpg;

import dev.noclip.backrooms.core.floors.Floors;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;

/**
 * Experience for surviving rather than killing (docs/project/1-gameplay/README.md): a level not yet seen this trip, shaking off
 * something that was hunting you. Settling at a base and walking out alive are paid by core.trip.Trips, contracts by
 * core.guild.Contracts; killing pays little (PlayerRpg).
 */
public final class SurvivalXp {
	static final int NEW_LEVEL = 40;
	static final int SHOOK_OFF = 20;
	/** Hunted for at least this many seconds, then left alone for this many, counts as getting away. */
	static final int HUNTED_FOR = 5, CALM_FOR = 10;
	static final double HUNTERS_WITHIN = 32.0;

	/** Per player: seconds hunted in the current chase, seconds since anything was hunting them. */
	private static final Map<UUID, int[]> CHASES = new HashMap<>();

	private SurvivalXp() {
	}

	public static void register() {
		Floors.onArrive((player, level, run, newLevel) -> {
			if (newLevel) PlayerRpg.grantXp(player, NEW_LEVEL);
		});
		ServerTickEvents.END_SERVER_TICK.register(SurvivalXp::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) return;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isAlive() || player.isCreative() || player.isSpectator() || !PlayerRpg.inBackrooms(player)) {
				CHASES.remove(player.getUUID());
				continue;
			}
			boolean hunted = !player.level().getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(HUNTERS_WITHIN),
					monster -> monster.getTarget() == player).isEmpty();
			int[] chase = CHASES.computeIfAbsent(player.getUUID(), id -> new int[2]);
			if (hunted) {
				chase[0]++;
				chase[1] = 0;
			} else if (chase[0] >= HUNTED_FOR && ++chase[1] >= CALM_FOR) {
				chase[0] = 0;
				chase[1] = 0;
				PlayerRpg.grantXp(player, SHOOK_OFF);
				player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.rpg.shook_off").withStyle(ChatFormatting.GRAY));
			} else if (chase[0] < HUNTED_FOR) {
				chase[0] = 0;
			}
		}
	}
}
