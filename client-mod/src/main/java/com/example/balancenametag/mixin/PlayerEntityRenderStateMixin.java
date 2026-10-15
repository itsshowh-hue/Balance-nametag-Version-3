package com.example.balancenametag.mixin;

import com.example.balancenametag.render.BalanceRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PlayerEntityRenderState.class)
public class PlayerEntityRenderStateMixin implements BalanceRenderState {
    @Unique
    private String balancenametag$balanceText;

    @Override
    public String balancenametag$getBalanceText() {
        return balancenametag$balanceText;
    }

    @Override
    public void balancenametag$setBalanceText(String text) {
        this.balancenametag$balanceText = text;
    }
}
