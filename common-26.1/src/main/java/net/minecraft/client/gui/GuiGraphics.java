package net.minecraft.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionfc;

/**
 * Thin compatibility shim for older immediate-mode GUI code while the 26.1
 * UI layer is migrated to GuiGraphicsExtractor.
 */
public class GuiGraphics
{
    private final GuiGraphicsExtractor extractor;
    private final PoseCompat pose = new PoseCompat();

    public GuiGraphics(GuiGraphicsExtractor extractor)
    {
        this.extractor = extractor;
    }

    public PoseCompat pose()
    {
        return pose;
    }

    public void fill(int left, int top, int right, int bottom, int colour)
    {
        extractor.fill(left, top, right, bottom, colour);
    }

    public void drawString(Font font, String text, int x, int y, int colour)
    {
        extractor.text(font, text, x, y, colour);
    }

    public void drawString(Font font, String text, int x, int y, int colour, boolean shadow)
    {
        extractor.text(font, text, x, y, colour, shadow);
    }

    public void drawString(Font font, Component text, int x, int y, int colour)
    {
        extractor.text(font, text, x, y, colour);
    }

    public void renderOutline(int x, int y, int width, int height, int colour)
    {
        extractor.outline(x, y, width, height, colour);
    }

    public void blit(Identifier texture, int x, int y, int u, int v, int width, int height)
    {
        extractor.blit(texture, x, y, width, height, u / 256F, v / 256F, width / 256F, height / 256F);
    }

    public void blit(TextureAtlasSprite sprite, int x, int y, int width, int height)
    {
        extractor.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height);
    }

    public GuiGraphicsExtractor extractor()
    {
        return extractor;
    }

    public static class PoseCompat
    {
        public void pushPose() {}
        public void popPose() {}
        public void translate(double x, double y, double z) {}
        public void translate(float x, float y, float z) {}
        public void scale(float x, float y, float z) {}
        public void mulPose(Quaternionfc quaternion) {}
    }
}
