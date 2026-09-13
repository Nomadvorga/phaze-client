package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.MultiSelectSetting;

public final class NoRender extends Module {
    private static final NoRender INSTANCE = new NoRender();

    public final BooleanSetting glowing = new BooleanSetting(
            "Glowing",
            "Hide the outline drawn around entities with the minecraft:glowing effect"
    ).setValue(true);

    public final BooleanSetting fire = new BooleanSetting(
            "Fire",
            "Hide the burning fire overlay drawn over the screen while on fire"
    ).setValue(true);

    public final BooleanSetting particles = new BooleanSetting(
            "Particles",
            "Skip every particle the client would spawn (ambient, weather, hit, etc.)"
    ).setValue(false);

    public final MultiSelectSetting particleTypes = new MultiSelectSetting(
            "Particle Types",
            "Pick which particle categories to skip. Click a label to toggle that category on / off."
    ).value(
            "Hit Particles", "Potion Particles", "Break Block Particles",
            "Splash Potion Particles", "Food Particles", "Mace Particles",
            "Scoreboard", "Boss Bar", "Rain"
    ).selected(
            "Hit Particles"
    );

    public final BooleanLike hitParticles = () -> particleTypes.getSelected().contains("Hit Particles");
    public final BooleanLike potionParticles = () -> particleTypes.getSelected().contains("Potion Particles");
    public final BooleanLike breakBlockParticles = () -> particleTypes.getSelected().contains("Break Block Particles");
    public final BooleanLike splashPotionParticles = () -> particleTypes.getSelected().contains("Splash Potion Particles");
    public final BooleanLike foodParticles = () -> particleTypes.getSelected().contains("Food Particles");
    public final BooleanLike maceParticles = () -> particleTypes.getSelected().contains("Mace Particles");
    public final BooleanLike scoreboard = () -> particleTypes.getSelected().contains("Scoreboard");
    public final BooleanLike bossBar = () -> particleTypes.getSelected().contains("Boss Bar");
    public final BooleanLike rain = () -> particleTypes.getSelected().contains("Rain");

    @FunctionalInterface
    public interface BooleanLike {
        boolean isValue();
    }

    private NoRender() {

        super("no_render", "Render Tweaks", ModuleCategory.OTHER);
        glowing.setFullWidth(true);
        fire.setFullWidth(true);
        particles.setFullWidth(true);

        particleTypes.setFullWidth(true);
        particleTypes.visible(() -> !particles.isValue());
        setup(glowing, fire, particles, particleTypes);
    }

    public static NoRender getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Render-pipeline tweaks: hide glowing, fire, particles, scoreboard, boss bar and rain";
    }

    @Override
    public String getIcon() {
        return "no_render.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }
}
