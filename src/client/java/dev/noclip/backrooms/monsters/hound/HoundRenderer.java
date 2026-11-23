package dev.noclip.backrooms.monsters.hound;

import dev.noclip.backrooms.BackroomsMod;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.AnimationState;

/** Draws the Hound; its eyes catch what little light there is, so they show in a blackout. */
public final class HoundRenderer extends MobRenderer<Hound, HoundRenderer.State, HoundModel> {
	private static final Identifier TEXTURE = BackroomsMod.id("textures/entity/hound.png");
	private static final RenderType EYES = RenderTypes.eyes(BackroomsMod.id("textures/entity/hound_eyes.png"));

	public HoundRenderer(EntityRendererProvider.Context context) {
		super(context, new HoundModel(context.bakeLayer(HoundModel.LAYER)), 0.6F);
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
	public void extractRenderState(Hound hound, State state, float partialTick) {
		super.extractRenderState(hound, state, partialTick);
		state.lunge.copyFrom(hound.lungeAnimation);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}

	public static final class State extends LivingEntityRenderState {
		final AnimationState lunge = new AnimationState();
	}
}
