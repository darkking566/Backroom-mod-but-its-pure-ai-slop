package dev.noclip.backrooms;

import dev.noclip.backrooms.core.people.People;
import dev.noclip.backrooms.core.people.PersonModel;
import dev.noclip.backrooms.core.people.PersonRenderer;
import dev.noclip.backrooms.monsters.Monsters;
import dev.noclip.backrooms.monsters.hound.HoundModel;
import dev.noclip.backrooms.monsters.hound.HoundRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

/**
 * Client-only setup: how monsters look. Code here lives in src/client and only runs in the game client,
 * never on a dedicated server.
 */
public final class BackroomsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ModelLayerRegistry.registerModelLayer(HoundModel.LAYER, HoundModel.RIG::layer);
		EntityRendererRegistry.register(Monsters.HOUND, HoundRenderer::new);
		ModelLayerRegistry.registerModelLayer(PersonModel.LAYER, PersonModel.RIG::layer);
		for (String look : People.LOOKS) EntityRendererRegistry.register(People.type(look), context -> new PersonRenderer(context, look));
	}
}
