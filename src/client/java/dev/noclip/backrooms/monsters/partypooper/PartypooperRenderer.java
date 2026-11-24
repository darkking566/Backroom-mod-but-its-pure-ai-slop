package dev.noclip.backrooms.monsters.partypooper;

import dev.noclip.backrooms.BackroomsMod;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

public final class PartypooperRenderer extends MobRenderer<Partypooper, PartypooperRenderer.State, PartypooperModel> {
	private static final Identifier TEXTURE = BackroomsMod.id("textures/entity/partypooper.png");
	private static final RenderType EYES = RenderTypes.eyes(BackroomsMod.id("textures/entity/partypooper_eyes.png"));

	public PartypooperRenderer(EntityRendererProvider.Context context) {
		super(context, new PartypooperModel(context.bakeLayer(PartypooperModel.LAYER)), 0.44F);
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
	public void extractRenderState(Partypooper mob, State state, float partialTick) {
		super.extractRenderState(mob, state, partialTick);
		state.aiState = mob.aiState();
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}

	public static final class State extends LivingEntityRenderState {
		int aiState;
	}
}
