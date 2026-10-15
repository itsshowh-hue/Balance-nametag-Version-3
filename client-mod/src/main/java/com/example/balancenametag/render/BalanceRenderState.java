package com.example.balancenametag.render;

/** Duck interface mixed into PlayerEntityRenderState to carry the line to draw. */
public interface BalanceRenderState {
    String balancenametag$getBalanceText();

    void balancenametag$setBalanceText(String text);
}
