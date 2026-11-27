package dev.noclip.backrooms.frontrooms;

import dev.noclip.backrooms.core.Home;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/**
 * The look of a night of Level 398 in the Frontrooms (Nights398): core.look.Look closes the fog in, dark red, and lets
 * the unlit go darker, as it does in the Backrooms; only the street lamps and the windows hold out.
 */
public final class Night398Client {
	private static boolean active;

	private Night398Client() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Night398Payload.TYPE, (payload, context) -> active = payload.active());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> active = false);
	}

	/** Whether the player is in the Frontrooms on a night of 398. */
	public static boolean active(Minecraft client) {
		return active && client.level != null && client.level.dimension() == Home.FRONTROOMS;
	}
}
