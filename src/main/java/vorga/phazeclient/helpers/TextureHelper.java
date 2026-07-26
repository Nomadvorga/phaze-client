package vorga.phazeclient.helpers;

import net.minecraft.util.Identifier;

public class TextureHelper {
    public static final Identifier MOD_ICONS = Identifier.of("phaze", "textures/icons.png");
    public static final Identifier FOOD_EMPTY_TEXTURE = Identifier.ofVanilla("hud/food_empty");
    public static final Identifier FOOD_HALF_TEXTURE = Identifier.ofVanilla("hud/food_half");
    public static final Identifier FOOD_FULL_TEXTURE = Identifier.ofVanilla("hud/food_full");
    public static final Identifier FOOD_EMPTY_HUNGER_TEXTURE = Identifier.ofVanilla("hud/food_empty_hunger");
    public static final Identifier FOOD_HALF_HUNGER_TEXTURE = Identifier.ofVanilla("hud/food_half_hunger");
    public static final Identifier FOOD_FULL_HUNGER_TEXTURE = Identifier.ofVanilla("hud/food_full_hunger");

    public enum FoodType {
        EMPTY,
        HALF,
        FULL,
    }

    public static Identifier getFoodTexture(boolean isRotten, FoodType type) {
        return switch (type) {
            case EMPTY -> FOOD_EMPTY_TEXTURE;
            case HALF -> isRotten ? FOOD_HALF_HUNGER_TEXTURE : FOOD_HALF_TEXTURE;
            case FULL -> isRotten ? FOOD_FULL_HUNGER_TEXTURE : FOOD_FULL_TEXTURE;
        };
    }
}
