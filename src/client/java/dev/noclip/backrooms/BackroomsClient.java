package dev.noclip.backrooms;

import dev.noclip.backrooms.core.people.People;
import dev.noclip.backrooms.core.people.PersonModel;
import dev.noclip.backrooms.core.people.PersonRenderer;
import dev.noclip.backrooms.monsters.Monsters;
import dev.noclip.backrooms.monsters.arms.ArmsModel;
import dev.noclip.backrooms.monsters.arms.ArmsRenderer;
import dev.noclip.backrooms.monsters.clump.ClumpModel;
import dev.noclip.backrooms.monsters.clump.ClumpRenderer;
import dev.noclip.backrooms.monsters.crawler.CrawlerModel;
import dev.noclip.backrooms.monsters.crawler.CrawlerRenderer;
import dev.noclip.backrooms.monsters.duller.DullerModel;
import dev.noclip.backrooms.monsters.duller.DullerRenderer;
import dev.noclip.backrooms.monsters.hound.HoundModel;
import dev.noclip.backrooms.monsters.hound.HoundRenderer;
import dev.noclip.backrooms.monsters.nguithrxurh.NguithrxurhModel;
import dev.noclip.backrooms.monsters.nguithrxurh.NguithrxurhRenderer;
import dev.noclip.backrooms.monsters.shepherdsnake.ShepherdSnakeModel;
import dev.noclip.backrooms.monsters.shepherdsnake.ShepherdSnakeRenderer;
import dev.noclip.backrooms.monsters.shriek.ShriekModel;
import dev.noclip.backrooms.monsters.shriek.ShriekRenderer;
import dev.noclip.backrooms.monsters.skinstealer.SkinStealerModel;
import dev.noclip.backrooms.monsters.skinstealer.SkinStealerRenderer;
import dev.noclip.backrooms.monsters.smiler.SmilerModel;
import dev.noclip.backrooms.monsters.smiler.SmilerRenderer;
import dev.noclip.backrooms.monsters.wretch.WretchModel;
import dev.noclip.backrooms.monsters.wretch.WretchRenderer;
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
		ModelLayerRegistry.registerModelLayer(SmilerModel.LAYER, SmilerModel.RIG::layer);
		EntityRendererRegistry.register(Monsters.SMILER, SmilerRenderer::new);
		ModelLayerRegistry.registerModelLayer(SkinStealerModel.LAYER, SkinStealerModel.RIG::layer);
		EntityRendererRegistry.register(Monsters.SKIN_STEALER, SkinStealerRenderer::new);
		ModelLayerRegistry.registerModelLayer(ClumpModel.LAYER, ClumpModel.RIG::layer);
		EntityRendererRegistry.register(Monsters.CLUMP, ClumpRenderer::new);
		ModelLayerRegistry.registerModelLayer(DullerModel.LAYER, DullerModel.RIG::layer);
		EntityRendererRegistry.register(Monsters.DULLER, DullerRenderer::new);
		ModelLayerRegistry.registerModelLayer(ArmsModel.LAYER, ArmsModel.RIG::layer);
		EntityRendererRegistry.register(Monsters.ARMS, ArmsRenderer::new);
		ModelLayerRegistry.registerModelLayer(WretchModel.LAYER, WretchModel.RIG::layer);
		EntityRendererRegistry.register(Monsters.WRETCH, WretchRenderer::new);
		ModelLayerRegistry.registerModelLayer(ShriekModel.LAYER, ShriekModel.RIG::layer);
		EntityRendererRegistry.register(Monsters.SHRIEK, ShriekRenderer::new);
		ModelLayerRegistry.registerModelLayer(CrawlerModel.LAYER, CrawlerModel.RIG::layer);
		EntityRendererRegistry.register(Monsters.CRAWLER, CrawlerRenderer::new);
		ModelLayerRegistry.registerModelLayer(ShepherdSnakeModel.LAYER, ShepherdSnakeModel.RIG::layer);
		EntityRendererRegistry.register(Monsters.SHEPHERD_SNAKE, ShepherdSnakeRenderer::new);
		ModelLayerRegistry.registerModelLayer(NguithrxurhModel.LAYER, NguithrxurhModel.RIG::layer);
		EntityRendererRegistry.register(Monsters.NGUITHRXURH, NguithrxurhRenderer::new);
		ModelLayerRegistry.registerModelLayer(PersonModel.LAYER, PersonModel.RIG::layer);
		for (String look : People.LOOKS) EntityRendererRegistry.register(People.type(look), context -> new PersonRenderer(context, look));
	}
}
