package dev.noclip.backrooms.core.locker;

import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Receives team research-pack unlock state on the client. */
public final class ResearchClient {
	private ResearchClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(ResearchPayload.TYPE,
				(payload, context) -> context.client().execute(() -> Research.setClientUnlocked(payload.unlocked())));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> Research.setClientUnlocked(List.of()));
	}
}
