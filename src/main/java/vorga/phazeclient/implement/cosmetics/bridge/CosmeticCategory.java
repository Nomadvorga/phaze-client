package vorga.phazeclient.implement.cosmetics.bridge;

public enum CosmeticCategory {
    WINGS("Крылья"),
    CAPE("Плащи"),
    HAT("Голова"),
    BODYWEAR("На тело"),
    PET("Питомцы"),
    GRAFFITI("Граффити");

    private final String title;

    CosmeticCategory(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
