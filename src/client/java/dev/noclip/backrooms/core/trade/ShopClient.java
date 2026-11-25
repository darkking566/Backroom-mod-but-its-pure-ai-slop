package dev.noclip.backrooms.core.trade;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Opens a merchant's shop when the server says so. */
public final class ShopClient {
	private ShopClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(ShopOpenPayload.TYPE, (payload, context) -> {
			ShopRole role = ShopRole.byId(payload.role());
			if (role != null) context.client().gui.setScreen(new ShopScreen(role, payload.offers()));
		});
	}
}
