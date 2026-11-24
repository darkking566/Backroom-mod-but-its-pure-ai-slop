package dev.noclip.backrooms.monsters.deathmoth;

import dev.noclip.backrooms.BackroomsMod;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.AnimationState;

public final class DeathmothRenderer extends MobRenderer<Deathmoth, DeathmothRenderer.State, DeathmothModel> {
	private static final Identifier TEXTURE = BackroomsMod.id("textures/entity/deathmoth.png");
	private static final RenderType EYES = RenderTypes.eyes(BackroomsMod.id("textures/entity/deathmoth_eyes.png"));

	public DeathmothRenderer(EntityRendererProvider.Context context) {
		super(context, new DeathmothModel(context.bakeLayer(DeathmothModel.LAYER)), 0.65F);
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
	public void extractRenderState(Deathmoth mob, State state, float partialTick) {
		super.extractRenderState(mob, state, partialTick);
		state.aiState = mob.aiState();
		state.spit.copyFrom(mob.spitAnimation);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}

	public static final class State extends LivingEntityRenderState {
		int aiState;
		final AnimationState spit = new AnimationState();
	}
}
