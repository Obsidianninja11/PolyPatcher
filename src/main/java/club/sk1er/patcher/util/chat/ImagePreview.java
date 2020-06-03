package club.sk1er.patcher.util.chat;

import club.sk1er.mods.core.util.Multithreading;
import club.sk1er.patcher.config.PatcherConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import scala.actors.Eval;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;

public class ImagePreview {

    private final HashMap<String, DynamicTexture> map = new HashMap<>();
    private String loaded;
    private int tex = -1;
    private int zLevel = 0;
    private int width = 100;
    private int height = 100;
    private BufferedImage image;

    @SubscribeEvent
    public void renderTickEvent(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!PatcherConfig.imagePreview) return;
        final GuiNewChat chat = Minecraft.getMinecraft().ingameGUI.getChatGUI();
        final IChatComponent chatComponent = chat.getChatComponent(Mouse.getX(), Mouse.getY());
        if (chatComponent != null) {
            final ChatStyle chatStyle = chatComponent.getChatStyle();
            final ClickEvent chatClickEvent = chatStyle.getChatClickEvent();
            if (chatStyle.getChatClickEvent() != null && chatClickEvent.getAction() == ClickEvent.Action.OPEN_URL) {
                handle(chatClickEvent.getValue());
            }
        }
    }

    private void handle(String value) {
        if (!value.startsWith("http")) {
            GlStateManager.deleteTexture(tex);
            tex = -1;
            return;
        }
        if (value.contains("imgur.com/")) {
            final String[] split = value.split("/");
            value = String.format("https://i.imgur.com/%s.png", split[split.length - 1]);
        }
        if (!value.equals(loaded)) {
            loaded = value;
            if (tex != -1)
                GlStateManager.deleteTexture(tex);
            tex = -1;
            String finalValue = value;
            Multithreading.runAsync(() -> {
                loadUrl(finalValue);
            });
        }
        if (this.image != null) {
            final DynamicTexture dynamicTexture = new DynamicTexture(image);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);

            dynamicTexture.updateDynamicTexture();
            this.tex = dynamicTexture.getGlTextureId();
            this.width = image.getWidth();
            this.height = image.getHeight();
            this.image = null;

        }
        if (tex != -1) {
            GlStateManager.pushMatrix();
            final ScaledResolution scaledResolution = new ScaledResolution(Minecraft.getMinecraft());
            final int scaleFactor = scaledResolution.getScaleFactor();
            final float i = 1 / ((float) scaleFactor);
            GlStateManager.scale(i, i, i);
            GlStateManager.enableTexture2D();
            GlStateManager.bindTexture(tex);
            GlStateManager.color(1, 1, 1, 1);
            float aspectRatio = width / (float) height;
            float scaleWidth = scaledResolution.getScaledWidth() * scaleFactor;
            if (!Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
                scaleWidth *= PatcherConfig.imagePreviewWidth / 100D;
            }
            if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)) {
                scaleWidth = this.width;
            }
            float maxWidth = scaleWidth;
            float height = maxWidth / aspectRatio;
            if (height > scaledResolution.getScaledHeight() * scaleFactor) {
                height = scaledResolution.getScaledHeight() * scaleFactor;
                maxWidth = height * aspectRatio;
            }
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);

            drawTexturedModalRect(0, 0, (int) maxWidth, (int) height);
            GlStateManager.popMatrix();
        }
    }

    public void drawTexturedModalRect(int x, int y, int width, int height) {
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        worldrenderer.begin(7, DefaultVertexFormats.POSITION_TEX);
        worldrenderer.pos(x, y + height, this.zLevel).tex(0, 1).endVertex();
        worldrenderer.pos((x + width), (y + height), this.zLevel).tex(1, 1).endVertex();
        worldrenderer.pos((x + width), (y), this.zLevel).tex(1, 0).endVertex();
        worldrenderer.pos((x), (y), this.zLevel).tex(0, 0).endVertex();
        tessellator.draw();
    }

    private void loadUrl(String url) {
        URL u = null;
        try {
            System.out.println("loading: " + url);
            u = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) u.openConnection();
            connection.setRequestMethod("GET");
            connection.setUseCaches(true);
            connection.addRequestProperty("User-Agent", "Patcher Image Previewer");
            connection.setReadTimeout(15000);
            connection.setConnectTimeout(15000);
            connection.setDoOutput(true);
            image = ImageIO.read(connection.getInputStream());
        } catch (IOException e) {
        }

    }
}
