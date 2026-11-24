package dev.noclip.backrooms.monsters.skinstealer;

import dev.noclip.backrooms.BackroomsMod;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.AnimationState;

/** Draws the 3D Skin-Stealer with faint eerie pinprick eyes visible in dim halls. */
public final class SkinStealerRenderer extends MobRenderer<SkinStealer, SkinStealerRenderer.State, SkinStealerModel> {
	private static final Identifier TEXTURE = BackroomsMod.id("textures/entity/skin_stealer.png");
	private static final RenderType EYES = RenderTypes.eyes(BackroomsMod.id("textures/entity/skin_stealer_eyes.png"));

	public SkinStealerRenderer(EntityRendererProvider.Context context) {
		super(context, new SkinStealerModel(context.bakeLayer(SkinStealerModel.LAYER)), 0.5F);
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
	public void extractRenderState(SkinStealer mob, State state, float partialTick) {
		super.extractRenderState(mob, state, partialTick);
		state.mode = mob.mode();
		state.reveal.copyFrom(mob.revealAnimation);
		state.slash.copyFrom(mob.slashAnimation);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}

	public static final class State extends LivingEntityRenderState {
		int mode;
		final AnimationState reveal = new AnimationState();
		final AnimationState slash = new AnimationState();
	}
}
