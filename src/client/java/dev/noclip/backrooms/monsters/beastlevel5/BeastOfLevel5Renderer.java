package dev.noclip.backrooms.monsters.beastlevel5;

import dev.noclip.backrooms.BackroomsMod;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.AnimationState;

public final class BeastOfLevel5Renderer extends MobRenderer<BeastOfLevel5, BeastOfLevel5Renderer.State, BeastOfLevel5Model> {
	private static final Identifier TEXTURE = BackroomsMod.id("textures/entity/beast_level5.png");
	private static final RenderType EYES = RenderTypes.eyes(BackroomsMod.id("textures/entity/beast_level5_eyes.png"));

	public BeastOfLevel5Renderer(EntityRendererProvider.Context context) {
		super(context, new BeastOfLevel5Model(context.bakeLayer(BeastOfLevel5Model.LAYER)), 0.62F);
		addLayer(new EyesLayer<>(this) {
			@Override
			public RenderType renderType() {
				return EYES;
			}
		});
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(BeastOfLevel5 mob, State state, float partialTick) {
		super.extractRenderState(mob, state, partialTick);
		state.phase2 = mob.isPhase2();
		state.lashing = mob.isLashing();
		state.unfurl.copyFrom(mob.unfurlAnimation);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}

	public static final class State extends LivingEntityRenderState {
		boolean phase2;
		boolean lashing;
		final AnimationState unfurl = new AnimationState();
	}
}
