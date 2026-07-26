package vorga.phazeclient.base.util.math;

import com.mojang.blaze3d.systems.RenderSystem;
import lombok.experimental.UtilityClass;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

import static net.minecraft.util.math.MathHelper.lerp;

@UtilityClass
public class MathUtil {
    public double PI2 = Math.PI * 2;

    public boolean isHovered(double mouseX, double mouseY, double x, double y, double width, double height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public Vec3d closestPointToBox(Box box) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.getRenderTickCounter() == null) {
            return box.getCenter();
        }

        Vec3d eye = client.player.getCameraPosVec(client.getRenderTickCounter().getTickDelta(true));
        return new Vec3d(Math.min(Math.max(eye.x, box.minX), box.maxX), Math.min(Math.max(eye.y, box.minY), box.maxY), Math.min(Math.max(eye.z, box.minZ), box.maxZ));
    }

    public void scale(MatrixStack stack, float x, float y, float scale, Runnable data) {
        if (scale != 1) {
            float scale2 = 0.5F + scale / 2;
            stack.push();
            stack.translate(x, y, 0);
            stack.scale(scale2, scale2, 1);
            stack.translate(-x, -y, 0);
            setAlpha(scale, data);
            stack.pop();
        } else {
            data.run();
        }
    }

    public void scale(MatrixStack stack, float x, float y, float scaleX, float scaleY, Runnable data) {
        float sumScale = scaleX * scaleY;
        if (sumScale != 1) {
            stack.push();
            stack.translate(x, y, 0);
            stack.scale(scaleX, scaleY, 1);
            stack.translate(-x, -y, 0);
            setAlpha(sumScale, data);
            stack.pop();
        } else {
            data.run();
        }
    }

    public float blinking(double speed, float f) {
        float red = (float) (System.currentTimeMillis() % speed / (speed / f));
        if (red > f / 2) red = f - red;
        return red;
    }

    public float textScrolling(float textWidth) {
        int speed = (int) (textWidth * 75);
        return (float) MathHelper.clamp((System.currentTimeMillis() % speed * Math.PI / speed), 0, 1) * textWidth;
    }

    public void setAlpha(float alpha, Runnable data) {
        setColor(1.0F, 1.0F, 1.0F, alpha, data);
    }

    public void setColor(float red, float green, float blue, float alpha, Runnable data) {
        RenderSystem.setShaderColor(MathHelper.clamp(red, 0, 1), MathHelper.clamp(green, 0, 1), MathHelper.clamp(blue, 0, 1), MathHelper.clamp(alpha, 0, 1));
        data.run();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public double round(double num, double increment) {
        double rounded = Math.round(num / increment) * increment;
        return Math.round(rounded * 100.0) / 100.0;
    }

    public int floorNearestMulN(int x, int n) {
        return n * (int) Math.floor((double) x / (double) n);
    }

    public int getRed(int hex) {
        return hex >> 16 & 255;
    }

    public int getGreen(int hex) {
        return hex >> 8 & 255;
    }

    public int getBlue(int hex) {
        return hex & 255;
    }

    public int getAlpha(int hex) {
        return hex >> 24 & 255;
    }

    public int applyOpacity(int color, float opacity) {
        return ColorHelper.getArgb((int) (getAlpha(color) * opacity / 255), getRed(color), getGreen(color), getBlue(color));
    }

    public Vec3d cosSin(int i, int size, double width) {
        int index = Math.min(i, size);
        float cos = (float) (Math.cos(index * MathUtil.PI2 / size) * width);
        float sin = (float) (-Math.sin(index * MathUtil.PI2 / size) * width);
        return new Vec3d(cos, 0, sin);
    }

    public double absSinAnimation(double input) {
        return Math.abs(1 + Math.sin(input)) / 2;
    }

    public Vector3d interpolate(Vector3d prevPos, Vector3d pos) {
        return new Vector3d(interpolate(prevPos.x, pos.x), interpolate(prevPos.y, pos.y), interpolate(prevPos.z, pos.z));
    }

    public Vec3d interpolate(Vec3d prevPos, Vec3d pos) {
        return new Vec3d(interpolate(prevPos.x, pos.x), interpolate(prevPos.y, pos.y), interpolate(prevPos.z, pos.z));
    }

    public Vec3d interpolate(Entity entity) {
        if (entity == null) return Vec3d.ZERO;
        return new Vec3d(interpolate(entity.prevX, entity.getX()), interpolate(entity.prevY, entity.getY()), interpolate(entity.prevZ, entity.getZ()));
    }

    public float interpolate(float prev, float orig) {
        RenderTickCounter tickCounter = currentTickCounter();
        return tickCounter != null ? lerp(tickCounter.getTickDelta(false), prev, orig) : orig;
    }

    public double interpolate(double prev, double orig) {
        RenderTickCounter tickCounter = currentTickCounter();
        return tickCounter != null ? lerp(tickCounter.getTickDelta(false), prev, orig) : orig;
    }

    public float interpolateSmooth(double smooth, float prev, float orig) {
        RenderTickCounter tickCounter = currentTickCounter();
        return tickCounter != null ? (float) lerp(tickCounter.getLastDuration() / smooth, prev, orig) : orig;
    }

    public double interpolateSmooth(double smooth, double prev, double orig) {
        RenderTickCounter tickCounter = currentTickCounter();
        return tickCounter != null ? lerp(tickCounter.getLastDuration() / smooth, prev, orig) : orig;
    }

    private RenderTickCounter currentTickCounter() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null ? client.getRenderTickCounter() : null;
    }
}
