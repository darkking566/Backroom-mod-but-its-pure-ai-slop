package dev.noclip.backrooms.monsters.crawler;

import dev.noclip.backrooms.BackroomsMod;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.AnimationState;

/** Renders the 3D white-mold fungal Crawler. */
public final class CrawlerRenderer extends MobRenderer<Crawler, CrawlerRenderer.State, CrawlerModel> {
	private static final Identifier TEXTURE = BackroomsMod.id("textures/entity/crawler.png");

	public CrawlerRenderer(EntityRendererProvider.Context context) {
		super(context, new CrawlerModel(context.bakeLayer(CrawlerModel.LAYER)), 0.72F);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(Crawler mob, State state, float partialTick) {
		super.extractRenderState(mob, state, partialTick);
		state.spore.copyFrom(mob.sporeAnimation);
		state.lunge.copyFrom(mob.lungeAnimation);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}

	public static final class State extends LivingEntityRenderState {
		final AnimationState spore = new AnimationState();
		final AnimationState lunge = new AnimationState();
	}
}
