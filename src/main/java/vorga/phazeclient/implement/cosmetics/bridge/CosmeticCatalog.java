package vorga.phazeclient.implement.cosmetics.bridge;

import java.util.*;

public final class CosmeticCatalog {
    private static final List<CosmeticEntry> ALL = new ArrayList<>();
    private static final Map<Integer, CosmeticEntry> BY_ID = new HashMap<>();
    private static final Map<CosmeticCategory, List<CosmeticEntry>> BY_CATEGORY = new EnumMap<>(CosmeticCategory.class);

    static {
        add(new CosmeticEntry(25, CosmeticCategory.WINGS, "Крылья - Aurum", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/wings/25/model.json", "phazecosmetics:cosmetics/wings/25/texture.png", "phazecosmetics:cosmetics/wings/25/preview.png", 64, 64, 1, 64, 64, 1, 149));
        add(new CosmeticEntry(61, CosmeticCategory.WINGS, "Крылья - Angel", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/wings/61/model.json", "phazecosmetics:cosmetics/wings/61/texture.png", "phazecosmetics:cosmetics/wings/61/preview.png", 516, 774, 1, 516, 774, 1, 219));
        add(new CosmeticEntry(62, CosmeticCategory.WINGS, "Крылья - Dragon", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/wings/62/model.json", "phazecosmetics:cosmetics/wings/62/texture.png", "phazecosmetics:cosmetics/wings/62/preview.png", 64, 64, 1, 64, 64, 1, 199));
        add(new CosmeticEntry(63, CosmeticCategory.WINGS, "Крылья - Diamond", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/wings/63/model.json", "phazecosmetics:cosmetics/wings/63/texture.png", "phazecosmetics:cosmetics/wings/63/preview.png", 128, 128, 1, 128, 128, 1, 149));
        add(new CosmeticEntry(64, CosmeticCategory.WINGS, "Крылья - Spider", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/wings/64/model.json", "phazecosmetics:cosmetics/wings/64/texture.png", "phazecosmetics:cosmetics/wings/64/preview.png", 256, 256, 1, 256, 256, 1, 249));
        add(new CosmeticEntry(65, CosmeticCategory.WINGS, "Крылья - Easter", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/wings/65/model.json", "phazecosmetics:cosmetics/wings/65/texture.png", "phazecosmetics:cosmetics/wings/65/preview.png", 128, 128, 1, 128, 128, 1, 399));
        add(new CosmeticEntry(82, CosmeticCategory.WINGS, "Крылья - Fluger", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/wings/82/model.json", "phazecosmetics:cosmetics/wings/82/texture.png", "phazecosmetics:cosmetics/wings/82/preview.png", 16, 16, 1, 16, 16, 1, 349));
        add(new CosmeticEntry(86, CosmeticCategory.WINGS, "Трезубец - Броя", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/wings/86/model.json", "phazecosmetics:cosmetics/wings/86/texture.png", "phazecosmetics:cosmetics/wings/86/preview.png", 32, 32, 1, 32, 32, 1, 349));
        add(new CosmeticEntry(91, CosmeticCategory.WINGS, "Крылья - Аквыч", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/wings/91/model.json", "phazecosmetics:cosmetics/wings/91/texture.png", "phazecosmetics:cosmetics/wings/91/preview.png", 64, 640, 1, 64, 640, 1, 349));
        add(new CosmeticEntry(99, CosmeticCategory.WINGS, "Крылья - Dark", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/wings/99/model.json", "phazecosmetics:cosmetics/wings/99/texture.png", "phazecosmetics:cosmetics/wings/99/preview.png", 128, 128, 1, 128, 128, 1, 249));
        add(new CosmeticEntry(123, CosmeticCategory.WINGS, "Крылья - Phaze", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/wings/123/model.json", "phazecosmetics:cosmetics/wings/123/texture.png", "phazecosmetics:cosmetics/wings/123/preview.png", 64, 64, 1, 64, 64, 1, 149));
        add(new CosmeticEntry(124, CosmeticCategory.WINGS, "Крылья - Ghoul", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/wings/124/model.json", "phazecosmetics:cosmetics/wings/124/texture.png", "phazecosmetics:cosmetics/wings/124/preview.png", 64, 64, 1, 64, 64, 1, 249));
        add(new CosmeticEntry(136, CosmeticCategory.WINGS, "Крылья - Ягуар", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/wings/136/model.json", "phazecosmetics:cosmetics/wings/136/texture.png", "phazecosmetics:cosmetics/wings/136/preview.png", 32, 32, 1, 32, 32, 1, 349));
        add(new CosmeticEntry(48, CosmeticCategory.HAT, "Шапка - Armour", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/hat/48/model.json", "phazecosmetics:cosmetics/hat/48/texture.png", "phazecosmetics:cosmetics/hat/48/preview.png", 64, 1920, 1, 64, 1920, 1, 29));
        add(new CosmeticEntry(49, CosmeticCategory.HAT, "Шапка - Capybara", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/hat/49/model.json", "phazecosmetics:cosmetics/hat/49/texture.png", "phazecosmetics:cosmetics/hat/49/preview.png", 64, 960, 1, 64, 960, 1, 29));
        add(new CosmeticEntry(50, CosmeticCategory.HAT, "Шапка -  Chicken", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/hat/50/model.json", "phazecosmetics:cosmetics/hat/50/texture.png", "phazecosmetics:cosmetics/hat/50/preview.png", 64, 64, 1, 64, 64, 1, 29));
        add(new CosmeticEntry(53, CosmeticCategory.HAT, "Шапка - Pepe", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/hat/53/model.json", "phazecosmetics:cosmetics/hat/53/texture.png", "phazecosmetics:cosmetics/hat/53/preview.png", 64, 832, 1, 64, 832, 1, 79));
        add(new CosmeticEntry(57, CosmeticCategory.HAT, "Шапка - Sheep", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/hat/57/model.json", "phazecosmetics:cosmetics/hat/57/texture.png", "phazecosmetics:cosmetics/hat/57/preview.png", 64, 64, 1, 64, 64, 1, 29));
        add(new CosmeticEntry(80, CosmeticCategory.HAT, "Шапка - Fluger", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/hat/80/model.json", "phazecosmetics:cosmetics/hat/80/texture.png", "phazecosmetics:cosmetics/hat/80/preview.png", 64, 64, 1, 64, 64, 1, 249));
        add(new CosmeticEntry(85, CosmeticCategory.HAT, "Маска - Bro9I", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/hat/85/model.json", "phazecosmetics:cosmetics/hat/85/texture.png", "phazecosmetics:cosmetics/hat/85/preview.png", 16, 16, 1, 16, 16, 1, 249));
        add(new CosmeticEntry(88, CosmeticCategory.HAT, "Шапка - Аквыч", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/hat/88/model.json", "phazecosmetics:cosmetics/hat/88/texture.png", "phazecosmetics:cosmetics/hat/88/preview.png", 64, 64, 1, 64, 64, 1, 249));
        add(new CosmeticEntry(92, CosmeticCategory.HAT, "Шапка - Bear", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/hat/92/model.json", "phazecosmetics:cosmetics/hat/92/texture.png", "phazecosmetics:cosmetics/hat/92/preview.png", 96, 96, 1, 96, 96, 1, 149));
        add(new CosmeticEntry(96, CosmeticCategory.HAT, "Шапка - ПеПе", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/hat/96/model.json", "phazecosmetics:cosmetics/hat/96/texture.png", "phazecosmetics:cosmetics/hat/96/preview.png", 96, 96, 1, 96, 96, 1, 149));
        add(new CosmeticEntry(97, CosmeticCategory.HAT, "Шапка - Happy", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/hat/97/model.json", "phazecosmetics:cosmetics/hat/97/texture.png", "phazecosmetics:cosmetics/hat/97/preview.png", 64, 64, 1, 64, 64, 1, 99));
        add(new CosmeticEntry(98, CosmeticCategory.HAT, "Нимб", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/hat/98/model.json", "phazecosmetics:cosmetics/hat/98/texture.png", "phazecosmetics:cosmetics/hat/98/preview.png", 64, 64, 1, 64, 64, 1, 329));
        add(new CosmeticEntry(125, CosmeticCategory.HAT, "Шапка - Phaze", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/hat/125/model.json", "phazecosmetics:cosmetics/hat/125/texture.png", "phazecosmetics:cosmetics/hat/125/preview.png", 64, 64, 1, 64, 64, 1, 0));
        add(new CosmeticEntry(36, CosmeticCategory.BODYWEAR, "Рюкзак - Louis Vuitton", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/bodywear/36/model.json", "phazecosmetics:cosmetics/bodywear/36/texture.png", "phazecosmetics:cosmetics/bodywear/36/preview.png", 256, 256, 1, 256, 256, 1, 169));
        add(new CosmeticEntry(37, CosmeticCategory.BODYWEAR, "Рюкзак - Gucci", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/bodywear/37/model.json", "phazecosmetics:cosmetics/bodywear/37/texture.png", "phazecosmetics:cosmetics/bodywear/37/preview.png", 128, 128, 1, 128, 128, 1, 129));
        add(new CosmeticEntry(38, CosmeticCategory.BODYWEAR, "Рюкзак - Adidas", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/bodywear/38/model.json", "phazecosmetics:cosmetics/bodywear/38/texture.png", "phazecosmetics:cosmetics/bodywear/38/preview.png", 128, 128, 1, 128, 128, 1, 59));
        add(new CosmeticEntry(39, CosmeticCategory.BODYWEAR, "Рюкзак - Nike", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/bodywear/39/model.json", "phazecosmetics:cosmetics/bodywear/39/texture.png", "phazecosmetics:cosmetics/bodywear/39/preview.png", 128, 128, 1, 128, 128, 1, 99));
        add(new CosmeticEntry(40, CosmeticCategory.BODYWEAR, "Рюкзак - Supreme", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/bodywear/40/model.json", "phazecosmetics:cosmetics/bodywear/40/texture.png", "phazecosmetics:cosmetics/bodywear/40/preview.png", 128, 128, 1, 128, 128, 1, 119));
        add(new CosmeticEntry(41, CosmeticCategory.BODYWEAR, "Рюкзак - Vlone", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/bodywear/41/model.json", "phazecosmetics:cosmetics/bodywear/41/texture.png", "phazecosmetics:cosmetics/bodywear/41/preview.png", 128, 128, 1, 128, 128, 1, 149));
        add(new CosmeticEntry(42, CosmeticCategory.BODYWEAR, "Рюкзак - Bape", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/bodywear/42/model.json", "phazecosmetics:cosmetics/bodywear/42/texture.png", "phazecosmetics:cosmetics/bodywear/42/preview.png", 128, 128, 1, 128, 128, 1, 129));
        add(new CosmeticEntry(43, CosmeticCategory.BODYWEAR, "Рюкзак - Balenciaga", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/bodywear/43/model.json", "phazecosmetics:cosmetics/bodywear/43/texture.png", "phazecosmetics:cosmetics/bodywear/43/preview.png", 256, 256, 1, 256, 256, 1, 219));
        add(new CosmeticEntry(44, CosmeticCategory.BODYWEAR, "Рюкзак -  Chrome Hearts", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/bodywear/44/model.json", "phazecosmetics:cosmetics/bodywear/44/texture.png", "phazecosmetics:cosmetics/bodywear/44/preview.png", 256, 256, 1, 256, 256, 1, 149));
        add(new CosmeticEntry(95, CosmeticCategory.BODYWEAR, "Рюкзак -  PePe", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/bodywear/95/model.json", "phazecosmetics:cosmetics/bodywear/95/texture.png", "phazecosmetics:cosmetics/bodywear/95/preview.png", 80, 80, 1, 80, 80, 1, 99));
        add(new CosmeticEntry(137, CosmeticCategory.BODYWEAR, "Катана - ila_studio", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/bodywear/137/model.json", "phazecosmetics:cosmetics/bodywear/137/texture.png", "phazecosmetics:cosmetics/bodywear/137/preview.png", 32, 32, 1, 32, 32, 1, 299));
        add(new CosmeticEntry(45, CosmeticCategory.PET, "Питомец - Angel", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/pet/45/model.json", "phazecosmetics:cosmetics/pet/45/texture.png", "phazecosmetics:cosmetics/pet/45/preview.png", 128, 128, 1, 128, 128, 1, 99));
        add(new CosmeticEntry(46, CosmeticCategory.PET, "Питомец - Demon", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/pet/46/model.json", "phazecosmetics:cosmetics/pet/46/texture.png", "phazecosmetics:cosmetics/pet/46/preview.png", 64, 64, 1, 64, 64, 1, 99));
        add(new CosmeticEntry(47, CosmeticCategory.PET, "Питомец - Buddy", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/pet/47/model.json", "phazecosmetics:cosmetics/pet/47/texture.png", "phazecosmetics:cosmetics/pet/47/preview.png", 32, 32, 1, 32, 32, 1, 69));
        add(new CosmeticEntry(81, CosmeticCategory.PET, "Питомец - Fluger Door", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/pet/81/model.json", "phazecosmetics:cosmetics/pet/81/texture.png", "phazecosmetics:cosmetics/pet/81/preview.png", 32, 32, 1, 32, 32, 1, 299));
        add(new CosmeticEntry(84, CosmeticCategory.PET, "Питомец - Diamond", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/pet/84/model.json", "phazecosmetics:cosmetics/pet/84/texture.png", "phazecosmetics:cosmetics/pet/84/preview.png", 32, 288, 1, 32, 288, 1, 299));
        add(new CosmeticEntry(90, CosmeticCategory.PET, "Питомец - Аквыч", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/pet/90/model.json", "phazecosmetics:cosmetics/pet/90/texture.png", "phazecosmetics:cosmetics/pet/90/preview.png", 64, 64, 1, 64, 64, 1, 299));
        add(new CosmeticEntry(93, CosmeticCategory.PET, "Питомец - Bee", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/pet/93/model.json", "phazecosmetics:cosmetics/pet/93/texture.png", "phazecosmetics:cosmetics/pet/93/preview.png", 64, 64, 1, 64, 64, 1, 199));
        add(new CosmeticEntry(126, CosmeticCategory.PET, "Питомец - 1 Год", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/pet/126/model.json", "phazecosmetics:cosmetics/pet/126/texture.png", "phazecosmetics:cosmetics/pet/126/preview.png", 64, 64, 1, 64, 64, 1, 99));
        add(new CosmeticEntry(132, CosmeticCategory.PET, "Питомец - Ягуар", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/pet/132/model.json", "phazecosmetics:cosmetics/pet/132/texture.png", "phazecosmetics:cosmetics/pet/132/preview.png", 64, 64, 1, 64, 64, 1, 299));
        add(new CosmeticEntry(133, CosmeticCategory.PET, "Питомец - Cergifff", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/pet/133/model.json", "phazecosmetics:cosmetics/pet/133/texture.png", "phazecosmetics:cosmetics/pet/133/preview.png", 64, 64, 1, 64, 64, 1, 299));
        add(new CosmeticEntry(134, CosmeticCategory.PET, "Питомец - Ila_Studio", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/pet/134/model.json", "phazecosmetics:cosmetics/pet/134/texture.png", "phazecosmetics:cosmetics/pet/134/preview.png", 64, 64, 1, 64, 64, 1, 299));
        add(new CosmeticEntry(135, CosmeticCategory.PET, "Питомец - Creeper", CosmeticKind.GEOMETRY, "assets/phazecosmetics/cosmetics/pet/135/model.json", "phazecosmetics:cosmetics/pet/135/texture.png", "phazecosmetics:cosmetics/pet/135/preview.png", 64, 64, 1, 64, 64, 1, 299));
        add(new CosmeticEntry(127, CosmeticCategory.GRAFFITI, "Граффити Phaze #127", CosmeticKind.GRAFFITI, null, "phazecosmetics:cosmetics/graffiti/127/texture.png", null, 1327, 1186, 1, 1327, 1186, 1, 0));
        add(new CosmeticEntry(128, CosmeticCategory.GRAFFITI, "Граффити Phaze #128", CosmeticKind.GRAFFITI, null, "phazecosmetics:cosmetics/graffiti/128/texture.png", null, 319, 360, 1, 319, 360, 1, 0));
        add(new CosmeticEntry(129, CosmeticCategory.GRAFFITI, "Граффити Phaze #129", CosmeticKind.GRAFFITI, null, "phazecosmetics:cosmetics/graffiti/129/texture.png", null, 512, 512, 1, 512, 512, 1, 0));
        add(new CosmeticEntry(138, CosmeticCategory.GRAFFITI, "Граффити Phaze #138", CosmeticKind.GRAFFITI, null, "phazecosmetics:cosmetics/graffiti/138/texture.png", null, 512, 512, 1, 512, 512, 1, 0));
        add(new CosmeticEntry(139, CosmeticCategory.GRAFFITI, "Граффити Phaze #139", CosmeticKind.GRAFFITI, null, "phazecosmetics:cosmetics/graffiti/139/texture.png", null, 512, 512, 1, 512, 512, 1, 0));
        add(new CosmeticEntry(140, CosmeticCategory.GRAFFITI, "Граффити Phaze #140", CosmeticKind.GRAFFITI, null, "phazecosmetics:cosmetics/graffiti/140/texture.png", null, 1024, 1024, 1, 1024, 1024, 1, 0));
        add(new CosmeticEntry(141, CosmeticCategory.GRAFFITI, "Граффити Phaze #141", CosmeticKind.GRAFFITI, null, "phazecosmetics:cosmetics/graffiti/141/texture.png", null, 256, 192, 1, 256, 192, 1, 0));
        add(new CosmeticEntry(142, CosmeticCategory.GRAFFITI, "Граффити Phaze #142", CosmeticKind.GRAFFITI, null, "phazecosmetics:cosmetics/graffiti/142/texture.png", null, 512, 512, 1, 512, 512, 1, 0));
        add(new CosmeticEntry(143, CosmeticCategory.GRAFFITI, "Граффити Phaze #143", CosmeticKind.GRAFFITI, null, "phazecosmetics:cosmetics/graffiti/143/texture.png", null, 512, 512, 1, 512, 512, 1, 0));
        add(new CosmeticEntry(144, CosmeticCategory.GRAFFITI, "Граффити Phaze #144", CosmeticKind.GRAFFITI, null, "phazecosmetics:cosmetics/graffiti/144/texture.png", null, 512, 324, 1, 512, 324, 1, 0));
        add(new CosmeticEntry(145, CosmeticCategory.GRAFFITI, "Граффити Phaze #145", CosmeticKind.GRAFFITI, null, "phazecosmetics:cosmetics/graffiti/145/texture.png", null, 512, 458, 1, 512, 458, 1, 0));
        add(new CosmeticEntry(146, CosmeticCategory.GRAFFITI, "Граффити Phaze #146", CosmeticKind.GRAFFITI, null, "phazecosmetics:cosmetics/graffiti/146/texture.png", null, 128, 128, 1, 128, 128, 1, 0));
        add(new CosmeticEntry(148, CosmeticCategory.GRAFFITI, "Граффити Phaze #148", CosmeticKind.GRAFFITI, null, "phazecosmetics:cosmetics/graffiti/148/texture.png", null, 224, 224, 1, 224, 224, 1, 0));
        add(new CosmeticEntry(149, CosmeticCategory.GRAFFITI, "Граффити Phaze #149", CosmeticKind.GRAFFITI, null, "phazecosmetics:cosmetics/graffiti/149/texture.png", null, 1536, 1024, 1, 1536, 1024, 1, 0));
        add(new CosmeticEntry(150, CosmeticCategory.GRAFFITI, "Граффити Phaze #150", CosmeticKind.GRAFFITI, null, "phazecosmetics:cosmetics/graffiti/150/texture.png", null, 256, 256, 1, 256, 256, 1, 0));
        add(new CosmeticEntry(151, CosmeticCategory.GRAFFITI, "Граффити Phaze #151", CosmeticKind.GRAFFITI, null, "phazecosmetics:cosmetics/graffiti/151/texture.png", null, 256, 256, 1, 256, 256, 1, 0));
        add(new CosmeticEntry(152, CosmeticCategory.GRAFFITI, "Граффити Phaze #152", CosmeticKind.GRAFFITI, null, "phazecosmetics:cosmetics/graffiti/152/texture.png", null, 1024, 1024, 1, 1024, 1024, 1, 0));
        add(new CosmeticEntry(153, CosmeticCategory.GRAFFITI, "Граффити Phaze #153", CosmeticKind.GRAFFITI, null, "phazecosmetics:cosmetics/graffiti/153/texture.png", null, 1024, 1024, 1, 1024, 1024, 1, 0));
        add(new CosmeticEntry(154, CosmeticCategory.GRAFFITI, "Граффити Phaze #154", CosmeticKind.GRAFFITI, null, "phazecosmetics:cosmetics/graffiti/154/texture.png", null, 512, 512, 1, 512, 512, 1, 0));
        add(new CosmeticEntry(155, CosmeticCategory.GRAFFITI, "Граффити Phaze #155", CosmeticKind.GRAFFITI, null, "phazecosmetics:cosmetics/graffiti/155/texture.png", null, 512, 512, 1, 512, 512, 1, 0));
        for (CosmeticCategory category : CosmeticCategory.values()) {
            BY_CATEGORY.computeIfAbsent(category, k -> new ArrayList<>()).sort(Comparator.comparingInt(CosmeticEntry::id));
        }
    }

    private static void add(CosmeticEntry entry) {
        ALL.add(entry);
        BY_ID.put(entry.id(), entry);
        BY_CATEGORY.computeIfAbsent(entry.category(), k -> new ArrayList<>()).add(entry);
    }

    public static List<CosmeticEntry> all() {
        return Collections.unmodifiableList(ALL);
    }

    public static List<CosmeticEntry> byCategory(CosmeticCategory category) {
        return Collections.unmodifiableList(BY_CATEGORY.getOrDefault(category, List.of()));
    }

    public static CosmeticEntry byId(int id) {
        return BY_ID.get(id);
    }

    private CosmeticCatalog() {}
}
