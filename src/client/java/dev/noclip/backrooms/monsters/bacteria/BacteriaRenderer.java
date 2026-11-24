package dev.noclip.backrooms.monsters.bacteria;

import dev.noclip.backrooms.BackroomsMod;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.AnimationState;

public final class BacteriaRenderer extends MobRenderer<Bacteria, BacteriaRenderer.State, BacteriaModel> {
	private static final Identifier TEXTURE = BackroomsMod.id("textures/entity/bacteria.png");
	private static final RenderType EYES = RenderTypes.eyes(BackroomsMod.id("textures/entity/bacteria_eyes.png"));

	public BacteriaRenderer(EntityRendererProvider.Context context) {
		super(context, new BacteriaModel(context.bakeLayer(BacteriaModel.LAYER)), 0.5F);
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
	public void extractRenderState(Bacteria mob, State state, float partialTick) {
		super.extractRenderState(mob, state, partialTick);
		state.aiState = mob.aiState();
		state.lunge.copyFrom(mob.lungeAnimation);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}

	public static final class State extends LivingEntityRenderState {
		int aiState;
		final AnimationState lunge = new AnimationState();
	}
}
