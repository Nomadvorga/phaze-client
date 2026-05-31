package me.zyouime.hitcolor.screen.widget;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import me.zyouime.hitcolor.render.RenderHelper;
import me.zyouime.hitcolor.render.font.FontRenderers;
import me.zyouime.hitcolor.screen.widget.api.AbstractWidget;
import net.minecraft.class_332;
import net.minecraft.class_4587;

public class CustomTooltip {
   private final List<String> lines = new ArrayList();
   private final Color backgroundColor;
   private final Color textColor;
   private final Color arcColor;
   private final float width;
   private final AbstractWidget parent;

   private CustomTooltip(List<String> lines, Color backgroundColor, Color arcColor, Color textColor, float width, AbstractWidget parent) {
      this.lines.addAll(lines);
      this.backgroundColor = backgroundColor;
      this.textColor = textColor;
      this.width = width;
      this.parent = parent;
      this.arcColor = arcColor;
   }

   public void render(class_332 context, double mouseX, double mouseY) {
      if (this.isMouseOver(mouseX, mouseY)) {
         class_4587 matrixStack = context.method_51448();
         float lineHeight = 8.0F;
         float height = (float)this.lines.size() * lineHeight + 2.0F;
         float tooltipX = (float)mouseX + 7.0F;
         float tooltipY = (float)mouseY;
         float mostLongLine = (float)this.lines.stream().mapToDouble((string) -> (double)(FontRenderers.mainFont.getStringWidth(string) + 7.0F)).max().orElse((double)0.0F);
         float renderWidth = Math.min(this.width, mostLongLine);
         RenderHelper.drawArc(matrixStack, tooltipX - 1.0F, tooltipY - 1.0F, renderWidth + 2.0F, height + 2.0F, 4.0F, 1.0F, this.arcColor);
         RenderHelper.drawRoundedRect(matrixStack, tooltipX, tooltipY, renderWidth, height, 3.0F, this.backgroundColor);
         float lineOffset = 0.0F;

         for(String line : this.lines) {
            FontRenderers.mainFont.drawString(matrixStack, line, (double)(tooltipX + 2.0F), (double)(tooltipY + 2.5F + lineOffset), this.textColor.getRGB());
            lineOffset += lineHeight;
         }

      }
   }

   public boolean isMouseOver(double mouseX, double mouseY) {
      return mouseX >= (double)this.parent.getX() && mouseX <= (double)(this.parent.getX() + this.parent.getWidth()) && mouseY >= (double)this.parent.getY() && mouseY <= (double)(this.parent.getY() + this.parent.getHeight());
   }

   public static CustomTooltip of(AbstractWidget parent, String tooltip, float width) {
      return (new TooltipBuilder(parent, width)).tooltip(tooltip).build();
   }

   public static TooltipBuilder builder(AbstractWidget parent, float width) {
      return new TooltipBuilder(parent, width);
   }

   public static class TooltipBuilder {
      private final float width;
      private final AbstractWidget parent;
      private Color backgroundColor;
      private Color arcColor;
      private Color textColor;
      private List<String> lines;

      public TooltipBuilder(AbstractWidget parent, float width) {
         this.backgroundColor = Color.LIGHT_GRAY;
         this.arcColor = Color.BLACK;
         this.textColor = Color.BLACK;
         this.lines = new ArrayList();
         this.width = width;
         this.parent = parent;
      }

      public TooltipBuilder backgroundColor(Color backgroundColor) {
         this.backgroundColor = backgroundColor;
         return this;
      }

      public TooltipBuilder arcColor(Color arcColor) {
         this.arcColor = arcColor;
         return this;
      }

      public TooltipBuilder textColor(Color textColor) {
         this.textColor = textColor;
         return this;
      }

      public TooltipBuilder tooltip(String tooltip) {
         String[] split = tooltip.split("\n");
         List<String> lines = new ArrayList();

         for(String string : split) {
            String[] words = string.split(" ");
            StringBuilder currentLine = new StringBuilder();

            for(String word : words) {
               float wordW = FontRenderers.mainFont.getStringWidth(word + " ");
               float currentW = FontRenderers.mainFont.getStringWidth(currentLine.toString());
               if (currentW + wordW < this.width) {
                  currentLine.append(word).append(" ");
               } else {
                  lines.add(currentLine.toString().trim());
                  currentLine = (new StringBuilder(word)).append(" ");
               }
            }

            if (!currentLine.isEmpty()) {
               lines.add(currentLine.toString().trim());
            }
         }

         this.lines = lines;
         return this;
      }

      public CustomTooltip build() {
         return new CustomTooltip(this.lines, this.backgroundColor, this.arcColor, this.textColor, this.width, this.parent);
      }
   }
}
