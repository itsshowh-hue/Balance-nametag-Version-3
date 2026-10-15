package com.example.balancenametag.mixin;

import com.example.balancenametag.BalanceNametagClient;
import com.example.balancenametag.render.BalanceLabelRenderer;
import com.example.balancenametag.render.BalanceRenderState;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {

    /** The render state has no UUID, so resolve the balance here where the entity is available. */
    @Inject(method = "updateRenderState(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",
            at = @At("TAIL"))
    private void balancenametag$captureBalance(AbstractClientPlayerEntity player, PlayerEntityRenderState state,
                                               float tickDelta, CallbackInfo ci) {
        String text = null;
        try {
            text = BalanceNametagClient.manager().getDisplayText(player);
        } catch (Throwable t) {
            BalanceNametagClient.LOGGER.error("Failed to resolve balance text", t);
        }
        ((BalanceRenderState) state).balancenametag$setBalanceText(text);
    }

    /** Runs after vanilla has drawn the username (and any below-name score). */
    @Inject(method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("TAIL"))
    private void balancenametag$renderBalance(PlayerEntityRenderState state, Text label, MatrixStack matrices,
                                              VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        try {
            String text = ((BalanceRenderState) state).balancenametag$getBalanceText();
            BalanceLabelRenderer.render(state, state.sneaking, text, matrices, vertexConsumers, light);
        } catch (Throwable t) {
            BalanceNametagClient.LOGGER.error("Failed to render balance line", t);
        }
    }
}
