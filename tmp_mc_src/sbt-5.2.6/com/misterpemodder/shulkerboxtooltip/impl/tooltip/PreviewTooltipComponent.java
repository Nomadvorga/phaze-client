package com.misterpemodder.shulkerboxtooltip.impl.tooltip;

import com.misterpemodder.shulkerboxtooltip.api.PreviewContext;
import com.misterpemodder.shulkerboxtooltip.api.provider.PreviewProvider;
import net.minecraft.class_5632;

public record PreviewTooltipComponent(PreviewProvider provider, PreviewContext context) implements class_5632 {
}
