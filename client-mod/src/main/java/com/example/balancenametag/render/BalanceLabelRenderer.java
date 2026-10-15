package com.example.balancenametag.render;

import com.example.balancenametag.BalanceNametagClient;
import com.example.balancenametag.config.BalanceConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Draws the balance line using the same billboard transform vanilla uses for nametags. */
public final class BalanceLabelRenderer {
    /** One text row below the vanilla line (font height 9 + 1px gap), in text-space pixels. */
    private static final float ROW_OFFSET = 10.0f;

    private BalanceLabelRenderer() {
    }

    public static void render(EntityRenderState state, boolean sneaking, String line, MatrixStack matrices,
                              VertexConsumerProvider vertexConsumers, int light) {
        if (line == null || line.isEmpty()) {
            return;
        }
        Vec3d labelPos = state.nameLabelPos;
        if (labelPos == null) {
            return;
        }
        BalanceConfig cfg = BalanceNametagClient.config();
        MinecraftClient mc = MinecraftClient.getInstance();

        matrices.push();
        try {
            matrices.translate(labelPos.x, labelPos.y + 0.5, labelPos.z);

            Vector3f pos = matrices.peek().getPositionMatrix().getTranslation(new Vector3f());
            float distance = pos.length();
            if (distance > cfg.maxDistance) {
                return;
            }

            matrices.multiply(mc.gameRenderer.getCamera().getRotation());
            matrices.scale(0.025f, -0.025f, 0.025f);

            // Move down one row, then scale about the top-centre of that row so growth never
            // pushes the line up into the vanilla nametag.
            matrices.translate(0.0f, ROW_OFFSET, 0.0f);
            if (cfg.distanceScaling) {
                float s = MathHelper.clamp(1.0f + (distance - 8.0f) * 0.06f, 1.0f, cfg.maxScale);
                matrices.scale(s, s, 1.0f);
            }

            TextRenderer tr = mc.textRenderer;
            Text text = Text.literal(line);
            Matrix4f matrix = matrices.peek().getPositionMatrix();
            float x = -tr.getWidth(text) / 2.0f;
            int background = (int) (mc.options.getTextBackgroundOpacity(0.25f) * 255.0f) << 24;
            int rgb = cfg.rgb();
            int dim = 0x80000000 | rgb;
            int full = 0xFF000000 | rgb;

            boolean seeThrough = !sneaking;
            tr.draw(text, x, 0.0f, dim, false, matrix, vertexConsumers,
                    seeThrough ? TextRenderer.TextLayerType.SEE_THROUGH : TextRenderer.TextLayerType.NORMAL,
                    background, light);
            if (seeThrough) {
                tr.draw(text, x, 0.0f, full, false, matrix, vertexConsumers,
                        TextRenderer.TextLayerType.NORMAL, 0, light);
            }
        } finally {
            matrices.pop();
        }
    }
}
