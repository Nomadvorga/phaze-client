package vorga.phazeclient.mixins;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.client.gui.screen.pack.PackListWidget;
import net.minecraft.client.gui.screen.pack.ResourcePackOrganizer;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.PackOrganizer;
import vorga.phazeclient.implement.features.modules.other.PackPreviewRenderer;
import vorga.phazeclient.implement.features.modules.other.PackSearch;
import vorga.phazeclient.base.util.Lang;

import java.util.stream.Stream;

@Mixin(PackScreen.class)
public abstract class PackScreenMixin extends Screen {
    @Shadow @Final
    private ResourcePackOrganizer organizer;

    @Shadow
    private ButtonWidget doneButton;

    @Shadow private PackListWidget availablePackList;
    @Shadow private PackListWidget selectedPackList;

    @Unique
    private String phaze$packSearchQuery = "";

    @Unique
    private TextFieldWidget phaze$packSearchField;

    @Shadow
    private void updatePackLists() {
        throw new AssertionError("Mixin shadow");
    }

    protected PackScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void phaze$addPackSearchBar(CallbackInfo ci) {
        PackPreviewRenderer.clear(this.client);
        this.phaze$packSearchField = null;
        if (this.phaze$isPackSearchEnabled()) {
            int w = 180;
            int h = 18;
            int x = this.width / 2 - w / 2;
            int y = 14;
            TextFieldWidget searchField = new TextFieldWidget(this.textRenderer, x, y, w, h, Text.literal(Lang.translate("Search")));
            searchField.setPlaceholder(Text.literal(Lang.translate("Search packs...")));
            searchField.setMaxLength(256);
            // Keep the query on this screen, including when vanilla recreates its widgets.
            searchField.setText(this.phaze$packSearchQuery);
            searchField.setChangedListener(query -> {
                // TextFieldWidget also notifies its listener when only the cursor moves.
                if (!this.phaze$packSearchQuery.equals(query)) {
                    this.phaze$packSearchQuery = query;
                    this.updatePackLists();
                    this.availablePackList.setScrollY(0);
                    this.selectedPackList.setScrollY(0);
                }
            });
            this.phaze$packSearchField = searchField;
            this.addDrawableChild(searchField);
        }
    }

    @Inject(method = "refreshWidgetPositions", at = @At("TAIL"))
    private void phaze$positionPackSearchBar(CallbackInfo ci) {
        if (this.phaze$packSearchField != null) {
            this.phaze$packSearchField.setX(this.width / 2 - this.phaze$packSearchField.getWidth() / 2);
        }
    }

    @Inject(method = "refresh", at = @At("HEAD"))
    private void phaze$invalidatePackPreviews(CallbackInfo ci) {
        if (this.client != null) {
            PackPreviewRenderer.clear(this.client);
        }
    }

    @Override
    public void removed() {
        super.removed();
        PackPreviewRenderer.clear(this.client);
    }

    /** Filter presentation only; vanilla retains the complete pack selection and order. */
    @ModifyVariable(method = "updatePackList", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Stream<ResourcePackOrganizer.Pack> phaze$filterPacks(Stream<ResourcePackOrganizer.Pack> packs) {
        if (!this.phaze$isPackSearchEnabled() || this.phaze$packSearchQuery.isEmpty()) {
            return packs;
        }
        return packs.filter(pack -> PackSearch.matches(this.phaze$packSearchQuery, pack.getName(),
                pack.getDisplayName().getString(), pack.getDescription().getString()));
    }

    @Inject(method = "updatePackLists", at = @At("TAIL"))
    private void phaze$keepDoneButtonUsable(CallbackInfo ci) {
        if (this.phaze$isPackSearchEnabled() && !this.phaze$packSearchQuery.isEmpty()) {
            // An empty search result does not mean that no resource packs are enabled.
            this.doneButton.active = this.organizer.getEnabledPacks().findAny().isPresent();
        }
    }

    @Unique
    private boolean phaze$isPackSearchEnabled() {
        PackOrganizer module = PackOrganizer.getInstance();
        return module != null && module.isEnabled() && module.searchBar.isValue();
    }

    /** Hide vanilla's long drop hint so it cannot sit beneath the search field. */
    @ModifyArg(method = "init", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/widget/TextWidget;<init>(Lnet/minecraft/text/Text;Lnet/minecraft/client/font/TextRenderer;)V",
            ordinal = 1), index = 0)
    private Text phaze$hideDropHint(Text original) {
        return Text.empty();
    }
}
