package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.core.LevelExits;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * "Enter the doors and corridors in the right order and you reach the Hub": here the glass door (22), then the
 * concrete door (1.2), then the hole to the attic (19), each within ten minutes of the one before.
 */
final class HubRoute {
	private static final String[] ORDER = {"22", "1.2", "19"};
	private static final int WINDOW = 10 * 60 * 20;
	private static final Map<UUID, int[]> PROGRESS = new HashMap<>();
	private static int completions;

	/** How many times the route has been completed since the game started, for tests. */
	static int completions() {
		return completions;
	}

	private HubRoute() {
	}

	static void register() {
		LevelExits.onUse(HubRoute::used);
	}

	private static void used(ServerPlayer player, String level) {
		if (level.equals("hub")) return;
		int now = player.level().getServer().getTickCount();
		int[] progress = PROGRESS.computeIfAbsent(player.getUUID(), id -> new int[] {0, 0});
		if (progress[0] > 0 && now - progress[1] > WINDOW) progress[0] = 0;
		if (level.equals(ORDER[progress[0]])) progress[0]++;
		else progress[0] = level.equals(ORDER[0]) ? 1 : 0;
		progress[1] = now;
		if (progress[0] == ORDER.length) {
			progress[0] = 0;
			completions++;
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.hub_route"));
			LevelExits.use(player, "hub");
		}
	}
}
