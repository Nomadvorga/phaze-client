package vorga.phazeclient.implement.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.Item;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.block.BlockState;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import vorga.phazeclient.implement.features.modules.hud.ArmorHud;
import vorga.phazeclient.implement.features.modules.hud.DayCounterHud;
import vorga.phazeclient.implement.features.modules.hud.RectHudModule;
import vorga.phazeclient.implement.features.modules.hud.SessionTimeHud;
import vorga.phazeclient.implement.features.modules.hud.WailaHud;
import vorga.phazeclient.implement.features.modules.hud.TimeHud;
import vorga.phazeclient.implement.features.modules.other.AutoSprint;
import vorga.phazeclient.implement.features.modules.other.StreamerMode;
import java.util.Locale;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Game-state strings shown by HUD widgets. Version-sensitive Minecraft lookups (biomes, effects, ping, block info) live here. */
final class HudInfoText {
    private HudInfoText() {}

    static String formatDurabilityText(ArmorHud module, ItemStack stack) {
        if (!stack.isDamageable()) {
            return "-";
        }
        int remaining = Math.max(0, stack.getMaxDamage() - stack.getDamage());
        return module.formatDurability(remaining, stack.getMaxDamage());
    }

    static String getSprintHudText(MinecraftClient client) {
        // Sprint HUD now displays state without surrounding [] - the
        // qualifier sources (Key Held / AutoSprint / Vanilla) live in
        // round parens so the line reads naturally as one phrase.
        if (client.player == null || client.options == null) {
            return "Not Sprinting";
        }
        if (client.player.getAbilities().flying && client.options.sneakKey.isPressed()) {
            return "Flying Descending";
        }
        if (client.player.getAbilities().flying) {
            return "Flying";
        }
        if (client.options.sneakKey.isPressed() || client.player.isSneaking()) {
            return "Sneaking (Key Held)";
        }
        // AutoSprint label override: when the module is on AND its
        // {@code showInSprintHud} toggle is true, the label flips
        // from "Key Held" / "Vanilla" to "AutoSprint" so the user
        // can see the module is the source. We override the label
        // in any sprint-context (ground, in-air, falling) but never
        // when the player isn't sprinting at all - "Not Sprinting
        // (AutoSprint)" would be a lie.
        AutoSprint autoSprint = AutoSprint.getInstance();
        boolean autoSprintActive = autoSprint.isEnabled() && autoSprint.showInSprintHud.isValue();
        if (client.options.sprintKey.isPressed()) {
            return autoSprintActive ? "Sprinting (AutoSprint)" : "Sprinting (Key Held)";
        }
        if (client.player.isSprinting()) {
            return autoSprintActive ? "Sprinting (AutoSprint)" : "Sprinting (Vanilla)";
        }
        return "Not Sprinting";
    }

    static String getDayCounterText(MinecraftClient client) {
        DayCounterHud module = DayCounterHud.getInstance();
        if (client.world == null) {
            // Idle / pre-world state: keep the legacy "0 Days" text in
            // the default order, but still respect the user's
            // {@code reverseOrder} preference so the HUD doesn't flip
            // formats the moment the world finishes loading.
            return module.reverseOrder.isValue() ? "Day: 0" : "0 Days";
        }
        long days = Math.max(0L, client.world.getTime() / 24000L) + 1;
        if (module.reverseOrder.isValue()) {
            // Label-prefixed, plain-number form. We deliberately drop
            // the singular/plural distinction here because "Day: 1"
            // already reads naturally regardless of the count.
            return "Day: " + days;
        }
        return days + (days == 1L ? " Day" : " Days");
    }

    static String getTimeHudText(TimeHud module, MinecraftClient client) {
        LocalTime now = LocalTime.now();
        boolean is24 = module.hour24.isValue();
        boolean wantSeconds = module.showSeconds.isValue();
        String pattern;
        if (is24) {
            pattern = wantSeconds ? "H:mm:ss" : "H:mm";
        } else if (module.showAmPm.isValue()) {
            pattern = wantSeconds ? "h:mm:ss a" : "h:mm a";
        } else {
            pattern = wantSeconds ? "h:mm:ss" : "h:mm";
        }
        String time = now.format(DateTimeFormatter.ofPattern(pattern, Locale.US));
        if (module.showPhase.isValue() && client != null && client.world != null) {
            TimeHud.Phase phase = module.phaseForTime(client.world.getTimeOfDay());
            String label = module.colorPhase.isValue() ? phase.colorCode + phase.label + "§r" : phase.label;
            return time + " " + label;
        }
        return time;
    }

    static String getSessionText(SessionTimeHud module) {
        long elapsed = Math.max(0L, System.currentTimeMillis() - module.getSessionStartMs());
        long ms = elapsed % 1000L;
        long totalSec = elapsed / 1000L;
        long sec = totalSec % 60L;
        long totalMin = totalSec / 60L;
        long min = totalMin % 60L;
        long hours = totalMin / 60L;
        String mode = module.displayOption.getSelected();
        return switch (mode) {
            case "12h 34m 56s" -> String.format(Locale.US, "%dh %dm %ds", hours, min, sec);
            case "123.456s" -> String.format(Locale.US, "%.3fs", elapsed / 1000.0);
            case "12:34" -> String.format(Locale.US, "%02d:%02d", hours, min);
            case "12:34:56.789" -> String.format(Locale.US, "%02d:%02d:%02d.%03d", hours, min, sec, ms);
            default -> String.format(Locale.US, "%02d:%02d:%02d", hours, min, sec);
        };
    }

    static String wrapTextWithBrackets(String text, RectHudModule module) {
        if (module != null && !module.background.isValue() && module.showBrackets.isValue()) {
            return "[" + text + "]";
        }
        return text;
    }

    static String directionLabel(int deg, boolean showIntermediate) {
        return switch (deg) {
            case 0 -> "S";
            case 45 -> showIntermediate ? "SE" : "";
            case 90 -> "W";
            case 135 -> showIntermediate ? "SW" : "";
            case 180 -> "N";
            case 225 -> showIntermediate ? "NW" : "";
            case 270 -> "E";
            case 315 -> showIntermediate ? "NE" : "";
            default -> String.valueOf(deg);
        };
    }

    static String getBiomeName(MinecraftClient client, BlockPos pos) {
        if (client.world == null) {
            return "Unknown";
        }
        return client.world.getBiome(pos).getKey()
                .map(key -> toTitleCase(key.getValue().getPath()))
                .orElse("Unknown");
    }

    static int getBiomeColor(MinecraftClient client, BlockPos pos) {
        if (client.world == null) {
            return 0xFFFF55;
        }
        String biomeId = client.world.getBiome(pos).getKey()
                .map(key -> key.getValue().getPath())
                .orElse("");
        return switch (biomeId) {
            case "river", "frozen_river" -> 0x55AAFF;
            case "ocean", "deep_ocean", "cold_ocean", "deep_cold_ocean", "lukewarm_ocean", "deep_lukewarm_ocean" -> 0x3388DD;
            case "warm_ocean" -> 0x55CCEE;
            case "frozen_ocean", "deep_frozen_ocean" -> 0xAADDFF;
            case "desert" -> 0xFFDD55;
            case "beach" -> 0xFFEE88;
            case "snowy_beach" -> 0xDDEEFF;
            case "plains", "sunflower_plains" -> 0x88DD44;
            case "meadow" -> 0x77DD66;
            case "forest", "flower_forest" -> 0x44BB33;
            case "birch_forest", "old_growth_birch_forest" -> 0x88CC55;
            case "dark_forest" -> 0x336622;
            case "taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga" -> 0x559944;
            case "snowy_taiga" -> 0xDDEEFF;
            case "snowy_plains", "ice_spikes", "snowy_slopes", "frozen_peaks" -> 0xFFFFFF;
            case "jungle", "bamboo_jungle", "sparse_jungle" -> 0x33BB22;
            case "savanna", "savanna_plateau", "windswept_savanna" -> 0xBBAA44;
            case "badlands", "eroded_badlands", "wooded_badlands" -> 0xDD7733;
            case "swamp", "mangrove_swamp" -> 0x668855;
            case "mushroom_fields" -> 0xCC77CC;
            case "stony_shore", "windswept_hills", "windswept_gravelly_hills", "windswept_forest", "stony_peaks" -> 0xAAAAAA;
            case "grove" -> 0xBBDDAA;
            case "jagged_peaks" -> 0xCCDDEE;
            case "cherry_grove" -> 0xFFAACC;
            case "the_nether", "nether_wastes" -> 0xFF5533;
            case "soul_sand_valley" -> 0x774433;
            case "crimson_forest" -> 0xDD3344;
            case "warped_forest" -> 0x22BBAA;
            case "basalt_deltas" -> 0x888888;
            case "the_end", "end_highlands", "end_midlands", "end_barrens", "small_end_islands" -> 0xDDDD88;
            case "the_void" -> 0x555555;
            case "deep_dark" -> 0x224455;
            case "lush_caves", "dripstone_caves" -> 0x66BB55;
            default -> 0xFFFF55;
        };
    }

    static String toTitleCase(String value) {
        String[] parts = value.replace('_', ' ').split(" ");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.isEmpty() ? value : builder.toString();
    }

    static String getFacingShort(Direction direction) {
        return switch (direction) {
            case NORTH -> "N";
            case SOUTH -> "S";
            case EAST -> "E";
            case WEST -> "W";
            default -> "";
        };
    }

    static String getFacing8Point(float yaw) {
        float normalized = ((yaw % 360.0f) + 360.0f) % 360.0f;
        if (normalized >= 337.5f || normalized < 22.5f) return "S";
        if (normalized < 67.5f) return "SW";
        if (normalized < 112.5f) return "W";
        if (normalized < 157.5f) return "NW";
        if (normalized < 202.5f) return "N";
        if (normalized < 247.5f) return "NE";
        if (normalized < 292.5f) return "E";
        return "SE";
    }

    static String getDirectionXSign(String facing) {
        return switch (facing) {
            case "E", "NE", "SE" -> "+";
            case "W", "NW", "SW" -> "-";
            default -> "";
        };
    }

    static String getDirectionZSign(String facing) {
        return switch (facing) {
            case "S", "SE", "SW" -> "+";
            case "N", "NE", "NW" -> "-";
            default -> "";
        };
    }

    static int getServerPing(MinecraftClient client) {
        if (client.getNetworkHandler() == null || client.player == null) {
            return 0;
        }
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
        return entry == null ? 0 : Math.max(0, entry.getLatency());
    }

    static String getKeyLabel(Text keyText) {
        String value = keyText == null ? "" : keyText.getString();
        if (value == null || value.isBlank()) {
            return "?";
        }
        if (value.length() > 3) {
            return value.substring(0, 3).toUpperCase(Locale.ROOT);
        }
        return value.toUpperCase(Locale.ROOT);
    }

    static String getEffectName(StatusEffectInstance effect) {
        String name = effect.getEffectType().value().getName().getString();
        int amplifier = effect.getAmplifier();
        if (amplifier >= 1) {
            name = name + " " + toRoman(amplifier + 1);
        }
        return name;
    }

    static String toRoman(int number) {
        return switch (number) {
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(number);
        };
    }

    static String formatEffectDuration(StatusEffectInstance effect) {
        if (effect.isInfinite()) {
            // Infinity glyph (U+221E) - reads instantly as "permanent"
            // and lines up nicely under the effect name.
            return "\u221E";
        }
        int totalSeconds = Math.max(0, effect.getDuration() / 20);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    static String getWailaText(MinecraftClient client) {
        WailaHud waila = WailaHud.getInstance();
        if (client.player == null || client.world == null) {
            return waila.alwaysShow.isValue() ? "No target" : "";
        }

        HitResult hit = client.player.raycast(4.5, 0.0f, false);
        
        if (hit.getType() == HitResult.Type.MISS) {
            return waila.alwaysShow.isValue() ? "No target" : "";
        }

        StringBuilder sb = new StringBuilder();

        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = ((BlockHitResult)hit).getBlockPos();
            BlockState state = client.world.getBlockState(pos);
            Block block = state.getBlock();

            // Block name (uses Minecraft language)
            String blockName = block.getName().getString();
            sb.append(blockName);

            // Coordinates formatted as "(x, y, z)" with parentheses and
            // signed integers to match the screenshot the user shared
            // (e.g. "(-101, 71, -298)"). Single line, no axis labels -
            // the parens make the role unambiguous at a glance.
            // Streamer Mode veto: the Hide Coordinates toggle suppresses
            // this line even when WAILA's own Show Coordinates setting
            // is on, so the privacy feature wins over the per-HUD
            // preference the user last chose for normal play.
            if (waila.showCoordinates.isValue()
                    && !StreamerMode.getInstance().isHideCoordinatesEnabled()) {
                sb.append("\n(").append(pos.getX())
                  .append(", ").append(pos.getY())
                  .append(", ").append(pos.getZ())
                  .append(')');
            }

            // Optimal-tool hint. Walks the vanilla mineable BlockTags
            // (each block carries at most one of pickaxe/axe/shovel/hoe;
            // sword is the special-case for cobweb / leaves). The tier
            // prefix comes from the needs_X_tool tags so a wood pick
            // on diamond ore correctly shows "Diamond Pickaxe" instead
            // of the bare "Pickaxe" - users expect to see why their
            // current tool fails to harvest, not just the family.
            if (waila.showCorrectTool.isValue()) {
                sb.append("\nCorrect Tool: ").append(resolveCorrectTool(state));
            }

            // Break time on separate line. Label changed from "Break:"
            // to "Break Time:" per user request - keeps parity with
            // "Correct Tool:" wording above and is less ambiguous than
            // a bare "Break:" which reads like a verb.
            if (waila.showBreakTime.isValue()) {
                if (client.player.isCreative()) {
                    sb.append("\nBreak Time: Instant");
                } else {
                    float hardness = state.getHardness(client.world, pos);
                    if (hardness <= 0) {
                        sb.append("\nBreak Time: Instant");
                    } else {
                        float breakSpeed = client.player.getBlockBreakingSpeed(state);
                        if (breakSpeed <= 0) {
                            sb.append("\nBreak Time: Never");
                        } else {
                            boolean canHarvest = client.player.canHarvest(state);
                            int divider = canHarvest ? 30 : 100;
                            float breakTime = hardness / breakSpeed * divider / 20.0f;
                            if (breakTime < 0.05f) {
                                sb.append("\nBreak Time: Instant");
                            } else {
                                sb.append(String.format("\nBreak Time: %.2fs", breakTime));
                            }
                        }
                    }
                }
            }

        } else if (hit.getType() == HitResult.Type.ENTITY && waila.showEntities.isValue()) {
            Entity entity = ((EntityHitResult)hit).getEntity();
            String entityName = entity.getName().getString();
            sb.append(entityName);
            
            // Add entity type info
            EntityType<?> entityType = entity.getType();
            sb.append("\nType: ").append(entityType.getName().getString());
        }

        return sb.toString();
    }

    /**
     * Resolves the best-fit tool family for {@code state} by consulting
     * the vanilla {@link BlockTags} the block has been registered under.
     *
     * <p>Vanilla's data-driven mining system uses one of four mutually-
     * exclusive {@code *_MINEABLE} tags per block plus an orthogonal
     * tier tag ({@code NEEDS_DIAMOND_TOOL} / {@code NEEDS_IRON_TOOL} /
     * {@code NEEDS_STONE_TOOL}). We walk these in priority order:
     * <ol>
     *   <li>If a tier tag matches, prefix it ({@code "Diamond "}, etc.).
     *       Wooden / golden / netherite blocks have no needs-tag because
     *       wood is the implicit floor and netherite never requires a
     *       higher-than-diamond tier, so an unmarked block falls through
     *       to the bare family name.</li>
     *   <li>Pickaxe / axe / shovel / hoe in tag-registered order.
     *       Sword fires last because {@code SWORD_EFFICIENT} is a
     *       cosmetic speed-up (cobweb, leaves) rather than a true
     *       requirement; if a block were somehow in both pickaxe and
     *       sword tags, the pickaxe win is the user-expected answer.</li>
     *   <li>Default "Hand" - dirt, sand, gravel, plants, etc. don't
     *       benefit from any tool above bare-fists in vanilla.</li>
     * </ol>
     */
    static String resolveCorrectTool(BlockState state) {
        String tier = "";
        if (state.isIn(BlockTags.NEEDS_DIAMOND_TOOL)) {
            tier = "Diamond ";
        } else if (state.isIn(BlockTags.NEEDS_IRON_TOOL)) {
            tier = "Iron ";
        } else if (state.isIn(BlockTags.NEEDS_STONE_TOOL)) {
            tier = "Stone ";
        }
        if (state.isIn(BlockTags.PICKAXE_MINEABLE)) return tier + "Pickaxe";
        if (state.isIn(BlockTags.AXE_MINEABLE))     return tier + "Axe";
        if (state.isIn(BlockTags.SHOVEL_MINEABLE))  return tier + "Shovel";
        if (state.isIn(BlockTags.HOE_MINEABLE))     return tier + "Hoe";
        if (state.isIn(BlockTags.SWORD_EFFICIENT))  return "Sword";
        return "Hand";
    }

    static ItemStack getWailaIcon(MinecraftClient client) {
        WailaHud waila = WailaHud.getInstance();
        if (!waila.showIcon.isValue()) {
            return null;
        }
        
        if (client.player == null || client.world == null) {
            return null;
        }

        HitResult hit = client.player.raycast(4.5, 0.0f, false);
        
        if (hit.getType() == HitResult.Type.MISS) {
            // Return barrier block icon for "No target"
            return new ItemStack(Items.BARRIER);
        }

        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = ((BlockHitResult)hit).getBlockPos();
            BlockState state = client.world.getBlockState(pos);
            Block block = state.getBlock();
            
            // Get block as item stack for icon
            Item item = block.asItem();
            if (item != Items.AIR) {
                return new ItemStack(item);
            }
        } else if (hit.getType() == HitResult.Type.ENTITY && waila.showEntities.isValue()) {
            Entity entity = ((EntityHitResult)hit).getEntity();
            
            // For item frames, show the held item
            if (entity.getType() == EntityType.ITEM_FRAME || entity.getType() == EntityType.GLOW_ITEM_FRAME) {
                net.minecraft.entity.decoration.ItemFrameEntity itemFrame = (net.minecraft.entity.decoration.ItemFrameEntity)entity;
                ItemStack heldItem = itemFrame.getHeldItemStack();
                if (!heldItem.isEmpty()) {
                    return heldItem;
                }
            }
            
            // Try to get spawn egg for entity type
            try {
                String entityName = entity.getType().getName().getString().toLowerCase().replace(" ", "_");
                Identifier spawnEggId = Identifier.of("minecraft", entityName + "_spawn_egg");
                Item spawnEgg = Registries.ITEM.get(spawnEggId);
                if (spawnEgg != null && spawnEgg != Items.AIR) {
                    return new ItemStack(spawnEgg);
                }
            } catch (Exception e) {
                // Fall through to default
            }
            
            // Default to barrier icon for entities
            return new ItemStack(Items.BARRIER);
        }

        return null;
    }
}
