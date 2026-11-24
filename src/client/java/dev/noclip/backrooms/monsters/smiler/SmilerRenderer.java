package dev.noclip.backrooms.monsters.smiler;

import dev.noclip.backrooms.BackroomsMod;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.AnimationState;

/** Renders the 3D Smiler with a full-bright EyesLayer so its glowing eyes and grin pierce blackout darkness. */
public final class SmilerRenderer extends MobRenderer<Smiler, SmilerRenderer.State, SmilerModel> {
	private static final Identifier TEXTURE = BackroomsMod.id("textures/entity/smiler.png");
	private static final RenderType EYES = RenderTypes.eyes(BackroomsMod.id("textures/entity/smiler_eyes.png"));

	public SmilerRenderer(EntityRendererProvider.Context context) {
		super(context, new SmilerModel(context.bakeLayer(SmilerModel.LAYER)), 0.45F);
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
	public void extractRenderState(Smiler smiler, State state, float partialTick) {
		super.extractRenderState(smiler, state, partialTick);
		state.enraged = smiler.isEnraged();
		state.bite.copyFrom(smiler.biteAnimation);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}

	public static final class State extends LivingEntityRenderState {
		boolean enraged;
		final AnimationState bite = new AnimationState();
	}
}
