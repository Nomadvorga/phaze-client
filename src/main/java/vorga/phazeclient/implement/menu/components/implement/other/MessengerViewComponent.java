package vorga.phazeclient.implement.menu.components.implement.other;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import vorga.phazeclient.api.system.animation.Animation;
import vorga.phazeclient.api.system.animation.Direction;
import vorga.phazeclient.api.system.animation.implement.DecelerateAnimation;
import vorga.phazeclient.api.system.cursor.CursorManager;
import vorga.phazeclient.api.system.font.msdf.MsdfFont;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;
import vorga.phazeclient.base.util.Lang;
import vorga.phazeclient.base.util.MessengerPrefs;
import vorga.phazeclient.base.util.MessengerService;
import vorga.phazeclient.base.util.ServerUtil;
import vorga.phazeclient.base.util.SkinAvatarService;
import vorga.phazeclient.implement.config.ConfigManager;
import vorga.phazeclient.implement.menu.MenuLayout;
import vorga.phazeclient.implement.menu.MenuScreen;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.UiMsdfIconAtlas;
import vorga.phazeclient.implement.menu.components.AbstractComponent;
import vorga.phazeclient.implement.menu.components.implement.settings.AbstractSettingComponent;
import vorga.phazeclient.implement.menu.components.implement.settings.SettingLabel;
import vorga.phazeclient.implement.messenger.ChatWorld;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Full-width messenger page. Layout calculations intentionally live in one
 * place so every panel keeps the same gap, padding and vertical rhythm at
 * every menu size and theme: lists, forms and the right-hand panel all share
 * the card geometry of the direct-message list.
 */
public final class MessengerViewComponent extends AbstractComponent {
    private static final float SIDE = 8.0F;
    private static final float TOP = 42.0F;
    private static final float GAP = MenuLayout.PANEL_GAP;
    private static final float LEFT_WIDTH = 92.0F;
    private static final float RIGHT_WIDTH = 90.0F;
    private static final float PANEL_RADIUS = 4.0F;
    private static final float PANEL_PAD = MenuLayout.CONTENT_INSET;
    private static final float HEADER_TEXT_INSET = 2.0F;
    private static final float NAV_HEIGHT = 14.0F;
    private static final float NAV_GAP = 2.0F;
    private static final float PROFILE_HEIGHT = 42.0F;
    private static final float NAV_TOP_OFFSET = PROFILE_HEIGHT + 9.0F;
    private static final float FRIEND_PREVIEW_TOP_OFFSET = NAV_TOP_OFFSET
            + Section.values().length * NAV_HEIGHT - NAV_GAP + 16.0F;
    /** Height of a one-line message: name line, one text line and the gap to the next message. */
    private static final float MESSAGE_ROW = 22.0F;
    private static final float MESSAGE_AVATAR_SIZE = 17.0F;
    private static final float MESSAGE_TEXT_X = 22.0F;
    private static final float MESSAGE_TEXT_TOP = 11.0F;
    private static final float MESSAGE_TEXT_SIZE = 6.0F;
    private static final float MESSAGE_LINE_STEP = 8.0F;
    private static final float MESSAGE_ROW_GAP = MESSAGE_ROW - MESSAGE_TEXT_TOP - MESSAGE_LINE_STEP;
    /** Wrapped text keeps this distance from the list's right (scissor) edge. */
    private static final float MESSAGE_TEXT_RIGHT_INSET = 2.0F;
    private static final int WRAP_CACHE_LIMIT = 512;
    private static final float COMPACT_FRIEND_NAME_OFFSET = (18.0F - (7.0F + 4.8F)) / 2.0F;
    private static final float COMPACT_FRIEND_STATUS_OFFSET = COMPACT_FRIEND_NAME_OFFSET + 7.0F;
    private static final float FRIEND_CARD_HEIGHT = AbstractSettingComponent.ROW_HEIGHT;
    private static final float DIRECT_CARD_HEIGHT = FRIEND_CARD_HEIGHT;
    private static final float DIRECT_ROW_STEP = DIRECT_CARD_HEIGHT + 5.0F;
    private static final float CARD_AVATAR_SIZE = 17.5F;
    private static final float CARD_AVATAR_INSET = (FRIEND_CARD_HEIGHT - CARD_AVATAR_SIZE) / 2.0F;
    private static final float CARD_TEXT_INSET = CARD_AVATAR_INSET + CARD_AVATAR_SIZE + 6.0F;
    private static final float CARD_TEXT_LINE_STEP = 10.0F;
    private static final float DIRECT_CARD_NAME_OFFSET = (DIRECT_CARD_HEIGHT - (CARD_TEXT_LINE_STEP + 5.2F)) / 2.0F;
    private static final float REQUEST_CARD_HEIGHT = DIRECT_CARD_HEIGHT;
    private static final float REQUEST_ROW_STEP = DIRECT_ROW_STEP;
    /** Accept and decline are avatar-sized squares; decline keeps the avatar's inset from the right edge. */
    private static final float REQUEST_BUTTON_SIZE = CARD_AVATAR_SIZE;
    private static final float REQUEST_BUTTON_GAP = 4.0F;
    private static final float REQUEST_ICON_SIZE = 7.0F;
    private static final Identifier ACCEPT_ICON = Identifier.of("phaze", "textures/menu/check.png");
    private static final Identifier DECLINE_ICON = Identifier.of("phaze", "textures/menu/cross.png");
    private static final Identifier SETTINGS_ICON = Identifier.of("phaze", "textures/menu/settings.png");
    private static final Identifier POINT_ICON = Identifier.of("phaze", "textures/modules/death_coords.png");
    private static final Identifier DEATH_ICON = Identifier.of("phaze", "textures/modules/skull-outline.png");
    private static final Identifier CONFIG_ICON = Identifier.ofVanilla("textures/file.png");
    private static final float BLOCKED_ROW_HEIGHT = 25.0F;
    private static final float BLOCKED_ROW_STEP = BLOCKED_ROW_HEIGHT + 4.0F;
    private static final float UNBLOCK_TEXT_SIZE = 5.2F;
    private static final float UNBLOCK_HEIGHT = 17.0F;
    private static final float UNBLOCK_MIN_WIDTH = 44.0F;
    private static final float UNBLOCK_TEXT_PAD = 7.0F;
    private static final float UNBLOCK_RIGHT_INSET = 6.0F;
    private static final float EMPTY_STATE_TEXT_SIZE = 5.8F;
    private static final float CARD_RADIUS = 5.0F;
    private static final float DIRECT_HEADER_HEIGHT = 27.0F;
    private static final float DIRECT_BACK_INSET = 3.0F;
    /** Square back button: as wide as it is tall inside the header. */
    private static final float DIRECT_BACK_SIZE = DIRECT_HEADER_HEIGHT - DIRECT_BACK_INSET * 2.0F;
    private static final float DIRECT_BACK_ICON_SIZE = 10.0F;
    private static final float INPUT_HEIGHT = 18.0F;
    private static final float INPUT_ERROR_RESERVE = 9.0F;
    private static final float INPUT_TEXT_SIZE = 7.2F;
    private static final float SEND_WIDTH = 42.0F;
    /** Header controls (the "+" of Groups, the Requests tabs) share one height. */
    private static final float HEADER_CONTROL_HEIGHT = 15.0F;
    private static final float GEAR_SIZE = 13.0F;
    private static final float GEAR_ICON_SIZE = 7.6F;
    private static final float UNREAD_HEIGHT = 9.0F;
    private static final float ADD_BUTTON_WIDTH = 52.0F;
    /** The nickname field, then one line for its result, then the request cards. */
    private static final float ADD_ROW_RESERVE = MessengerTextField.HEIGHT + 11.0F;
    private static final float ATTACH_HEIGHT = 26.0F;
    private static final float ATTACH_BUTTON_HEIGHT = 14.0F;
    private static final float ATTACH_ICON_SIZE = 10.0F;
    private static final float ATTACH_MENU_ROW = 16.0F;
    private static final float ATTACH_MENU_WIDTH = 100.0F;
    private static final float PICK_CHECK_SIZE = 12.0F;
    private static final float FORM_BUTTON_HEIGHT = 18.0F;
    private static final float MEMBER_ROW = 18.0F;
    private static final float MEMBER_STEP = 20.0F;
    private static final float DETAIL_BUTTON_HEIGHT = 18.0F;
    private static final float DETAIL_BUTTON_STEP = 22.0F;
    private static final float SWITCH_WIDTH = 18.0F;
    private static final float SWITCH_HEIGHT = 10.0F;
    private static final float LIST_SCROLL_STEP = 24.0F;
    private static final long CONFIRM_WINDOW_MS = 3_000L;
    private static final int HOVER_MS = 170;
    private static final int MAX_INPUT_CODEPOINTS = 255;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault());

    private final MessengerService service = MessengerService.getInstance();
    private final SkinAvatarService skins = SkinAvatarService.getInstance();
    private final Animation pageAnimation = new DecelerateAnimation().setMs(180).setValue(1);
    private boolean open;
    private Section section = Section.GLOBAL;
    private Form form = Form.NONE;
    private RequestTab requestTab = RequestTab.INCOMING;
    private String input = "";
    private int cursor;
    private int selectionAnchor;
    private boolean inputFocused;
    private boolean draggingSelection;
    private long nextSendAt;
    private double scroll;
    private String scrollListKey;
    private float scrollListHeight;
    /** Scroll of the card lists and forms; messages keep their own bottom-anchored scroll. */
    private double listScroll;
    private double membersScroll;
    private final Map<Long, WrappedText> wrapCache = new LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, WrappedText> eldest) {
            return size() > WRAP_CACHE_LIMIT;
        }
    };
    private volatile String selectedFriendId;
    private volatile String selectedProfileId;
    private volatile String selectedProfileUsername;
    private volatile String selectedConversationId;
    private volatile String selectedGroupId;
    private volatile long conversationRequestGeneration;
    private volatile String pendingConversationId;
    private volatile String pendingGroupId;
    private volatile boolean sendResultReady;
    private volatile boolean sendResultSuccess;
    private volatile boolean formPending;
    private boolean sendPending;
    private String pendingText;
    private volatile ActionMessage actionMessage;
    private boolean attachOpen;
    private boolean editingNote;
    private String armedKey;
    private long armedAt;
    private float listClipTop;
    private float listClipBottom;
    private final Set<String> pickedFriends = new LinkedHashSet<>();
    private final MessengerTextField nameField = new MessengerTextField(32, codePoint -> true);
    private final MessengerTextField nickField = new MessengerTextField(21,
            codePoint -> codePoint < 128 && (Character.isLetterOrDigit(codePoint) || codePoint == '_' || codePoint == '#'));
    private final MessengerTextField noteField = new MessengerTextField(MessengerPrefs.NOTE_MAX, codePoint -> true);
    private static final long MESSAGE_VISIBLE_MS = 5_000L;
    /** Service statuses that report success or connection state, never a failed action. */
    private static final Set<String> NON_ERROR_STATUSES = Set.of(
            "Online", "Offline", "Messenger reconnecting", "Friend request sent", "Friend request pending",
            "Friend added", "Friend removed", "Friend request declined", "User unblocked");
    private static final float ACTION_MESSAGE_SIZE = 5.2F;
    private static final int ERROR_COLOR = 0xFFE76868;
    private static final int NOTICE_COLOR = 0xFFE8C468;
    private static final int ADMIN_NAME_COLOR = 0xFFFF4F4F;

    record ActionMessage(String text, int color, long shownAt) {}
    private record WrappedText(String text, float width, List<String> lines) {}
    /** A labelled action in the right-hand panel; render and click walk the same list. */
    private record DetailButton(String key, String label, int style, Runnable action) {}
    private record AttachAction(String label, boolean enabled) {}

    private static final int STYLE_NORMAL = 0;
    private static final int STYLE_ACTIVE = 1;
    private static final int STYLE_DESTRUCTIVE = 2;

    private final Map<String, SettingLabel> marqueeLabels = new HashMap<>();
    private final Map<String, Animation> hoverAnimations = new HashMap<>();
    private final Map<String, Animation> stateAnimations = new HashMap<>();
    private final Set<String> renderedHoverKeys = new HashSet<>();

    public MessengerViewComponent() {
        pageAnimation.setDirectionAndFinish(Direction.BACKWARDS);
    }

    private enum Section {
        GLOBAL("messenger.global"),
        GROUPS("messenger.groups"),
        DIRECT("messenger.direct"),
        REQUESTS("messenger.requests"),
        BLOCKED("messenger.blocked");

        private final String label;

        Section(String label) {
            this.label = label;
        }
    }

    private enum Form { NONE, CREATE_GROUP, INVITE, RENAME, SETTINGS }

    private enum RequestTab { INCOMING, OUTGOING }

    public void open() {
        if (!open) hoverAnimations.clear();
        open = true;
        // Top-level pages switch instantly, like Mods/Settings/Configs.
        pageAnimation.setDirectionAndFinish(Direction.FORWARDS);
        service.start();
        service.refreshRequestsAsync();
        String conversationId = openConversationId();
        if (conversationId != null) service.selectConversation(conversationId);
    }

    public void close() {
        open = false;
        pageAnimation.setDirectionAndFinish(Direction.BACKWARDS);
        input = "";
        cursor = 0;
        selectionAnchor = 0;
        inputFocused = false;
        sendPending = false;
        pendingText = null;
        sendResultReady = false;
        pendingConversationId = null;
        pendingGroupId = null;
        conversationRequestGeneration++;
        attachOpen = false;
        finishNoteEdit();
        nameField.setFocused(false);
        nickField.setFocused(false);
        // While the menu is closed nothing is on screen: new messages count as unread and toast.
        service.selectConversation(null);
    }

    public boolean isOpen() {
        return open || pageAnimation.getOutputFloat() > 0.002F;
    }

    public boolean shouldRender() {
        return open || pageAnimation.getOutputFloat() > 0.002F;
    }

    /* ---- state helpers ------------------------------------------------ */

    private MessengerService.Group selectedGroup() {
        return service.getGroup(selectedGroupId);
    }

    /** The conversation whose messages are on screen (or under a form opened from it). */
    private String openConversationId() {
        if (section == Section.DIRECT && selectedConversation() != null) return selectedConversationId;
        if (section == Section.GROUPS && selectedGroup() != null) return selectedGroupId;
        return null;
    }

    private boolean conversationOpen() {
        return form == Form.NONE && openConversationId() != null;
    }

    private boolean groupPanelVisible() {
        return section == Section.GROUPS && selectedGroup() != null && selectedProfileId == null;
    }

    private boolean isMe(String userId) {
        return service.isSelf(userId);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!shouldRender()) return;
        float inheritedAlpha = globalAlpha;
        globalAlpha *= pageAnimation.getOutputFloat();
        applyAsyncState();

        renderedHoverKeys.clear();
        try {

        float pageX = x + SIDE;
        float pageY = y + TOP;
        float pageW = width - SIDE * 2.0F;
        float pageH = height - TOP - SIDE;
        float leftX = pageX;
        float centerX = leftX + LEFT_WIDTH + GAP;
        float rightX = pageX + pageW - RIGHT_WIDTH;
        float centerW = rightX - GAP - centerX;

        drawPanel(context, leftX, pageY, LEFT_WIDTH, pageH, MenuStyle.PANEL_SIDEBAR);
        drawPanel(context, centerX, pageY, centerW, pageH, MenuStyle.PANEL_CONTENT);
        drawPanel(context, rightX, pageY, RIGHT_WIDTH, pageH, MenuStyle.PANEL_SIDEBAR);

        renderProfile(context, leftX + PANEL_PAD, pageY + PANEL_PAD,
                LEFT_WIDTH - PANEL_PAD * 2.0F, mouseX, mouseY);
        renderNavigation(context, leftX + PANEL_PAD, pageY + NAV_TOP_OFFSET, LEFT_WIDTH - PANEL_PAD * 2.0F,
                mouseX, mouseY);
        renderFriendPreview(context, leftX + PANEL_PAD, pageY + FRIEND_PREVIEW_TOP_OFFSET,
                LEFT_WIDTH - PANEL_PAD * 2.0F, pageY + pageH - PANEL_PAD, mouseX, mouseY);

        float contentX = centerX + PANEL_PAD;
        float contentY = pageY + PANEL_PAD;
        float contentW = centerW - PANEL_PAD * 2.0F;
        float contentH = pageH - PANEL_PAD * 2.0F;
        renderCenter(context, contentX, contentY, contentW, contentH, mouseX, mouseY);
        renderDetails(context, rightX + PANEL_PAD, pageY + PANEL_PAD,
                RIGHT_WIDTH - PANEL_PAD * 2.0F, pageH - PANEL_PAD * 2.0F, mouseX, mouseY);
        if (attachOpen && conversationOpen()) {
            renderAttachMenu(context, contentX, contentY + contentH - INPUT_HEIGHT, mouseX, mouseY);
        }
        } finally {
            hoverAnimations.keySet().retainAll(renderedHoverKeys);
            globalAlpha = inheritedAlpha;
        }
    }

    /* ---- left panel ---------------------------------------------------- */

    private void renderProfile(DrawContext context, float panelX, float panelY, float panelW,
                               int mouseX, int mouseY) {
        drawSurface(context, panelX, panelY, panelW, PROFILE_HEIGHT, MenuStyle.CARD_INNER);
        String username = service.getCurrentUser() == null
                ? "Player" : service.getCurrentUser().username();
        renderAvatar(context, panelX + 6.0F, panelY + 8.0F, 25.0F, null, username);
        float gearX = gearX(panelX, panelW);
        float gearY = panelY + 4.0F;
        drawMarqueeLabel(context, "profile", username, 6.2F, panelX + 36.0F, panelY + 9.0F,
                gearX - 2.0F, panelY + 5.0F, 12.0F,
                MenuStyle.TEXT_PRIMARY, true,
                inside(mouseX, mouseY, panelX + 34.0F, panelY + 4.0F, gearX - panelX - 34.0F, 16.0F));
        drawStatus(context, panelX + 36.0F, panelY + 18.0F, service.isConnected(), null, panelW - 38.0F);

        float hover = animatedHover("profile:settings", inside(mouseX, mouseY, gearX, gearY, GEAR_SIZE, GEAR_SIZE));
        boolean active = form == Form.SETTINGS;
        if (hover > 0.0F || active) {
            drawChatCard(context, gearX, gearY, GEAR_SIZE, GEAR_SIZE, hover, Math.max(hover, active ? 1.0F : 0.0F));
        }
        UiMsdfIconAtlas.renderIconInkFitted(context, SETTINGS_ICON, gearX + GEAR_SIZE / 2.0F, gearY + GEAR_SIZE / 2.0F,
                GEAR_ICON_SIZE, GEAR_ICON_SIZE,
                applyGlobalAlpha(MenuStyle.mix(MenuStyle.TEXT_MUTED, MenuStyle.TEXT_PRIMARY, Math.max(hover, active ? 1.0F : 0.0F))));
    }

    private static float gearX(float panelX, float panelW) {
        return panelX + panelW - GEAR_SIZE - 4.0F;
    }

    private void renderNavigation(DrawContext context, float navX, float navY, float navW,
                                  int mouseX, int mouseY) {
        for (Section item : Section.values()) {
            float rowY = navY + item.ordinal() * (NAV_HEIGHT + NAV_GAP);
            boolean active = section == item;
            boolean hovered = inside(mouseX, mouseY, navX, rowY, navW, NAV_HEIGHT);
            float hover = animatedHover("nav:" + item.name(), hovered);
            int fill = active
                    ? MenuStyle.mix(MenuStyle.PANEL_ROW_ACTIVE, MenuStyle.CHIP_ACTIVE, 0.42F)
                    : MenuStyle.mix(MenuStyle.PANEL_ROW, MenuStyle.CARD_INNER, 0.12F);
            int border = active ? MenuStyle.mix(MenuStyle.BORDER_LIGHT, MenuStyle.CHIP_ACTIVE, 0.55F)
                    : MenuStyle.BORDER;
            drawButtonSurface(context, navX, rowY, navW, NAV_HEIGHT, fill, border, hover);
            drawCentered(context, Lang.t(item.label), navX, rowY, navW, NAV_HEIGHT, 5.2F,
                    active ? MenuStyle.TEXT_PRIMARY
                            : MenuStyle.mix(MenuStyle.TEXT_MUTED, MenuStyle.TEXT_PRIMARY, hover * 0.45F), true);
            int badge = switch (item) {
                case GROUPS -> service.getGroupUnread() + service.getGroupInvites().size();
                case DIRECT -> service.getDirectUnread();
                case REQUESTS -> service.getRequests().size();
                default -> 0;
            };
            if (badge > 0) drawBadge(context, navX, rowY, navW, NAV_HEIGHT, badge > 99 ? "99+" : String.valueOf(badge));
        }
    }

    private void renderFriendPreview(DrawContext context, float listX, float listY, float listW, float bottom,
                                     int mouseX, int mouseY) {
        drawText(context, Lang.t("messenger.friends"), listX + 2.0F, listY, 5.6F, MenuStyle.TEXT_PRIMARY, true);
        List<MessengerService.Friend> friends = service.getFriends();
        float rowY = listY + 14.0F;
        int shown = 0;
        for (MessengerService.Friend friend : friends) {
            if (rowY + 18.0F > bottom || shown++ >= 5) break;
            renderCompactFriend(context, friend, listX, rowY, listW, mouseX, mouseY);
            rowY += 20.0F;
        }
        if (friends.isEmpty()) {
            drawText(context, Lang.t("messenger.no_friends"), listX + 2.0F, rowY, 5.2F, MenuStyle.TEXT_MUTED, false);
        }
    }

    private void renderCompactFriend(DrawContext context, MessengerService.Friend friend,
                                      float rowX, float rowY, float rowW, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, rowX, rowY, rowW, 18.0F);
        float hover = animatedHover("compact:" + friend.id(), hovered);
        if (hover > 0.0F) drawChatCard(context, rowX, rowY, rowW, 18.0F, hover, hover);
        renderAvatar(context, rowX, rowY, 15.0F, friend.id(), friend.username());
        drawMarqueeLabel(context, "compact:" + friend.id(), friend.username(), 5.0F,
                rowX + 20.0F, rowY + COMPACT_FRIEND_NAME_OFFSET, rowX + rowW, rowY, 12.0F,
                MenuStyle.TEXT_PRIMARY, false,
                inside(mouseX, mouseY, rowX + 18.0F, rowY, rowW - 18.0F, 18.0F));
        drawStatus(context, rowX + 20.0F, rowY + COMPACT_FRIEND_STATUS_OFFSET, service.isUserOnline(friend.id()),
                serverName(friend.id()), rowW - 21.0F);
    }

    private String serverName(String userId) {
        MessengerService.ServerInfo server = service.getServer(userId);
        return server == null ? null : server.name();
    }

    /* ---- center ------------------------------------------------------- */

    private void renderCenter(DrawContext context, float contentX, float contentY, float contentW,
                              float contentH, int mouseX, int mouseY) {
        if (form != Form.NONE) {
            renderForm(context, contentX, contentY, contentW, contentH, mouseX, mouseY);
            return;
        }
        MessengerService.Conversation conversation = section == Section.DIRECT ? selectedConversation() : null;
        MessengerService.Group group = section == Section.GROUPS ? selectedGroup() : null;
        boolean conversationOpen = conversation != null || group != null;
        if (conversation != null) {
            renderDirectHeader(context, conversation, contentX, contentY, contentW, mouseX, mouseY);
        } else if (group != null) {
            renderGroupHeader(context, group, contentX, contentY, contentW, mouseX, mouseY);
        } else {
            String title = section == Section.GLOBAL ? Lang.t("messenger.title") : Lang.t(section.label);
            drawText(context, title, contentX + HEADER_TEXT_INSET, contentY + 2.0F, 8.2F, MenuStyle.TEXT_PRIMARY, true);
        }
        if (section == Section.GLOBAL) {
            String online = service.getOnlineCount() < 0 ? "—" : service.getOnlineCount() + " online";
            float onlineWidth = MsdfFonts.medium().getWidth(online, 5.0F);
            drawText(context, online, headerRightTextX(contentX, contentW, onlineWidth), contentY + 4.0F,
                    5.0F, MenuStyle.TEXT_MUTED, false);
        } else if (!conversationOpen && section == Section.GROUPS) {
            renderPlusButton(context, "groups:create", plusX(contentX, contentW), contentY, mouseX, mouseY);
        } else if (!conversationOpen && section == Section.REQUESTS) {
            renderRequestTabs(context, contentX, contentY, contentW, mouseX, mouseY);
        }
        if (!conversationOpen) {
            drawLine(context, contentX - PANEL_PAD, contentY + 21.0F, contentW + PANEL_PAD * 2.0F, MenuStyle.BORDER);
        }

        float inputY = contentY + contentH - INPUT_HEIGHT;
        float listTop = contentY + (conversationOpen ? DIRECT_HEADER_HEIGHT + 5.0F : 27.0F);
        boolean hasInput = conversationOpen || section == Section.GLOBAL;
        float listBottom = listBottom(contentY, contentH, hasInput) - (hasInput ? inputErrorReserve() : 0.0F);
        listClipTop = listTop;
        listClipBottom = listBottom;
        BatchedRectangle.flushIfBatching();
        context.enableScissor((int) contentX, (int) listTop, (int) (contentX + contentW), (int) listBottom);
        if (section == Section.GLOBAL) {
            renderGlobalMessages(context, contentX, listTop, contentW, listBottom, mouseX, mouseY);
        } else if (conversationOpen) {
            renderConversationMessages(context, openConversationId(), group != null,
                    contentX, listTop, contentW, listBottom, mouseX, mouseY);
        } else {
            renderSectionRows(context, contentX, listTop, contentW, listBottom, mouseX, mouseY);
        }
        BatchedRectangle.flushIfBatching();
        context.disableScissor();

        if (hasInput) {
            renderInput(context, contentX, inputY, contentW, mouseX, mouseY);
        }
    }

    private void renderDirectHeader(DrawContext context, MessengerService.Conversation conversation,
                                    float contentX, float contentY, float contentW, int mouseX, int mouseY) {
        renderConversationHeader(context, contentX, contentY, contentW, mouseX, mouseY);
        renderAvatar(context, contentX + 28.0F, contentY + 3.0F, 21.0F,
                conversation.participantId(), conversation.username());
        drawMarqueeLabel(context, "conversation:" + conversation.id(), conversation.username(), 6.0F,
                contentX + 54.0F, contentY + 3.0F, contentX + contentW - 6.0F,
                contentY + 2.0F, 12.0F, MenuStyle.TEXT_PRIMARY, true, false);
        drawStatus(context, contentX + 54.0F, contentY + 12.0F,
                service.isUserOnline(conversation.participantId()), serverName(conversation.participantId()),
                contentW - 60.0F);
    }

    /** The header card with the square back button every sub-page (DM, group, form) shares. */
    private void renderConversationHeader(DrawContext context, float contentX, float contentY, float contentW,
                                          int mouseX, int mouseY) {
        drawChatCard(context, contentX, contentY, contentW, DIRECT_HEADER_HEIGHT, 0.0F);
        float backX = contentX + DIRECT_BACK_INSET;
        float backY = contentY + DIRECT_BACK_INSET;
        float hover = animatedHover("conversation:back",
                inside(mouseX, mouseY, backX, backY, DIRECT_BACK_SIZE, DIRECT_BACK_SIZE));
        if (hover > 0.0F) {
            drawChatCard(context, backX, backY, DIRECT_BACK_SIZE, DIRECT_BACK_SIZE, hover, hover);
        }
        UiMsdfIconAtlas.renderBackArrow(context,
                backX + DIRECT_BACK_SIZE / 2.0F, backY + DIRECT_BACK_SIZE / 2.0F, DIRECT_BACK_ICON_SIZE,
                applyGlobalAlpha(MenuStyle.mix(MenuStyle.TEXT_PRIMARY, 0xFFFFFFFF, hover * 0.18F)));
    }

    private static boolean onHeaderBack(double mouseX, double mouseY, float contentX, float contentY) {
        return inside(mouseX, mouseY, contentX + DIRECT_BACK_INSET, contentY + DIRECT_BACK_INSET,
                DIRECT_BACK_SIZE, DIRECT_BACK_SIZE);
    }

    private void renderGroupHeader(DrawContext context, MessengerService.Group group,
                                   float contentX, float contentY, float contentW, int mouseX, int mouseY) {
        renderConversationHeader(context, contentX, contentY, contentW, mouseX, mouseY);
        renderAvatarGrid(context, group.members(), contentX + 28.0F, contentY + 3.0F, 21.0F);
        drawMarqueeLabel(context, "group-header:" + group.id(), group.name(), 6.0F,
                contentX + 54.0F, contentY + 3.0F, contentX + contentW - 6.0F,
                contentY + 2.0F, 12.0F, MenuStyle.TEXT_PRIMARY, true, false);
        drawText(context, fitSingleLine(membersLine(group), contentW - 60.0F, 4.8F, "..."),
                contentX + 54.0F, contentY + 12.0F, 4.8F, MenuStyle.TEXT_MUTED, false);
    }

    private String membersLine(MessengerService.Group group) {
        int online = 0;
        for (MessengerService.GroupMember member : group.members()) if (service.isUserOnline(member.id())) online++;
        return String.format(Locale.ROOT, Lang.t("messenger.members_count"), group.members().size(), online);
    }

    private static float plusX(float contentX, float contentW) {
        return contentX + contentW - HEADER_CONTROL_HEIGHT - HEADER_TEXT_INSET;
    }

    private void renderPlusButton(DrawContext context, String key, float buttonX, float buttonY, int mouseX, int mouseY) {
        float size = HEADER_CONTROL_HEIGHT;
        float hover = animatedHover(key, inside(mouseX, mouseY, buttonX, buttonY, size, size));
        drawButtonSurface(context, buttonX, buttonY, size, size,
                MenuStyle.mix(MenuStyle.PANEL_ROW, MenuStyle.CARD_INNER, 0.12F), MenuStyle.BORDER, hover);
        int color = applyGlobalAlpha(MenuStyle.mix(MenuStyle.TEXT_MUTED, MenuStyle.TEXT_PRIMARY, 0.55F + hover * 0.45F));
        float arm = 7.0F;
        float stroke = 1.2F;
        float cx = buttonX + size / 2.0F;
        float cy = buttonY + size / 2.0F;
        rectangle.render(ShapeProperties.create(context.getMatrices(), cx - arm / 2.0F, cy - stroke / 2.0F, arm, stroke)
                .round(stroke / 2.0F).color(color).build());
        rectangle.render(ShapeProperties.create(context.getMatrices(), cx - stroke / 2.0F, cy - arm / 2.0F, stroke, arm)
                .round(stroke / 2.0F).color(color).build());
    }

    /** Right-aligned "Incoming · Sent" chips; render and click share these rectangles. */
    private float[][] requestTabRects(float contentX, float contentY, float contentW) {
        String[] labels = requestTabLabels();
        float[][] rects = new float[2][];
        float right = contentX + contentW - HEADER_TEXT_INSET;
        for (int index = 1; index >= 0; index--) {
            float w = MsdfFonts.bold().getWidth(labels[index], 5.0F) + 14.0F;
            rects[index] = new float[]{right - w, contentY, w, HEADER_CONTROL_HEIGHT};
            right -= w + 4.0F;
        }
        return rects;
    }

    private String[] requestTabLabels() {
        int incoming = service.getRequests().size();
        int outgoing = service.getOutgoingRequests().size();
        return new String[]{
                Lang.t("messenger.incoming") + (incoming > 0 ? " " + incoming : ""),
                Lang.t("messenger.outgoing") + (outgoing > 0 ? " " + outgoing : "")
        };
    }

    private void renderRequestTabs(DrawContext context, float contentX, float contentY, float contentW,
                                   int mouseX, int mouseY) {
        float[][] rects = requestTabRects(contentX, contentY, contentW);
        String[] labels = requestTabLabels();
        for (int index = 0; index < 2; index++) {
            float[] r = rects[index];
            boolean active = requestTab.ordinal() == index;
            float hover = animatedHover("requests:tab:" + index, inside(mouseX, mouseY, r[0], r[1], r[2], r[3]));
            int fill = active ? MenuStyle.mix(MenuStyle.PANEL_ROW_ACTIVE, MenuStyle.CHIP_ACTIVE, 0.42F)
                    : MenuStyle.mix(MenuStyle.PANEL_ROW, MenuStyle.CARD_INNER, 0.12F);
            int border = active ? MenuStyle.mix(MenuStyle.BORDER_LIGHT, MenuStyle.CHIP_ACTIVE, 0.55F) : MenuStyle.BORDER;
            drawButtonSurface(context, r[0], r[1], r[2], r[3], fill, border, hover);
            drawCentered(context, labels[index], r[0], r[1], r[2], r[3], 5.0F,
                    active ? MenuStyle.TEXT_PRIMARY : MenuStyle.mix(MenuStyle.TEXT_MUTED, MenuStyle.TEXT_PRIMARY, hover * 0.45F),
                    true);
        }
    }

    private void renderGlobalMessages(DrawContext context, float contentX, float listTop,
                                      float contentW, float listBottom, int mouseX, int mouseY) {
        List<MessengerService.Message> list = service.getGlobalMessages();
        if (list.isEmpty()) {
            drawEmptyState(context, Lang.t("messenger.empty_global"), contentX, listTop, contentW, listBottom);
            return;
        }
        renderMessageList(context, list, contentX, listTop, contentW, listBottom, mouseX, mouseY);
    }

    private void renderConversationMessages(DrawContext context, String conversationId, boolean group,
                                            float contentX, float listTop, float contentW, float listBottom,
                                            int mouseX, int mouseY) {
        List<MessengerService.Message> list = service.getMessages(conversationId);
        if (list.isEmpty()) {
            drawEmptyState(context, Lang.t(group ? "messenger.group_no_messages" : "messenger.empty_direct"),
                    contentX, listTop, contentW, listBottom);
            return;
        }
        renderMessageList(context, list, contentX, listTop, contentW, listBottom, mouseX, mouseY);
    }

    /**
     * Messages are bottom-anchored. {@code scroll} is the distance scrolled up from the newest
     * message: 0 shows the newest at the bottom, the maximum shows the oldest at the top, and
     * nothing beyond either end is reachable.
     */
    private void renderMessageList(DrawContext context, List<MessengerService.Message> list,
                                   float contentX, float listTop, float contentW, float listBottom,
                                   int mouseX, int mouseY) {
        float rowW = contentW - 6.0F;
        float total = messageListHeight(list, rowW);
        String key = section == Section.GLOBAL ? MessengerService.GLOBAL_CONVERSATION_ID : openConversationId();
        // A new message must not drag a reader who scrolled up away from what they are reading.
        if (key != null && key.equals(scrollListKey) && scroll > 0.0D && total > scrollListHeight) {
            scroll += total - scrollListHeight;
        }
        scrollListKey = key;
        scrollListHeight = total;
        scroll = clampScroll(scroll, total, listBottom - listTop);
        float rowY = listBottom - total + (float) scroll;
        for (MessengerService.Message message : list) {
            float height = messageHeight(message, rowW);
            if (rowY + height >= listTop && rowY <= listBottom) {
                renderMessage(context, message, contentX + 3.0F, rowY, rowW, mouseX, mouseY);
            }
            rowY += height;
        }
    }

    private static double clampScroll(double value, float total, float available) {
        return Math.max(0.0D, Math.min(Math.max(0.0D, total - available), value));
    }

    private float messageListHeight(List<MessengerService.Message> list, float rowW) {
        float total = 0.0F;
        for (MessengerService.Message message : list) total += messageHeight(message, rowW);
        return total;
    }

    private float messageHeight(MessengerService.Message message, float rowW) {
        if (hasCard(message)) return MESSAGE_TEXT_TOP + ATTACH_HEIGHT + MESSAGE_ROW_GAP + 2.0F;
        return MESSAGE_TEXT_TOP + wrappedLines(message, rowW).size() * MESSAGE_LINE_STEP + MESSAGE_ROW_GAP;
    }

    private boolean hasCard(MessengerService.Message message) {
        return message.attachment() != null && !isHiddenMessage(message);
    }

    /** In a group the local player still sees who they blocked speak, but not what they said. */
    private boolean isHiddenMessage(MessengerService.Message message) {
        return section == Section.GROUPS && service.isBlocked(message.senderId());
    }

    private String displayText(MessengerService.Message message) {
        return isHiddenMessage(message) ? Lang.t("messenger.hidden_message") : message.text();
    }

    /** Word-wrapped once per message and width; rendering and hit-testing reuse the cached lines. */
    private List<String> wrappedLines(MessengerService.Message message, float rowW) {
        float width = Math.max(30.0F, rowW - MESSAGE_TEXT_X - MESSAGE_TEXT_RIGHT_INSET);
        String text = displayText(message);
        WrappedText cached = wrapCache.get(message.id());
        if (cached != null && cached.width() == width && cached.text().equals(text)) return cached.lines();
        List<String> lines = wrapText(text, width, MESSAGE_TEXT_SIZE);
        wrapCache.put(message.id(), new WrappedText(text, width, lines));
        return lines;
    }

    private static List<String> wrapText(String text, float width, float size) {
        String value = text == null ? "" : text.replace('\r', ' ').replace('\n', ' ');
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : value.split(" ", -1)) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (renderedTextWidth(candidate, size) <= width) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
                line.setLength(0);
            }
            String rest = word;
            // A single word wider than the line is split at the last code point that still fits.
            while (!rest.isEmpty() && renderedTextWidth(rest, size) > width) {
                int end = rest.offsetByCodePoints(0, 1);
                while (end < rest.length()) {
                    int next = rest.offsetByCodePoints(end, 1);
                    if (renderedTextWidth(rest.substring(0, next), size) > width) break;
                    end = next;
                }
                lines.add(rest.substring(0, end));
                rest = rest.substring(end);
            }
            line.append(rest);
        }
        if (!line.isEmpty() || lines.isEmpty()) lines.add(line.toString());
        return List.copyOf(lines);
    }

    /* ---- card lists --------------------------------------------------- */

    private boolean insideList(double mouseX, double mouseY, float boxX, float boxY, float boxW, float boxH) {
        return mouseY >= listClipTop && mouseY <= listClipBottom && inside(mouseX, mouseY, boxX, boxY, boxW, boxH);
    }

    /** Clamps {@link #listScroll} for {@code count} rows and returns it. */
    private float clampListScroll(int count, float step, float height, float viewport) {
        float total = count == 0 ? 0.0F : (count - 1) * step + height;
        listScroll = Math.max(0.0D, Math.min(Math.max(0.0D, total - viewport), listScroll));
        return (float) listScroll;
    }

    /** A card under the mouse in a scrolled list; the top edge of the viewport hides cards above it. */
    private int scrolledIndex(double mouseX, double mouseY, float listX, float top, float listW,
                              float cardHeight, float step, float listBottom, int size) {
        if (mouseY < top) return -1;
        return cardIndex(mouseX, mouseY, listX, top - (float) listScroll, listW, cardHeight, step, listBottom, size);
    }

    private void renderSectionRows(DrawContext context, float contentX, float listTop, float contentW,
                                   float listBottom, int mouseX, int mouseY) {
        float viewport = listBottom - listTop;
        if (section == Section.GROUPS) {
            List<MessengerService.GroupInvite> invites = service.getGroupInvites();
            List<MessengerService.Group> groups = service.getGroups();
            int count = invites.size() + groups.size();
            float offset = clampListScroll(count, DIRECT_ROW_STEP, DIRECT_CARD_HEIGHT, viewport);
            for (int index = 0; index < count; index++) {
                float rowY = listTop - offset + index * DIRECT_ROW_STEP;
                if (rowY + DIRECT_CARD_HEIGHT < listTop || rowY > listBottom) continue;
                if (index < invites.size()) {
                    renderGroupInviteCard(context, invites.get(index), contentX, rowY, contentW, mouseX, mouseY);
                } else {
                    renderGroupCard(context, groups.get(index - invites.size()), contentX, rowY, contentW, mouseX, mouseY);
                }
            }
            if (count == 0) {
                drawEmptyState(context, Lang.t("messenger.no_groups"), contentX, listTop, contentW, listBottom);
            }
        } else if (section == Section.DIRECT) {
            List<MessengerService.Conversation> conversations = service.getConversations();
            float offset = clampListScroll(conversations.size(), DIRECT_ROW_STEP, DIRECT_CARD_HEIGHT, viewport);
            for (int index = 0; index < conversations.size(); index++) {
                float rowY = listTop - offset + index * DIRECT_ROW_STEP;
                if (rowY + DIRECT_CARD_HEIGHT < listTop || rowY > listBottom) continue;
                renderConversationCard(context, conversations.get(index), contentX, rowY, contentW, mouseX, mouseY);
            }
            if (conversations.isEmpty()) {
                drawEmptyState(context, Lang.t("messenger.no_conversations"), contentX, listTop, contentW, listBottom);
            }
        } else if (section == Section.REQUESTS) {
            renderAddFriendRow(context, contentX, listTop, contentW, mouseX, mouseY);
            float rowsTop = listTop + ADD_ROW_RESERVE;
            listClipTop = rowsTop;
            BatchedRectangle.flushIfBatching();
            context.enableScissor((int) contentX, (int) rowsTop, (int) (contentX + contentW), (int) listBottom);
            List<MessengerService.FriendRequest> rows = requestTab == RequestTab.INCOMING
                    ? service.getRequests() : service.getOutgoingRequests();
            float offset = clampListScroll(rows.size(), REQUEST_ROW_STEP, REQUEST_CARD_HEIGHT, listBottom - rowsTop);
            for (int index = 0; index < rows.size(); index++) {
                float rowY = rowsTop - offset + index * REQUEST_ROW_STEP;
                if (rowY + REQUEST_CARD_HEIGHT < rowsTop || rowY > listBottom) continue;
                if (requestTab == RequestTab.INCOMING) {
                    renderFriendRequestCard(context, rows.get(index), contentX, rowY, contentW, mouseX, mouseY);
                } else {
                    renderOutgoingRequestCard(context, rows.get(index), contentX, rowY, contentW, mouseX, mouseY);
                }
            }
            if (rows.isEmpty()) {
                drawEmptyState(context, Lang.t(requestTab == RequestTab.INCOMING
                        ? "messenger.no_requests" : "messenger.no_outgoing"), contentX, rowsTop, contentW, listBottom);
            }
            BatchedRectangle.flushIfBatching();
            context.disableScissor();
        } else {
            List<MessengerService.Friend> blocked = service.getBlocked();
            float offset = clampListScroll(blocked.size(), BLOCKED_ROW_STEP, BLOCKED_ROW_HEIGHT, viewport);
            for (int index = 0; index < blocked.size(); index++) {
                float rowY = listTop - offset + index * BLOCKED_ROW_STEP;
                if (rowY + BLOCKED_ROW_HEIGHT < listTop || rowY > listBottom) continue;
                MessengerService.Friend user = blocked.get(index);
                renderSelectableUser(context, user.id(), user.username(), contentX, rowY, contentW, mouseX, mouseY);
            }
            if (blocked.isEmpty()) {
                drawEmptyState(context, Lang.t("messenger.no_blocked"), contentX, listTop, contentW, listBottom);
            }
        }
    }

    /** Centred in the list area, wrapped onto two lines when it would not fit on one. */
    private void drawEmptyState(DrawContext context, String text, float listX, float listTop,
                                float listW, float listBottom) {
        List<String> lines = splitToWidth(text, listW - 12.0F, EMPTY_STATE_TEXT_SIZE);
        float lineH = EMPTY_STATE_TEXT_SIZE + 3.0F;
        float top = listTop + (listBottom - listTop - lines.size() * lineH) / 2.0F;
        for (int index = 0; index < lines.size(); index++) {
            drawCentered(context, lines.get(index), listX, top + index * lineH, listW, lineH,
                    EMPTY_STATE_TEXT_SIZE, MenuStyle.TEXT_MUTED, false);
        }
    }

    private void renderAddFriendRow(DrawContext context, float contentX, float rowY, float contentW,
                                    int mouseX, int mouseY) {
        float fieldW = contentW - ADD_BUTTON_WIDTH - GAP;
        nickField.render(context.getMatrices(), contentX, rowY, fieldW, Lang.t("messenger.nick_placeholder"),
                applyGlobalAlpha(1.0F));
        renderPrimaryButton(context, "requests:add", Lang.t("messenger.add"), contentX + fieldW + GAP, rowY,
                ADD_BUTTON_WIDTH, MessengerTextField.HEIGHT, !nickField.value().isBlank() && !formPending, mouseX, mouseY);
        ActionMessage message = actionMessage;
        if (message != null && System.currentTimeMillis() - message.shownAt() < MESSAGE_VISIBLE_MS) {
            drawText(context, fitSingleLine(message.text(), contentW - 4.0F, 4.8F, "..."), contentX + 2.0F,
                    rowY + MessengerTextField.HEIGHT + 3.0F, 4.8F, message.color(), false);
        }
    }

    private void renderConversationCard(DrawContext context, MessengerService.Conversation conversation,
                                        float rowX, float rowY, float rowW, int mouseX, int mouseY) {
        boolean hovered = insideList(mouseX, mouseY, rowX, rowY, rowW, DIRECT_CARD_HEIGHT);
        float hover = animatedHover("conversation-card:" + conversation.id(), hovered);
        drawChatCard(context, rowX, rowY, rowW, DIRECT_CARD_HEIGHT, hover);
        renderAvatar(context, rowX + CARD_AVATAR_INSET, rowY + CARD_AVATAR_INSET, CARD_AVATAR_SIZE,
                conversation.participantId(), conversation.username());
        float textX = rowX + CARD_TEXT_INSET;
        float nameY = rowY + DIRECT_CARD_NAME_OFFSET;
        float badgeW = renderUnread(context, conversation.unreadCount(), rowX + rowW - CARD_AVATAR_INSET - 2.0F,
                rowY + DIRECT_CARD_HEIGHT / 2.0F);
        float right = rowX + rowW - 7.0F - badgeW;
        drawMarqueeLabel(context, "conversation-card:" + conversation.id(), conversation.username(), 6.0F,
                textX, nameY, right - 5.0F,
                rowY, DIRECT_CARD_HEIGHT, MenuStyle.TEXT_PRIMARY, true, hovered);
        String preview = conversation.lastText() == null || conversation.lastText().isBlank()
                ? Lang.t("messenger.message_placeholder") : conversation.lastText();
        drawText(context, fitSingleLine(preview, Math.max(24.0F, right - textX), 5.2F, "..."),
                textX, nameY + CARD_TEXT_LINE_STEP, 5.2F, MenuStyle.TEXT_MUTED, false);
    }

    private void renderGroupCard(DrawContext context, MessengerService.Group group,
                                 float rowX, float rowY, float rowW, int mouseX, int mouseY) {
        boolean hovered = insideList(mouseX, mouseY, rowX, rowY, rowW, DIRECT_CARD_HEIGHT);
        float hover = animatedHover("group-card:" + group.id(), hovered);
        drawChatCard(context, rowX, rowY, rowW, DIRECT_CARD_HEIGHT, hover);
        renderAvatarGrid(context, group.members(), rowX + CARD_AVATAR_INSET, rowY + CARD_AVATAR_INSET, CARD_AVATAR_SIZE);
        float textX = rowX + CARD_TEXT_INSET;
        float nameY = rowY + DIRECT_CARD_NAME_OFFSET;
        float badgeW = renderUnread(context, group.unreadCount(), rowX + rowW - CARD_AVATAR_INSET - 2.0F,
                rowY + DIRECT_CARD_HEIGHT / 2.0F);
        float right = rowX + rowW - 7.0F - badgeW;
        drawMarqueeLabel(context, "group-card:" + group.id(), group.name(), 6.0F,
                textX, nameY, right - 5.0F, rowY, DIRECT_CARD_HEIGHT, MenuStyle.TEXT_PRIMARY, true, hovered);
        String preview = group.lastText() == null || group.lastText().isBlank()
                ? Lang.t("messenger.group_no_messages")
                : (group.lastSender() == null ? "" : group.lastSender() + ": ") + group.lastText();
        drawText(context, fitSingleLine(preview, Math.max(24.0F, right - textX), 5.2F, "..."),
                textX, nameY + CARD_TEXT_LINE_STEP, 5.2F, MenuStyle.TEXT_MUTED, false);
    }

    private void renderGroupInviteCard(DrawContext context, MessengerService.GroupInvite invite,
                                       float rowX, float rowY, float rowW, int mouseX, int mouseY) {
        float acceptX = requestAcceptX(rowX, rowW);
        float declineX = requestDeclineX(rowX, rowW);
        float buttonY = rowY + CARD_AVATAR_INSET;
        drawChatCard(context, rowX, rowY, rowW, DIRECT_CARD_HEIGHT, 0.0F);
        renderAvatar(context, rowX + CARD_AVATAR_INSET, rowY + CARD_AVATAR_INSET, CARD_AVATAR_SIZE,
                invite.inviterId(), invite.inviterUsername());
        float textX = rowX + CARD_TEXT_INSET;
        float nameY = rowY + DIRECT_CARD_NAME_OFFSET;
        float right = acceptX - 5.0F;
        drawMarqueeLabel(context, "group-invite:" + invite.id(), invite.groupName(), 6.0F,
                textX, nameY, right, rowY, DIRECT_CARD_HEIGHT, MenuStyle.TEXT_PRIMARY, true,
                insideList(mouseX, mouseY, rowX, rowY, right - rowX, DIRECT_CARD_HEIGHT));
        drawText(context, fitSingleLine(String.format(Locale.ROOT, Lang.t("messenger.invited_by"), invite.inviterUsername()),
                        Math.max(24.0F, right - textX), 5.2F, "..."),
                textX, nameY + CARD_TEXT_LINE_STEP, 5.2F, MenuStyle.TEXT_MUTED, false);
        renderRequestActionButton(context, "group-invite:accept:" + invite.id(), ACCEPT_ICON, acceptX, buttonY,
                mouseX, mouseY, MenuStyle.ACCENT_GREEN);
        renderRequestActionButton(context, "group-invite:decline:" + invite.id(), DECLINE_ICON, declineX, buttonY,
                mouseX, mouseY, ERROR_COLOR);
    }

    private void renderFriendRequestCard(DrawContext context, MessengerService.FriendRequest request,
                                          float rowX, float rowY, float rowW, int mouseX, int mouseY) {
        float acceptX = requestAcceptX(rowX, rowW);
        float declineX = requestDeclineX(rowX, rowW);
        float buttonY = rowY + CARD_AVATAR_INSET;
        boolean hovered = insideList(mouseX, mouseY, rowX, rowY, rowW, REQUEST_CARD_HEIGHT);
        boolean buttonHovered = inside(mouseX, mouseY, acceptX, buttonY, REQUEST_BUTTON_SIZE, REQUEST_BUTTON_SIZE)
                || inside(mouseX, mouseY, declineX, buttonY, REQUEST_BUTTON_SIZE, REQUEST_BUTTON_SIZE);
        float hover = animatedHover("request:" + request.id(), hovered && !buttonHovered);
        drawChatCard(context, rowX, rowY, rowW, REQUEST_CARD_HEIGHT, hover);
        renderAvatar(context, rowX + CARD_AVATAR_INSET, rowY + CARD_AVATAR_INSET, CARD_AVATAR_SIZE,
                request.requesterId(), request.requesterUsername());
        float textX = rowX + CARD_TEXT_INSET;
        float nameY = rowY + DIRECT_CARD_NAME_OFFSET;
        float right = acceptX - 5.0F;
        drawMarqueeLabel(context, "request:" + request.id(), request.requesterUsername(), 6.0F,
                textX, nameY, right,
                rowY, REQUEST_CARD_HEIGHT, MenuStyle.TEXT_PRIMARY, true, hovered && !buttonHovered);
        drawText(context, fitSingleLine(Lang.t("messenger.request_incoming"), Math.max(24.0F, right - textX), 5.2F, "..."),
                textX, nameY + CARD_TEXT_LINE_STEP, 5.2F, MenuStyle.TEXT_MUTED, false);

        renderRequestActionButton(context, "request:accept:" + request.id(), ACCEPT_ICON, acceptX, buttonY,
                mouseX, mouseY, MenuStyle.ACCENT_GREEN);
        renderRequestActionButton(context, "request:decline:" + request.id(), DECLINE_ICON, declineX, buttonY,
                mouseX, mouseY, ERROR_COLOR);
    }

    private void renderOutgoingRequestCard(DrawContext context, MessengerService.FriendRequest request,
                                           float rowX, float rowY, float rowW, int mouseX, int mouseY) {
        float cancelX = requestDeclineX(rowX, rowW);
        float buttonY = rowY + CARD_AVATAR_INSET;
        boolean buttonHovered = inside(mouseX, mouseY, cancelX, buttonY, REQUEST_BUTTON_SIZE, REQUEST_BUTTON_SIZE);
        boolean hovered = insideList(mouseX, mouseY, rowX, rowY, rowW, REQUEST_CARD_HEIGHT) && !buttonHovered;
        float hover = animatedHover("outgoing:" + request.id(), hovered);
        drawChatCard(context, rowX, rowY, rowW, REQUEST_CARD_HEIGHT, hover);
        renderAvatar(context, rowX + CARD_AVATAR_INSET, rowY + CARD_AVATAR_INSET, CARD_AVATAR_SIZE,
                request.targetId(), request.targetUsername());
        float textX = rowX + CARD_TEXT_INSET;
        float nameY = rowY + DIRECT_CARD_NAME_OFFSET;
        float right = cancelX - 5.0F;
        drawMarqueeLabel(context, "outgoing:" + request.id(), request.targetUsername(), 6.0F,
                textX, nameY, right, rowY, REQUEST_CARD_HEIGHT, MenuStyle.TEXT_PRIMARY, true, hovered);
        drawText(context, fitSingleLine(Lang.t("messenger.request_outgoing"), Math.max(24.0F, right - textX), 5.2F, "..."),
                textX, nameY + CARD_TEXT_LINE_STEP, 5.2F, MenuStyle.TEXT_MUTED, false);
        renderRequestActionButton(context, "outgoing:cancel:" + request.id(), DECLINE_ICON, cancelX, buttonY,
                mouseX, mouseY, ERROR_COLOR);
    }

    private static float requestDeclineX(float rowX, float rowW) {
        return rowX + rowW - CARD_AVATAR_INSET - REQUEST_BUTTON_SIZE;
    }

    private static float requestAcceptX(float rowX, float rowW) {
        return requestDeclineX(rowX, rowW) - REQUEST_BUTTON_GAP - REQUEST_BUTTON_SIZE;
    }

    private void renderSelectableUser(DrawContext context, String id, String username, float rowX,
                                      float rowY, float rowW, int mouseX, int mouseY) {
        boolean selected = id.equals(selectedFriendId) || id.equals(selectedConversationId);
        boolean hovered = insideList(mouseX, mouseY, rowX, rowY, rowW, BLOCKED_ROW_HEIGHT);
        float hover = animatedHover("user:" + id, hovered);
        drawButtonSurface(context, rowX, rowY, rowW, BLOCKED_ROW_HEIGHT,
                selected ? MenuStyle.PANEL_ROW_ACTIVE : MenuStyle.mix(MenuStyle.PANEL_ROW, MenuStyle.CARD_INNER,
                        0.10F), selected ? MenuStyle.CHIP_ACTIVE : MenuStyle.BORDER, hover);
        renderAvatar(context, rowX + 5.0F, rowY + 5.0F, 15.0F, id, username);
        String unblock = Lang.t("messenger.unblock");
        float unblockW = unblockWidth(unblock);
        float unblockX = unblockX(rowX, rowW, unblockW);
        float unblockY = unblockY(rowY);
        drawMarqueeLabel(context, "user:" + id, username, 5.8F, rowX + 26.0F, rowY + 5.0F,
                unblockX - 6.0F, rowY + 2.0F,
                19.0F, MenuStyle.TEXT_PRIMARY, false, hovered);
        float unblockHover = animatedHover("unblock:" + id,
                insideList(mouseX, mouseY, unblockX, unblockY, unblockW, UNBLOCK_HEIGHT));
        drawButtonSurface(context, unblockX, unblockY, unblockW, UNBLOCK_HEIGHT,
                MenuStyle.CARD_INNER, MenuStyle.BORDER, unblockHover);
        drawCentered(context, unblock, unblockX, unblockY, unblockW, UNBLOCK_HEIGHT, UNBLOCK_TEXT_SIZE,
                MenuStyle.mix(MenuStyle.TEXT_MUTED, MenuStyle.TEXT_PRIMARY, unblockHover * 0.45F), true);
    }

    /** Grows with the localized label so the larger text never touches the button edges. */
    private static float unblockWidth(String label) {
        return Math.max(UNBLOCK_MIN_WIDTH, MsdfFonts.bold().getWidth(label, UNBLOCK_TEXT_SIZE) + UNBLOCK_TEXT_PAD * 2.0F);
    }

    private static float unblockX(float rowX, float rowW, float unblockW) {
        return rowX + rowW - UNBLOCK_RIGHT_INSET - unblockW;
    }

    private static float unblockY(float rowY) {
        return rowY + (BLOCKED_ROW_HEIGHT - UNBLOCK_HEIGHT) / 2.0F;
    }

    /** A pill with the unread count, right-aligned at {@code right}; returns its width (0 when none). */
    private float renderUnread(DrawContext context, int count, float right, float centerY) {
        if (count <= 0) return 0.0F;
        String text = count > 99 ? "99+" : String.valueOf(count);
        float w = Math.max(UNREAD_HEIGHT, MsdfFonts.bold().getWidth(text, 4.6F) + 6.0F);
        float pillX = right - w;
        float pillY = centerY - UNREAD_HEIGHT / 2.0F;
        rectangle.render(ShapeProperties.create(context.getMatrices(), pillX, pillY, w, UNREAD_HEIGHT)
                .round(UNREAD_HEIGHT / 2.0F).color(applyGlobalAlpha(MenuStyle.CHIP_ACTIVE)).build());
        drawCentered(context, text, pillX, pillY, w, UNREAD_HEIGHT, 4.6F, 0xFFFFFFFF, true);
        return w;
    }

    /** One head, or a 2x2 grid of member heads in the same square. */
    private void renderAvatarGrid(DrawContext context, List<MessengerService.GroupMember> members,
                                  float gridX, float gridY, float size) {
        int count = Math.min(4, members.size());
        if (count <= 1) {
            MessengerService.GroupMember only = members.isEmpty() ? null : members.get(0);
            renderAvatar(context, gridX, gridY, size, only == null ? null : only.id(), only == null ? "Player" : only.username());
            return;
        }
        float cell = (size - 1.0F) / 2.0F;
        float[][] cells = switch (count) {
            case 2 -> new float[][]{{0, 0}, {1, 1}};
            case 3 -> new float[][]{{0, 0}, {1, 0}, {0.5F, 1}};
            default -> new float[][]{{0, 0}, {1, 0}, {0, 1}, {1, 1}};
        };
        for (int index = 0; index < count; index++) {
            MessengerService.GroupMember member = members.get(index);
            renderAvatar(context, gridX + cells[index][0] * (cell + 1.0F), gridY + cells[index][1] * (cell + 1.0F),
                    cell, member.id(), member.username());
        }
    }

    /* ---- messages ----------------------------------------------------- */

    private void renderMessage(DrawContext context, MessengerService.Message message, float rowX,
                               float rowY, float rowW, int mouseX, int mouseY) {
        boolean hidden = isHiddenMessage(message);
        float avatarHover = animatedHover("message-avatar:" + message.id(),
                insideList(mouseX, mouseY, rowX, rowY + 1.0F, MESSAGE_AVATAR_SIZE, MESSAGE_AVATAR_SIZE));
        if (hidden) {
            drawTexture(context, skins.anonymousHead(), rowX, rowY + 1.0F, MESSAGE_AVATAR_SIZE, applyGlobalAlpha(0xFFFFFFFF));
        } else {
            renderAvatar(context, rowX, rowY + 1.0F, MESSAGE_AVATAR_SIZE, message.senderId(), message.senderUsername());
        }
        if (avatarHover > 0.0F) {
            rectangle.render(ShapeProperties.create(context.getMatrices(), rowX, rowY + 1.0F,
                            MESSAGE_AVATAR_SIZE, MESSAGE_AVATAR_SIZE)
                    .round(avatarRadius(MESSAGE_AVATAR_SIZE))
                    .color(applyGlobalAlpha(MenuStyle.withAlpha(0xFFFFFFFF, avatarHover * 0.08F))).build());
        }
        int senderColor = MessengerService.isAdmin(message.senderId()) ? ADMIN_NAME_COLOR
                : isMe(message.senderId()) ? MenuStyle.CHIP_ACTIVE : MenuStyle.TEXT_PRIMARY;
        drawMarqueeLabel(context, "sender:" + message.senderId(), message.senderUsername(), 6.2F,
                rowX + MESSAGE_TEXT_X, rowY + 1.0F, rowX + rowW - 30.0F,
                rowY, 10.0F, senderColor, true,
                insideList(mouseX, mouseY, rowX + MESSAGE_TEXT_X, rowY, Math.max(1.0F, rowW - 52.0F), 10.0F));
        drawText(context, formatTime(message.createdAt()), rowX + rowW - 23.0F, rowY + 2.0F,
                5.0F, MenuStyle.TEXT_MUTED, false);
        if (hasCard(message)) {
            renderAttachment(context, message, rowX + MESSAGE_TEXT_X, rowY + MESSAGE_TEXT_TOP,
                    rowW - MESSAGE_TEXT_X - MESSAGE_TEXT_RIGHT_INSET, mouseX, mouseY);
            return;
        }
        float lineY = rowY + MESSAGE_TEXT_TOP;
        int textColor = hidden ? MenuStyle.TEXT_MUTED : MenuStyle.TEXT_PRIMARY;
        for (String line : wrappedLines(message, rowW)) {
            drawText(context, line, rowX + MESSAGE_TEXT_X, lineY, MESSAGE_TEXT_SIZE, textColor, false);
            lineY += MESSAGE_LINE_STEP;
        }
    }

    private void renderAttachment(DrawContext context, MessengerService.Message message, float cardX, float cardY,
                                  float cardW, int mouseX, int mouseY) {
        MessengerService.Attachment attachment = message.attachment();
        drawChatCard(context, cardX, cardY, cardW, ATTACH_HEIGHT, 0.0F);
        Identifier icon = "config".equals(attachment.type()) ? CONFIG_ICON
                : "death".equals(attachment.type()) ? DEATH_ICON : POINT_ICON;
        float iconCenterX = cardX + 6.0F + ATTACH_ICON_SIZE / 2.0F + 1.0F;
        UiMsdfIconAtlas.renderIconInkFitted(context, icon, iconCenterX, cardY + ATTACH_HEIGHT / 2.0F,
                ATTACH_ICON_SIZE, ATTACH_ICON_SIZE, applyGlobalAlpha(MenuStyle.TEXT_PRIMARY));
        float[] button = attachmentButton(message, cardX, cardY, cardW);
        AttachAction action = attachmentAction(message);
        float textX = cardX + 6.0F + ATTACH_ICON_SIZE + 7.0F;
        float textW = Math.max(20.0F, button[0] - 6.0F - textX);
        drawText(context, fitSingleLine(message.text(), textW, 5.6F, "..."), textX, cardY + 5.0F, 5.6F,
                MenuStyle.TEXT_PRIMARY, true);
        drawText(context, fitSingleLine(attachmentDetail(attachment), textW, 4.8F, "..."), textX, cardY + 15.0F, 4.8F,
                MenuStyle.TEXT_MUTED, false);
        boolean hovered = action.enabled() && insideList(mouseX, mouseY, button[0], button[1], button[2], button[3]);
        float hover = animatedHover("attachment:" + message.id(), hovered);
        int fill = action.enabled() ? MenuStyle.mix(MenuStyle.CARD_INNER, MenuStyle.CHIP_ACTIVE, 0.35F)
                : MenuStyle.mix(MenuStyle.PANEL_ROW, MenuStyle.CARD_INNER, 0.12F);
        drawButtonSurface(context, button[0], button[1], button[2], button[3], fill, MenuStyle.BORDER, hover);
        drawCentered(context, action.label(), button[0], button[1], button[2], button[3], 4.8F,
                action.enabled() ? MenuStyle.TEXT_PRIMARY : MenuStyle.TEXT_MUTED, true);
    }

    private String attachmentDetail(MessengerService.Attachment attachment) {
        if ("config".equals(attachment.type())) return Lang.t("messenger.config_card") + " · " + attachment.configId();
        return attachment.x() + " " + attachment.y() + " " + attachment.z() + " · " + dimensionLabel(attachment.dimension());
    }

    private static String dimensionLabel(String dimension) {
        return switch (dimension) {
            case "minecraft:overworld" -> Lang.t("messenger.dim_overworld");
            case "minecraft:the_nether" -> Lang.t("messenger.dim_nether");
            case "minecraft:the_end" -> Lang.t("messenger.dim_end");
            default -> dimension.substring(dimension.indexOf(':') + 1);
        };
    }

    private AttachAction attachmentAction(MessengerService.Message message) {
        MessengerService.Attachment attachment = message.attachment();
        if ("config".equals(attachment.type())) return new AttachAction(Lang.t("messenger.config_apply"), true);
        if (!attachment.serverKey().equals(ChatWorld.serverKey())) {
            return new AttachAction(Lang.t("messenger.point_elsewhere"), false);
        }
        return new AttachAction(Lang.t(ChatWorld.isMarked(message.id()) ? "messenger.point_hide" : "messenger.point_show"), true);
    }

    private float[] attachmentButton(MessengerService.Message message, float cardX, float cardY, float cardW) {
        float w = Math.max(44.0F, MsdfFonts.bold().getWidth(attachmentAction(message).label(), 4.8F) + 14.0F);
        float inset = (ATTACH_HEIGHT - ATTACH_BUTTON_HEIGHT) / 2.0F;
        return new float[]{cardX + cardW - inset - w, cardY + inset, w, ATTACH_BUTTON_HEIGHT};
    }

    private void runAttachment(MessengerService.Message message) {
        MessengerService.Attachment attachment = message.attachment();
        if ("config".equals(attachment.type())) {
            MenuScreen.INSTANCE.openConfigImportModal(attachment.configId());
        } else if (attachment.serverKey().equals(ChatWorld.serverKey())) {
            ChatWorld.toggleMarker(message.id(), message.text(), attachment);
        }
    }

    /* ---- input and attachments --------------------------------------- */

    private float inputBoxX(float contentX) {
        return contentX + (conversationOpen() ? INPUT_HEIGHT + GAP : 0.0F);
    }

    private float inputBoxWidth(float contentX, float contentW) {
        return contentX + contentW - SEND_WIDTH - GAP - inputBoxX(contentX);
    }

    private void renderInput(DrawContext context, float contentX, float inputY, float contentW, int mouseX, int mouseY) {
        float inputX = inputBoxX(contentX);
        float boxW = inputBoxWidth(contentX, contentW);
        if (conversationOpen()) {
            float hover = animatedHover("input:attach", inside(mouseX, mouseY, contentX, inputY, INPUT_HEIGHT, INPUT_HEIGHT));
            int fill = attachOpen ? MenuStyle.mix(MenuStyle.PANEL_ROW_ACTIVE, MenuStyle.CHIP_ACTIVE, 0.42F) : MenuStyle.CARD_INNER;
            drawButtonSurface(context, contentX, inputY, INPUT_HEIGHT, INPUT_HEIGHT, fill, MenuStyle.BORDER, hover);
            float arm = 7.0F;
            float stroke = 1.2F;
            float cx = contentX + INPUT_HEIGHT / 2.0F;
            float cy = inputY + INPUT_HEIGHT / 2.0F;
            int color = applyGlobalAlpha(MenuStyle.mix(MenuStyle.TEXT_MUTED, MenuStyle.TEXT_PRIMARY,
                    attachOpen ? 1.0F : 0.55F + hover * 0.45F));
            rectangle.render(ShapeProperties.create(context.getMatrices(), cx - arm / 2.0F, cy - stroke / 2.0F, arm, stroke)
                    .round(stroke / 2.0F).color(color).build());
            rectangle.render(ShapeProperties.create(context.getMatrices(), cx - stroke / 2.0F, cy - arm / 2.0F, stroke, arm)
                    .round(stroke / 2.0F).color(color).build());
        }
        drawSurface(context, inputX, inputY, boxW, INPUT_HEIGHT, MenuStyle.CARD_INNER, MenuStyle.BORDER);
        String value = input.isEmpty()
                ? Lang.t(conversationOpen() ? "messenger.message_placeholder" : "messenger.input")
                : input;
        float textX = inputX + 7.0F;
        float textOrigin = textX + 0.75F;
        float textY = MenuStyle.centerMsdfTextY(INPUT_TEXT_SIZE, inputY, INPUT_HEIGHT);
        float lineY = textY - 1.0F;
        float lineHeight = INPUT_TEXT_SIZE + 2.0F;
        if (input.isEmpty() && !inputFocused) {
            drawText(context, value, textOrigin, textY, INPUT_TEXT_SIZE, MenuStyle.TEXT_MUTED, false);
        } else {
            float available = boxW - 14.0F;
            int[] range = visibleInputRange(available);
            int visibleStart = range[0];
            int visibleEnd = range[1];
            String visible = input.substring(visibleStart, visibleEnd);
            float startX = textX;
            if (hasSelection()) {
                int start = Math.max(visibleStart, selectionStart());
                int end = Math.min(visibleEnd, selectionEnd());
                if (start < end) {
                    float sx = startX + renderedTextWidth(input.substring(visibleStart, start), INPUT_TEXT_SIZE);
                    float sw = renderedTextWidth(input.substring(start, end), INPUT_TEXT_SIZE);
                    rectangle.render(ShapeProperties.create(context.getMatrices(), sx, lineY, sw, lineHeight)
                            .color(applyGlobalAlpha(MenuStyle.mix(MenuStyle.CHIP_ACTIVE, MenuStyle.TEXT_PRIMARY, 0.35F))).build());
                }
            }
            drawText(context, visible, textOrigin, textY, INPUT_TEXT_SIZE, MenuStyle.TEXT_PRIMARY, false);
            if (inputFocused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
                int caret = Math.max(visibleStart, Math.min(cursor, visibleEnd));
                float caretX = startX + renderedTextWidth(input.substring(visibleStart, caret), INPUT_TEXT_SIZE);
                rectangle.render(ShapeProperties.create(context.getMatrices(), caretX, lineY, 0.7F, lineHeight)
                        .color(applyGlobalAlpha(MenuStyle.TEXT_PRIMARY)).build());
            }
        }
        String error = inputError();
        if (error != null) {
            int color = isModerationStatus(service.getStatus()) ? NOTICE_COLOR : ERROR_COLOR;
            drawText(context, error, contentX + 2.0F, inputY - 8.0F, 4.8F, color, false);
        }
        float sendX = contentX + contentW - SEND_WIDTH;
        long remaining = Math.max(0L, nextSendAt - System.currentTimeMillis());
        boolean enabled = !input.isEmpty() && !sendPending && remaining == 0L;
        float hover = animatedHover("input:send", enabled && inside(mouseX, mouseY, sendX, inputY, SEND_WIDTH, INPUT_HEIGHT));
        drawButtonSurface(context, sendX, inputY, SEND_WIDTH, INPUT_HEIGHT,
                enabled ? MenuStyle.CHIP_ACTIVE : MenuStyle.PANEL_ROW, MenuStyle.BORDER_LIGHT, hover);
        drawCentered(context, remaining > 0L ? String.format("%.1f", remaining / 1000.0D) : (sendPending ? "..." : Lang.t("messenger.send")), sendX, inputY, SEND_WIDTH, INPUT_HEIGHT,
                5.8F, enabled ? MenuStyle.TEXT_PRIMARY : MenuStyle.TEXT_MUTED, true);
    }

    private String[] attachLabels() {
        return new String[]{Lang.t("messenger.attach_here"), Lang.t("messenger.attach_death"), Lang.t("messenger.attach_config")};
    }

    private boolean[] attachEnabled() {
        boolean idle = !sendPending && nextSendAt <= System.currentTimeMillis();
        return new boolean[]{idle && ChatWorld.here() != null, idle && ChatWorld.lastDeath() != null, idle};
    }

    /** The menu sits on the attach button's left edge, just above the input row. */
    private static float[] attachMenuRect(float contentX, float inputY) {
        float h = ATTACH_MENU_ROW * 3.0F + 4.0F;
        return new float[]{contentX, inputY - 4.0F - h, ATTACH_MENU_WIDTH, h};
    }

    private void renderAttachMenu(DrawContext context, float contentX, float inputY, int mouseX, int mouseY) {
        float[] menu = attachMenuRect(contentX, inputY);
        float innerAlpha = ((MenuStyle.CARD_INNER >>> 24) & 0xFF) / 255.0F;
        int solid = 0xFF000000 | (MenuStyle.mix(MenuStyle.PANEL_BG, MenuStyle.CARD_INNER, innerAlpha) & 0x00FFFFFF);
        drawSurface(context, menu[0], menu[1], menu[2], menu[3], solid, MenuStyle.BORDER_LIGHT);
        String[] labels = attachLabels();
        boolean[] enabled = attachEnabled();
        Identifier[] icons = {POINT_ICON, DEATH_ICON, CONFIG_ICON};
        for (int index = 0; index < 3; index++) {
            float rowY = menu[1] + 2.0F + index * ATTACH_MENU_ROW;
            float rowX = menu[0] + 2.0F;
            float rowW = menu[2] - 4.0F;
            float hover = animatedHover("attach:" + index, enabled[index] && inside(mouseX, mouseY, rowX, rowY, rowW, ATTACH_MENU_ROW));
            if (hover > 0.0F) drawChatCard(context, rowX, rowY, rowW, ATTACH_MENU_ROW, hover, hover);
            int color = enabled[index] ? MenuStyle.TEXT_PRIMARY : MenuStyle.TEXT_MUTED;
            UiMsdfIconAtlas.renderIconInkFitted(context, icons[index], rowX + 9.0F, rowY + ATTACH_MENU_ROW / 2.0F,
                    7.5F, 7.5F, applyGlobalAlpha(color));
            drawText(context, labels[index], rowX + 18.0F, MenuStyle.centerMsdfTextY(5.4F, rowY, ATTACH_MENU_ROW),
                    5.4F, color, false);
        }
    }

    private boolean clickAttachMenu(double mouseX, double mouseY, float contentX, float inputY) {
        float[] menu = attachMenuRect(contentX, inputY);
        if (!inside(mouseX, mouseY, menu[0], menu[1], menu[2], menu[3])) return false;
        int index = (int) ((mouseY - menu[1] - 2.0F) / ATTACH_MENU_ROW);
        boolean[] enabled = attachEnabled();
        if (index < 0 || index > 2 || !enabled[index]) return true;
        attachOpen = false;
        String conversationId = openConversationId();
        if (conversationId == null) return true;
        String caption = input.isBlank() ? attachLabels()[index] : input.trim();
        if (index == 0 || index == 1) {
            ChatWorld.Point point = index == 0 ? ChatWorld.here() : ChatWorld.lastDeath();
            if (point == null) return true;
            beginSend();
            service.sendWithAttachment(conversationId, caption, ChatWorld.attachment(index == 0 ? "waypoint" : "death", point),
                    this::finishSend);
            return true;
        }
        // The current config goes up as a share key first, the card carries only that key.
        String payload;
        try {
            payload = ConfigManager.getInstance().exportCurrentToString();
        } catch (RuntimeException failed) {
            showActionError(Lang.t("messenger.error_action"));
            return true;
        }
        String configName = ConfigManager.getInstance().getCurrentConfigName();
        String author = service.getCurrentUser() == null ? null : service.getCurrentUser().username();
        String label = input.isBlank() ? (configName == null || configName.isBlank() ? caption : configName) : caption;
        beginSend();
        CompletableFuture.supplyAsync(() -> ConfigShareApi.upload(payload, author, null, configName))
                .thenAccept(id -> {
                    if (id == null || !id.matches("^[a-z0-9_]{1,5}-[a-z0-9]{4}-[a-z0-9]{4}$")) {
                        showActionError(Lang.t("messenger.error_action"));
                        finishSend(false);
                        return;
                    }
                    JsonObject attachment = new JsonObject();
                    attachment.addProperty("type", "config");
                    attachment.addProperty("configId", id);
                    service.sendWithAttachment(conversationId, label, attachment, this::finishSend);
                });
        return true;
    }

    private void beginSend() {
        pendingText = input;
        sendPending = true;
    }

    private void finishSend(boolean success) {
        sendResultSuccess = success;
        sendResultReady = true;
    }

    /* ---- forms -------------------------------------------------------- */

    private void openForm(Form next) {
        form = next;
        listScroll = 0.0D;
        pickedFriends.clear();
        formPending = false;
        attachOpen = false;
        inputFocused = false;
        nickField.setFocused(false);
        if (next == Form.RENAME) {
            MessengerService.Group group = selectedGroup();
            nameField.setValue(group == null ? "" : group.name());
            nameField.setFocused(true);
        } else if (next == Form.CREATE_GROUP) {
            nameField.setValue("");
            nameField.setFocused(true);
        } else {
            nameField.setFocused(false);
        }
    }

    private void closeForm() {
        form = Form.NONE;
        nameField.setFocused(false);
        pickedFriends.clear();
        listScroll = 0.0D;
    }

    private String formTitle() {
        return switch (form) {
            case CREATE_GROUP -> Lang.t("messenger.group_new");
            case INVITE -> Lang.t("messenger.group_invite_title");
            case RENAME -> Lang.t("messenger.group_rename_title");
            case SETTINGS -> Lang.t("messenger.settings");
            case NONE -> "";
        };
    }

    /** Friends the current form can pick: everyone for a new group, non-members for an invite. */
    private List<MessengerService.Friend> pickableFriends() {
        List<MessengerService.Friend> friends = service.getFriends();
        if (form != Form.INVITE) return friends;
        MessengerService.Group group = selectedGroup();
        if (group == null) return List.of();
        List<MessengerService.Friend> outside = new ArrayList<>();
        for (MessengerService.Friend friend : friends) if (group.member(friend.id()) == null) outside.add(friend);
        return outside;
    }

    /** Where the form's scrolled list starts; render and click must agree on it. */
    private float formListTop(float contentY) {
        float top = contentY + DIRECT_HEADER_HEIGHT + 6.0F;
        return form == Form.CREATE_GROUP ? top + MessengerTextField.HEIGHT + 16.0F : top + 10.0F;
    }

    private float formListBottom(float contentY, float contentH) {
        return contentY + contentH - FORM_BUTTON_HEIGHT - 6.0F;
    }

    private void renderForm(DrawContext context, float contentX, float contentY, float contentW, float contentH,
                            int mouseX, int mouseY) {
        renderConversationHeader(context, contentX, contentY, contentW, mouseX, mouseY);
        drawText(context, fitSingleLine(formTitle(), contentW - 34.0F, 6.4F, "..."), contentX + 28.0F,
                MenuStyle.centerMsdfTextY(6.4F, contentY, DIRECT_HEADER_HEIGHT), 6.4F, MenuStyle.TEXT_PRIMARY, true);
        float top = contentY + DIRECT_HEADER_HEIGHT + 6.0F;
        float bottom = contentY + contentH;
        switch (form) {
            case CREATE_GROUP, INVITE -> {
                if (form == Form.CREATE_GROUP) {
                    nameField.render(context.getMatrices(), contentX, top, contentW, Lang.t("messenger.group_name"),
                            applyGlobalAlpha(1.0F));
                }
                float labelY = form == Form.CREATE_GROUP ? top + MessengerTextField.HEIGHT + 7.0F : top + 1.0F;
                String label = Lang.t("messenger.pick_friends") + (pickedFriends.isEmpty() ? "" : " · " + pickedFriends.size());
                drawText(context, label, contentX + HEADER_TEXT_INSET, labelY, 5.0F, MenuStyle.TEXT_MUTED, true);
                renderPickList(context, contentX, formListTop(contentY), contentW, formListBottom(contentY, contentH),
                        mouseX, mouseY);
                boolean enabled = !formPending && (form == Form.CREATE_GROUP
                        ? !nameField.value().isBlank() : !pickedFriends.isEmpty());
                String button = form == Form.CREATE_GROUP ? Lang.t("messenger.group_create")
                        : Lang.t("messenger.invite") + (pickedFriends.isEmpty() ? "" : " (" + pickedFriends.size() + ")");
                renderPrimaryButton(context, "form:submit", formPending ? "..." : button, contentX,
                        bottom - FORM_BUTTON_HEIGHT, contentW, FORM_BUTTON_HEIGHT, enabled, mouseX, mouseY);
            }
            case RENAME -> {
                nameField.render(context.getMatrices(), contentX, top, contentW, Lang.t("messenger.group_name"),
                        applyGlobalAlpha(1.0F));
                renderPrimaryButton(context, "form:submit", formPending ? "..." : Lang.t("messenger.save"), contentX,
                        top + MessengerTextField.HEIGHT + 6.0F, contentW, FORM_BUTTON_HEIGHT,
                        !formPending && !nameField.value().isBlank(), mouseX, mouseY);
            }
            case SETTINGS -> renderSettings(context, contentX, top, contentW, bottom, mouseX, mouseY);
            case NONE -> {
            }
        }
        ActionMessage message = actionMessage;
        if (form != Form.SETTINGS && message != null && System.currentTimeMillis() - message.shownAt() < MESSAGE_VISIBLE_MS) {
            drawText(context, fitSingleLine(message.text(), contentW - 4.0F, 4.8F, "..."), contentX + 2.0F,
                    bottom - FORM_BUTTON_HEIGHT - 4.0F - 4.8F, 4.8F, message.color(), false);
        }
    }

    private void renderPickList(DrawContext context, float listX, float listTop, float listW, float listBottom,
                                int mouseX, int mouseY) {
        List<MessengerService.Friend> friends = pickableFriends();
        listClipTop = listTop;
        listClipBottom = listBottom;
        BatchedRectangle.flushIfBatching();
        context.enableScissor((int) listX, (int) listTop, (int) (listX + listW), (int) listBottom);
        float offset = clampListScroll(friends.size(), DIRECT_ROW_STEP, DIRECT_CARD_HEIGHT, listBottom - listTop);
        for (int index = 0; index < friends.size(); index++) {
            float rowY = listTop - offset + index * DIRECT_ROW_STEP;
            if (rowY + DIRECT_CARD_HEIGHT < listTop || rowY > listBottom) continue;
            renderPickRow(context, friends.get(index), listX, rowY, listW, mouseX, mouseY);
        }
        if (friends.isEmpty()) {
            drawEmptyState(context, Lang.t("messenger.no_friends_to_invite"), listX, listTop, listW, listBottom);
        }
        BatchedRectangle.flushIfBatching();
        context.disableScissor();
    }

    private void renderPickRow(DrawContext context, MessengerService.Friend friend, float rowX, float rowY, float rowW,
                               int mouseX, int mouseY) {
        boolean picked = pickedFriends.contains(friend.id());
        boolean hovered = insideList(mouseX, mouseY, rowX, rowY, rowW, DIRECT_CARD_HEIGHT);
        float hover = animatedHover("pick:" + friend.id(), hovered);
        drawChatCard(context, rowX, rowY, rowW, DIRECT_CARD_HEIGHT, hover);
        renderAvatar(context, rowX + CARD_AVATAR_INSET, rowY + CARD_AVATAR_INSET, CARD_AVATAR_SIZE,
                friend.id(), friend.username());
        float textX = rowX + CARD_TEXT_INSET;
        float nameY = rowY + DIRECT_CARD_NAME_OFFSET;
        float checkX = rowX + rowW - CARD_AVATAR_INSET - (CARD_AVATAR_SIZE + PICK_CHECK_SIZE) / 2.0F;
        float checkY = rowY + (DIRECT_CARD_HEIGHT - PICK_CHECK_SIZE) / 2.0F;
        drawMarqueeLabel(context, "pick:" + friend.id(), friend.username(), 6.0F, textX, nameY, checkX - 6.0F,
                rowY, DIRECT_CARD_HEIGHT, MenuStyle.TEXT_PRIMARY, true, hovered);
        drawStatus(context, textX, nameY + CARD_TEXT_LINE_STEP, service.isUserOnline(friend.id()),
                serverName(friend.id()), checkX - 6.0F - textX);
        float state = animatedState("pick-state:" + friend.id(), picked);
        int fill = MenuStyle.mix(MenuStyle.CARD_INNER, MenuStyle.ACCENT_GREEN, 0.20F + state * 0.35F);
        int border = MenuStyle.mix(MenuStyle.BORDER, MenuStyle.ACCENT_GREEN, 0.25F + state * 0.55F);
        rectangle.render(ShapeProperties.create(context.getMatrices(), checkX, checkY, PICK_CHECK_SIZE, PICK_CHECK_SIZE)
                .round(avatarRadius(PICK_CHECK_SIZE)).thickness(1.2F)
                .outlineColor(applyGlobalAlpha(border))
                .color(applyGlobalAlpha(MenuStyle.withAlpha(fill, 0.35F + state * 0.65F))).build());
        if (state > 0.01F) {
            UiMsdfIconAtlas.renderIconInkFitted(context, ACCEPT_ICON, checkX + PICK_CHECK_SIZE / 2.0F,
                    checkY + PICK_CHECK_SIZE / 2.0F, 6.5F, 6.5F,
                    applyGlobalAlpha(MenuStyle.withAlpha(0xFFFFFFFF, state)));
        }
    }

    private void renderSettings(DrawContext context, float contentX, float top, float contentW, float bottom,
                                int mouseX, int mouseY) {
        MessengerPrefs prefs = MessengerPrefs.get();
        boolean[] values = settingValues(prefs);
        String[][] rows = settingLabels();
        for (int index = 0; index < rows.length; index++) {
            float rowY = top + index * DIRECT_ROW_STEP;
            if (rowY + DIRECT_CARD_HEIGHT > bottom - 12.0F) break;
            boolean dimmed = index == 2 && !prefs.toasts();
            boolean hovered = !dimmed && inside(mouseX, mouseY, contentX, rowY, contentW, DIRECT_CARD_HEIGHT);
            float hover = animatedHover("setting:" + index, hovered);
            drawChatCard(context, contentX, rowY, contentW, DIRECT_CARD_HEIGHT, hover);
            float textX = contentX + 8.0F;
            float switchX = contentX + contentW - 8.0F - SWITCH_WIDTH;
            float nameY = rowY + DIRECT_CARD_NAME_OFFSET;
            drawText(context, fitSingleLine(rows[index][0], switchX - 6.0F - textX, 6.0F, "..."), textX, nameY, 6.0F,
                    dimmed ? MenuStyle.TEXT_MUTED : MenuStyle.TEXT_PRIMARY, true);
            drawText(context, fitSingleLine(rows[index][1], switchX - 6.0F - textX, 5.0F, "..."), textX,
                    nameY + CARD_TEXT_LINE_STEP, 5.0F, MenuStyle.TEXT_MUTED, false);
            renderSwitch(context, "setting-state:" + index, switchX, rowY + (DIRECT_CARD_HEIGHT - SWITCH_HEIGHT) / 2.0F,
                    values[index], dimmed);
        }
        drawCentered(context, fitSingleLine(Lang.t("messenger.ping_hint"), contentW - 8.0F, 4.8F, "..."), contentX,
                bottom - 10.0F, contentW, 10.0F, 4.8F, MenuStyle.TEXT_MUTED, false);
    }

    private boolean[] settingValues(MessengerPrefs prefs) {
        return new boolean[]{service.isSharingServer(), prefs.toasts(), prefs.friendOnlineToasts(),
                prefs.doNotDisturb(), prefs.highlightTeammates()};
    }

    private static String[][] settingLabels() {
        return new String[][]{
                {Lang.t("messenger.set_share"), Lang.t("messenger.set_share_desc")},
                {Lang.t("messenger.set_toasts"), Lang.t("messenger.set_toasts_desc")},
                {Lang.t("messenger.set_online"), Lang.t("messenger.set_online_desc")},
                {Lang.t("messenger.set_dnd"), Lang.t("messenger.set_dnd_desc")},
                {Lang.t("messenger.set_team"), Lang.t("messenger.set_team_desc")},
        };
    }

    private void toggleSetting(int index) {
        MessengerPrefs prefs = MessengerPrefs.get();
        switch (index) {
            case 0 -> service.setShareServer(!service.isSharingServer(), success -> {
                if (!success) showActionError(Lang.t("messenger.error_action"));
            });
            case 1 -> prefs.setToasts(!prefs.toasts());
            case 2 -> {
                if (prefs.toasts()) prefs.setFriendOnlineToasts(!prefs.friendOnlineToasts());
            }
            case 3 -> prefs.setDoNotDisturb(!prefs.doNotDisturb());
            case 4 -> prefs.setHighlightTeammates(!prefs.highlightTeammates());
            default -> {
            }
        }
    }

    private void renderSwitch(DrawContext context, String key, float switchX, float switchY, boolean on, boolean dimmed) {
        float state = animatedState(key, on);
        int track = MenuStyle.mix(MenuStyle.mix(MenuStyle.PANEL_ROW, MenuStyle.CARD_INNER, 0.2F), MenuStyle.ACCENT_GREEN,
                state * 0.85F);
        if (dimmed) track = MenuStyle.withAlpha(track, 0.5F);
        rectangle.render(ShapeProperties.create(context.getMatrices(), switchX, switchY, SWITCH_WIDTH, SWITCH_HEIGHT)
                .round(SWITCH_HEIGHT / 2.0F).thickness(1.0F)
                .outlineColor(applyGlobalAlpha(MenuStyle.BORDER)).color(applyGlobalAlpha(track)).build());
        float knob = SWITCH_HEIGHT - 3.0F;
        float knobX = switchX + 1.5F + state * (SWITCH_WIDTH - knob - 3.0F);
        rectangle.render(ShapeProperties.create(context.getMatrices(), knobX, switchY + 1.5F, knob, knob)
                .round(knob / 2.0F).color(applyGlobalAlpha(MenuStyle.withAlpha(0xFFFFFFFF, dimmed ? 0.5F : 0.95F))).build());
    }

    private void renderPrimaryButton(DrawContext context, String key, String label, float buttonX, float buttonY,
                                     float buttonW, float buttonH, boolean enabled, int mouseX, int mouseY) {
        float hover = animatedHover(key, enabled && inside(mouseX, mouseY, buttonX, buttonY, buttonW, buttonH));
        drawButtonSurface(context, buttonX, buttonY, buttonW, buttonH,
                enabled ? MenuStyle.CHIP_ACTIVE : MenuStyle.PANEL_ROW, MenuStyle.BORDER_LIGHT, hover);
        drawCentered(context, label, buttonX, buttonY, buttonW, buttonH, 5.6F,
                enabled ? MenuStyle.TEXT_PRIMARY : MenuStyle.TEXT_MUTED, true);
    }

    private void submitForm() {
        if (formPending) return;
        switch (form) {
            case CREATE_GROUP -> {
                String name = nameField.value().strip();
                if (name.isEmpty()) return;
                formPending = true;
                service.createGroup(name, List.copyOf(pickedFriends), groupId -> {
                    formPending = false;
                    if (groupId == null) showActionError(actionErrorFor(service.getStatus()));
                    else pendingGroupId = groupId;
                });
            }
            case INVITE -> {
                MessengerService.Group group = selectedGroup();
                if (group == null || pickedFriends.isEmpty()) return;
                formPending = true;
                List<String> targets = List.copyOf(pickedFriends);
                int[] left = {targets.size()};
                boolean[] failed = {false};
                for (String target : targets) {
                    service.inviteToGroup(group.id(), target, success -> {
                        if (!success) failed[0] = true;
                        if (--left[0] == 0) {
                            formPending = false;
                            if (failed[0]) showActionError(actionErrorFor(service.getStatus()));
                            else pendingCloseForm = true;
                        }
                    });
                }
            }
            case RENAME -> {
                MessengerService.Group group = selectedGroup();
                String name = nameField.value().strip();
                if (group == null || name.isEmpty()) return;
                formPending = true;
                service.renameGroup(group.id(), name, success -> {
                    formPending = false;
                    if (success) pendingCloseForm = true;
                    else showActionError(actionErrorFor(service.getStatus()));
                });
            }
            default -> {
            }
        }
    }

    private volatile boolean pendingCloseForm;

    /* ---- right panel -------------------------------------------------- */

    private void renderDetails(DrawContext context, float panelX, float panelY, float panelW,
                               float panelH, int mouseX, int mouseY) {
        if (groupPanelVisible()) {
            renderGroupPanel(context, selectedGroup(), panelX, panelY, panelW, panelH, mouseX, mouseY);
            return;
        }
        MessengerService.Friend selected = selectedProfileFriend();
        if (selected == null) {
            drawCentered(context, Lang.t("messenger.no_selected"), panelX, panelY + panelH / 2.0F - 8.0F,
                    panelW, 16.0F, 5.8F, MenuStyle.TEXT_MUTED, false);
            return;
        }
        if (section == Section.GROUPS && selectedGroup() != null) {
            float backHover = animatedHover("details:back", inside(mouseX, mouseY, panelX, panelY, GEAR_SIZE, GEAR_SIZE));
            if (backHover > 0.0F) drawChatCard(context, panelX, panelY, GEAR_SIZE, GEAR_SIZE, backHover, backHover);
            UiMsdfIconAtlas.renderBackArrow(context, panelX + GEAR_SIZE / 2.0F, panelY + GEAR_SIZE / 2.0F, 7.5F,
                    applyGlobalAlpha(MenuStyle.mix(MenuStyle.TEXT_MUTED, MenuStyle.TEXT_PRIMARY, 0.55F + backHover * 0.45F)));
        }
        renderAvatar(context, panelX + (panelW - 34.0F) / 2.0F, panelY + 10.0F,
                34.0F, selected.id(), selected.username());
        drawCentered(context, clip(selected.username(), 16), panelX, panelY + 49.0F,
                panelW, 12.0F, 6.4F, MenuStyle.TEXT_PRIMARY, true);
        drawCenteredStatus(context, panelX, panelY + 61.0F, panelW, selected.id());
        renderNote(context, selected, panelX, panelY + 70.0F, panelW, mouseX, mouseY);

        float buttonY = detailButtonsTop(panelY);
        List<DetailButton> buttons = detailButtons(selected);
        float limit = panelY + panelH;
        int shown = 0;
        for (DetailButton button : buttons) {
            float rowY = buttonY + shown * DETAIL_BUTTON_STEP;
            if (rowY + DETAIL_BUTTON_HEIGHT > limit) break;
            renderActionButton(context, button.key(), armedLabel(button), panelX, rowY, panelW, mouseX, mouseY,
                    button.style() == STYLE_ACTIVE, button.style() == STYLE_DESTRUCTIVE);
            shown++;
        }
        ActionMessage message = actionMessage;
        float messageY = buttonY + shown * DETAIL_BUTTON_STEP + 1.0F;
        if (message != null && System.currentTimeMillis() - message.shownAt() < MESSAGE_VISIBLE_MS) {
            List<String> lines = splitToWidth(message.text(), panelW, ACTION_MESSAGE_SIZE);
            float lineH = ACTION_MESSAGE_SIZE + 2.5F;
            for (int index = 0; index < lines.size(); index++) {
                if (messageY + (index + 1) * lineH > limit) break;
                drawCentered(context, lines.get(index), panelX, messageY + index * lineH, panelW, lineH,
                        ACTION_MESSAGE_SIZE, message.color(), false);
            }
        }
    }

    private static float detailButtonsTop(float panelY) {
        return panelY + 70.0F + MessengerTextField.HEIGHT + 4.0F;
    }

    private void renderNote(DrawContext context, MessengerService.Friend selected, float panelX, float noteY, float panelW,
                            int mouseX, int mouseY) {
        if (isMe(selected.id())) return;
        if (editingNote) {
            noteField.render(context.getMatrices(), panelX, noteY, panelW, Lang.t("messenger.note_placeholder"),
                    applyGlobalAlpha(1.0F));
            return;
        }
        String note = MessengerPrefs.get().note(selected.id());
        boolean hovered = inside(mouseX, mouseY, panelX, noteY, panelW, MessengerTextField.HEIGHT);
        float hover = animatedHover("details:note", hovered);
        if (hover > 0.0F) drawChatCard(context, panelX, noteY, panelW, MessengerTextField.HEIGHT, hover, hover);
        String text = note.isEmpty() ? Lang.t("messenger.note_add") : note;
        drawCentered(context, fitSingleLine(text, panelW - 8.0F, 4.8F, "..."), panelX, noteY, panelW,
                MessengerTextField.HEIGHT, 4.8F,
                note.isEmpty() ? MenuStyle.mix(MenuStyle.TEXT_MUTED, MenuStyle.TEXT_PRIMARY, hover * 0.45F)
                        : MenuStyle.mix(MenuStyle.TEXT_PRIMARY, MenuStyle.TEXT_MUTED, 0.25F), false);
    }

    private void startNoteEdit(String userId) {
        editingNote = true;
        noteField.setValue(MessengerPrefs.get().note(userId));
        noteField.setFocused(true);
        inputFocused = false;
    }

    private void finishNoteEdit() {
        if (!editingNote) return;
        editingNote = false;
        noteField.setFocused(false);
        MessengerService.Friend selected = selectedProfileFriend();
        if (selected != null && !isMe(selected.id())) MessengerPrefs.get().setNote(selected.id(), noteField.value());
    }

    /** Destructive group actions ask once more; the label tells the player to click again. */
    private String armedLabel(DetailButton button) {
        return button.key().equals(armedKey) && System.currentTimeMillis() - armedAt < CONFIRM_WINDOW_MS
                ? Lang.t("messenger.confirm") : button.label();
    }

    private boolean confirm(String key) {
        long now = System.currentTimeMillis();
        if (key.equals(armedKey) && now - armedAt < CONFIRM_WINDOW_MS) {
            armedKey = null;
            return true;
        }
        armedKey = key;
        armedAt = now;
        return false;
    }

    private List<DetailButton> detailButtons(MessengerService.Friend selected) {
        List<DetailButton> buttons = new ArrayList<>();
        String id = selected.id();
        boolean blockedUser = isBlocked(id);
        boolean friend = isFriend(id);
        if (blockedUser) {
            buttons.add(new DetailButton("details:message", Lang.t("messenger.unblock"), STYLE_NORMAL,
                    () -> service.unblockUser(id, this::showActionResult)));
        } else if (!isMe(id)) {
            buttons.add(new DetailButton("details:message", Lang.t("messenger.message"),
                    selectedFriendId != null && selectedFriendId.equals(id) ? STYLE_ACTIVE : STYLE_NORMAL,
                    () -> openDirect(id)));
            String friendLabel = friend ? Lang.t("messenger.remove_friend")
                    : service.hasSentFriendRequest(id) ? Lang.t("messenger.request_sent_button")
                    : Lang.t("messenger.add_friend");
            buttons.add(new DetailButton("details:friend", friendLabel, friend ? STYLE_DESTRUCTIVE : STYLE_NORMAL,
                    () -> toggleFriend(id)));
            buttons.add(new DetailButton("details:block", Lang.t("messenger.block"), STYLE_NORMAL, () -> {
                service.blockUser(id);
                selectedFriendId = null;
                selectedProfileId = null;
                selectedProfileUsername = null;
            }));
        }
        MessengerService.ServerInfo server = service.getServer(id);
        if (server != null && !isMe(id) && !isCurrentServer(server.address())) {
            buttons.add(new DetailButton("details:join", Lang.t("messenger.join"), STYLE_ACTIVE,
                    () -> ChatWorld.join(server.address(), server.name())));
        }
        MessengerService.Group group = section == Section.GROUPS ? selectedGroup() : null;
        MessengerService.ChatUser me = service.getCurrentUser();
        if (group != null && me != null && !isMe(id)) {
            MessengerService.GroupMember mine = group.member(me.id());
            MessengerService.GroupMember target = group.member(id);
            if (mine != null && target != null) {
                if (mine.isOwner()) {
                    boolean admin = target.isAdmin();
                    buttons.add(new DetailButton("details:admin", Lang.t(admin ? "messenger.remove_admin" : "messenger.make_admin"),
                            STYLE_NORMAL, () -> service.setGroupRole(group.id(), id, admin ? "member" : "admin", this::showActionResult)));
                    buttons.add(new DetailButton("details:owner", Lang.t("messenger.make_owner"), STYLE_NORMAL, () -> {
                        if (confirm("details:owner")) service.setGroupRole(group.id(), id, "owner", this::showActionResult);
                    }));
                }
                boolean canKick = !target.isOwner() && (mine.isOwner() || (mine.isAdmin() && !target.isAdmin()));
                if (canKick) {
                    buttons.add(new DetailButton("details:kick", Lang.t("messenger.kick"), STYLE_DESTRUCTIVE, () -> {
                        if (!confirm("details:kick")) return;
                        service.kickFromGroup(group.id(), id, success -> {
                            if (success) pendingProfileReset = true;
                            else showActionError(actionErrorFor(service.getStatus()));
                        });
                    }));
                }
            }
        }
        return buttons;
    }

    private volatile boolean pendingProfileReset;

    private static boolean isCurrentServer(String address) {
        String host = ServerUtil.getCurrentServerHost();
        if (host == null || host.isBlank() || address == null) return false;
        String target = address.toLowerCase(Locale.ROOT);
        int colon = target.indexOf(':');
        return host.equals(colon >= 0 ? target.substring(0, colon) : target);
    }

    private void openDirect(String userId) {
        selectedFriendId = userId;
        long requestGeneration = ++conversationRequestGeneration;
        service.openConversation(userId, id -> {
            if (requestGeneration != conversationRequestGeneration) return;
            if (id == null) showActionError(actionErrorFor(service.getStatus()));
            else actionMessage = null;
            pendingConversationId = id;
        });
    }

    private void toggleFriend(String userId) {
        if (isFriend(userId)) {
            service.removeFriend(userId, success -> {
                if (success) {
                    selectedFriendId = null;
                    selectedProfileId = null;
                    selectedProfileUsername = null;
                    actionMessage = null;
                } else {
                    showActionError(actionErrorFor(service.getStatus()));
                }
            });
        } else if (service.isSelf(userId)) {
            actionMessage = friendRequestFailure(MessengerService.FRIEND_REQUEST_SELF, System.currentTimeMillis());
        } else {
            service.sendFriendRequest(userId, success -> {
                long now = System.currentTimeMillis();
                actionMessage = success ? friendRequestNotice(service.getStatus(), now)
                        : friendRequestFailure(service.getStatus(), now);
            });
        }
    }

    private List<DetailButton> groupButtons(MessengerService.Group group) {
        List<DetailButton> buttons = new ArrayList<>();
        MessengerService.ChatUser me = service.getCurrentUser();
        MessengerService.GroupMember mine = me == null ? null : group.member(me.id());
        buttons.add(new DetailButton("group:invite", Lang.t("messenger.invite"), STYLE_ACTIVE, () -> openForm(Form.INVITE)));
        if (mine != null && !"member".equals(mine.role())) {
            buttons.add(new DetailButton("group:rename", Lang.t("messenger.rename"), STYLE_NORMAL, () -> openForm(Form.RENAME)));
        }
        buttons.add(new DetailButton("group:leave", Lang.t("messenger.leave_group"), STYLE_DESTRUCTIVE, () -> {
            if (!confirm("group:leave")) return;
            service.leaveGroup(group.id(), success -> {
                if (success) pendingCloseGroup = true;
                else showActionError(actionErrorFor(service.getStatus()));
            });
        }));
        if (mine != null && mine.isOwner()) {
            buttons.add(new DetailButton("group:delete", Lang.t("messenger.delete_group"), STYLE_DESTRUCTIVE, () -> {
                if (!confirm("group:delete")) return;
                service.deleteGroup(group.id(), success -> {
                    if (success) pendingCloseGroup = true;
                    else showActionError(actionErrorFor(service.getStatus()));
                });
            }));
        }
        return buttons;
    }

    private volatile boolean pendingCloseGroup;

    /** Members fill the panel between the title and the group's own buttons, scrolling if needed. */
    private static float groupButtonsTop(float panelY, float panelH, int buttons) {
        return panelY + panelH - buttons * DETAIL_BUTTON_STEP + (DETAIL_BUTTON_STEP - DETAIL_BUTTON_HEIGHT);
    }

    private void renderGroupPanel(DrawContext context, MessengerService.Group group, float panelX, float panelY,
                                  float panelW, float panelH, int mouseX, int mouseY) {
        drawText(context, Lang.t("messenger.members") + " · " + group.members().size(), panelX + 2.0F, panelY + 2.0F,
                5.6F, MenuStyle.TEXT_PRIMARY, true);
        List<DetailButton> buttons = groupButtons(group);
        float buttonsTop = groupButtonsTop(panelY, panelH, buttons.size());
        float listTop = panelY + 14.0F;
        float listBottom = buttonsTop - 6.0F;
        float total = group.members().isEmpty() ? 0.0F : (group.members().size() - 1) * MEMBER_STEP + MEMBER_ROW;
        membersScroll = Math.max(0.0D, Math.min(Math.max(0.0D, total - (listBottom - listTop)), membersScroll));
        BatchedRectangle.flushIfBatching();
        context.enableScissor((int) panelX, (int) listTop, (int) (panelX + panelW), (int) listBottom);
        for (int index = 0; index < group.members().size(); index++) {
            float rowY = listTop - (float) membersScroll + index * MEMBER_STEP;
            if (rowY + MEMBER_ROW < listTop || rowY > listBottom) continue;
            MessengerService.GroupMember member = group.members().get(index);
            boolean hovered = mouseY >= listTop && mouseY <= listBottom
                    && inside(mouseX, mouseY, panelX, rowY, panelW, MEMBER_ROW);
            float hover = animatedHover("member:" + member.id(), hovered);
            if (hover > 0.0F) drawChatCard(context, panelX, rowY, panelW, MEMBER_ROW, hover, hover);
            renderAvatar(context, panelX + 1.5F, rowY + 1.5F, 15.0F, member.id(), member.username());
            drawMarqueeLabel(context, "member:" + member.id(), member.username(), 5.0F,
                    panelX + 20.0F, rowY + COMPACT_FRIEND_NAME_OFFSET, panelX + panelW, rowY, 12.0F,
                    MessengerService.isAdmin(member.id()) ? ADMIN_NAME_COLOR
                            : isMe(member.id()) ? MenuStyle.CHIP_ACTIVE : MenuStyle.TEXT_PRIMARY, false, hovered);
            String role = member.isOwner() ? Lang.t("messenger.role_owner") : member.isAdmin() ? Lang.t("messenger.role_admin") : null;
            boolean online = service.isUserOnline(member.id());
            String status = Lang.t(online ? "messenger.online" : "messenger.offline");
            String line = MessengerService.isAdmin(member.id()) ? role
                    : role == null ? status : role + " · " + status;
            drawText(context, fitSingleLine(line, panelW - 21.0F, 4.8F, "..."), panelX + 20.0F,
                    rowY + COMPACT_FRIEND_STATUS_OFFSET, 4.8F,
                    role != null ? MenuStyle.mix(MenuStyle.TEXT_MUTED, MenuStyle.CHIP_ACTIVE, 0.6F)
                            : online ? MenuStyle.ACCENT_GREEN : MenuStyle.TEXT_MUTED, false);
        }
        BatchedRectangle.flushIfBatching();
        context.disableScissor();
        for (int index = 0; index < buttons.size(); index++) {
            DetailButton button = buttons.get(index);
            renderActionButton(context, button.key(), armedLabel(button), panelX, buttonsTop + index * DETAIL_BUTTON_STEP,
                    panelW, mouseX, mouseY, button.style() == STYLE_ACTIVE, button.style() == STYLE_DESTRUCTIVE);
        }
        ActionMessage message = actionMessage;
        if (message != null && System.currentTimeMillis() - message.shownAt() < MESSAGE_VISIBLE_MS) {
            drawCentered(context, fitSingleLine(message.text(), panelW, ACTION_MESSAGE_SIZE, "..."), panelX,
                    buttonsTop - 6.0F - ACTION_MESSAGE_SIZE - 2.0F, panelW, ACTION_MESSAGE_SIZE + 2.0F,
                    ACTION_MESSAGE_SIZE, message.color(), false);
        }
    }

    /** At most two centered lines; a message never runs past the details panel. */
    private static List<String> splitToWidth(String text, float width, float size) {
        if (renderedTextWidth(text, size) <= width) return List.of(text);
        int split = -1;
        for (int space = text.indexOf(' '); space > 0; space = text.indexOf(' ', space + 1)) {
            if (renderedTextWidth(text.substring(0, space), size) > width) break;
            split = space;
        }
        return split < 0 ? List.of(text) : List.of(text.substring(0, split), text.substring(split + 1));
    }

    private void renderActionButton(DrawContext context, String key, String label, float buttonX, float buttonY,
                                    float buttonW, int mouseX, int mouseY, boolean active, boolean destructive) {
        boolean hovered = inside(mouseX, mouseY, buttonX, buttonY, buttonW, 18.0F);
        float hover = animatedHover(key, hovered);
        int fill = destructive
                ? MenuStyle.mix(0xFFB64055, MenuStyle.CARD_INNER, 0.42F)
                : active ? MenuStyle.mix(MenuStyle.PANEL_ROW_ACTIVE, MenuStyle.CHIP_ACTIVE, 0.42F)
                : MenuStyle.mix(MenuStyle.PANEL_ROW, MenuStyle.CARD_INNER, 0.10F);
        drawButtonSurface(context, buttonX, buttonY, buttonW, 18.0F, fill,
                destructive ? 0xFFE76868 : active ? MenuStyle.CHIP_ACTIVE : MenuStyle.BORDER, hover);
        int text = destructive ? 0xFFFFC1C8 : active ? MenuStyle.TEXT_PRIMARY : MenuStyle.TEXT_MUTED;
        drawCentered(context, fitSingleLine(label, buttonW - 6.0F, 5.2F, "..."), buttonX, buttonY, buttonW, 18.0F, 5.2F,
                MenuStyle.mix(text, MenuStyle.TEXT_PRIMARY, hover * 0.45F), true);
    }

    private void renderRequestActionButton(DrawContext context, String key, Identifier icon, float buttonX, float buttonY,
                                           int mouseX, int mouseY, int accent) {
        float hover = animatedHover(key,
                insideList(mouseX, mouseY, buttonX, buttonY, REQUEST_BUTTON_SIZE, REQUEST_BUTTON_SIZE));
        int fill = MenuStyle.mix(MenuStyle.CARD_INNER, accent, 0.20F + hover * 0.18F);
        int border = MenuStyle.mix(MenuStyle.BORDER, accent, 0.45F + hover * 0.30F);
        rectangle.render(ShapeProperties.create(context.getMatrices(), buttonX, buttonY,
                        REQUEST_BUTTON_SIZE, REQUEST_BUTTON_SIZE)
                .round(avatarRadius(REQUEST_BUTTON_SIZE)).thickness(1.2F)
                .outlineColor(applyGlobalAlpha(border)).color(applyGlobalAlpha(fill)).build());
        int iconColor = MenuStyle.mix(MenuStyle.mix(accent, 0xFFFFFFFF, 0.35F), 0xFFFFFFFF, hover * 0.45F);
        UiMsdfIconAtlas.renderIconInkFitted(context, icon,
                buttonX + REQUEST_BUTTON_SIZE / 2.0F, buttonY + REQUEST_BUTTON_SIZE / 2.0F,
                REQUEST_ICON_SIZE, REQUEST_ICON_SIZE, applyGlobalAlpha(iconColor));
    }

    private MessengerService.Friend selectedFriend() {
        if (selectedFriendId == null) return null;
        for (MessengerService.Friend friend : service.getFriends()) {
            if (selectedFriendId.equals(friend.id())) return friend;
        }
        return null;
    }

    private MessengerService.Friend selectedProfileFriend() {
        if (selectedProfileId != null && selectedProfileUsername != null) {
            return new MessengerService.Friend(selectedProfileId, selectedProfileUsername, 0L);
        }
        MessengerService.Friend friend = selectedFriend();
        if (friend != null) return friend;
        MessengerService.Conversation conversation = section == Section.DIRECT ? selectedConversation() : null;
        return conversation == null ? null
                : new MessengerService.Friend(conversation.participantId(), conversation.username(), 0L);
    }

    private MessengerService.Conversation selectedConversation() {
        if (selectedConversationId == null) return null;
        for (MessengerService.Conversation conversation : service.getConversations()) {
            if (selectedConversationId.equals(conversation.id())) return conversation;
        }
        return null;
    }

    private boolean isBlocked(String userId) {
        return service.isBlocked(userId);
    }

    private boolean isFriend(String userId) {
        return service.isFriend(userId);
    }

    private void selectProfile(String id, String username) {
        if (MessengerService.isAdmin(id)) return;
        finishNoteEdit();
        selectedProfileId = id;
        selectedProfileUsername = username;
        selectedFriendId = id;
    }

    private void clearProfile() {
        finishNoteEdit();
        selectedProfileId = null;
        selectedProfileUsername = null;
        selectedFriendId = null;
    }

    private void openGroup(String groupId) {
        clearProfile();
        selectedGroupId = groupId;
        inputFocused = false;
        input = "";
        cursor = selectionAnchor = 0;
        scroll = 0.0D;
        membersScroll = 0.0D;
        attachOpen = false;
        actionMessage = null;
        service.clearError();
        service.selectConversation(groupId);
    }

    private void closeConversation() {
        conversationRequestGeneration++;
        selectedConversationId = null;
        selectedGroupId = null;
        clearProfile();
        pendingConversationId = null;
        input = "";
        cursor = selectionAnchor = 0;
        scroll = 0.0D;
        attachOpen = false;
        actionMessage = null;
        service.clearError();
        service.selectConversation(null);
    }

    private MessengerService.Message messageAt(double mouseX, double mouseY, float contentX, float listTop,
                                               float contentW, float listBottom, boolean attachmentButton) {
        List<MessengerService.Message> list = section == Section.GLOBAL
                ? service.getGlobalMessages() : service.getMessages(openConversationId());
        float rowW = contentW - 6.0F;
        float total = messageListHeight(list, rowW);
        float rowY = listBottom - total + (float) clampScroll(scroll, total, listBottom - listTop);
        float rowX = contentX + 3.0F;
        if (mouseY < listTop || mouseY > listBottom) return null;
        for (MessengerService.Message message : list) {
            if (attachmentButton) {
                if (hasCard(message) && attachmentAction(message).enabled()) {
                    float[] button = attachmentButton(message, rowX + MESSAGE_TEXT_X, rowY + MESSAGE_TEXT_TOP,
                            rowW - MESSAGE_TEXT_X - MESSAGE_TEXT_RIGHT_INSET);
                    if (inside(mouseX, mouseY, button[0], button[1], button[2], button[3])) return message;
                }
            } else if (inside(mouseX, mouseY, rowX, rowY + 1.0F, MESSAGE_AVATAR_SIZE, MESSAGE_AVATAR_SIZE)) {
                return message;
            }
            rowY += messageHeight(message, rowW);
        }
        return null;
    }

    /* ---- input handling ----------------------------------------------- */

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!open || button != 0) return false;
        float pageX = x + SIDE;
        float pageY = y + TOP;
        float pageW = width - SIDE * 2.0F;
        float pageH = height - TOP - SIDE;
        float leftX = pageX + PANEL_PAD;
        float centerX = pageX + LEFT_WIDTH + GAP;
        float rightX = pageX + pageW - RIGHT_WIDTH;
        float centerW = rightX - GAP - centerX;
        float contentX = centerX + PANEL_PAD;
        float contentY = pageY + PANEL_PAD;
        float contentW = centerW - PANEL_PAD * 2.0F;
        float contentH = pageH - PANEL_PAD * 2.0F;
        float inputY = contentY + contentH - INPUT_HEIGHT;

        if (attachOpen) {
            if (conversationOpen() && clickAttachMenu(mouseX, mouseY, contentX, inputY)) return true;
            attachOpen = false;
            if (inside(mouseX, mouseY, contentX, inputY, INPUT_HEIGHT, INPUT_HEIGHT)) return true;
        }
        if (editingNote && !noteField.contains(mouseX, mouseY)) finishNoteEdit();
        // A field only takes clicks while it is on screen; its last rectangle may belong to another page.
        boolean nameVisible = form == Form.CREATE_GROUP || form == Form.RENAME;
        boolean nickVisible = form == Form.NONE && section == Section.REQUESTS;
        boolean nameHit = nameVisible ? nameField.mouseClicked(mouseX, mouseY) : blur(nameField);
        boolean nickHit = nickVisible ? nickField.mouseClicked(mouseX, mouseY) : blur(nickField);
        boolean noteHit = editingNote && noteField.mouseClicked(mouseX, mouseY);
        if (nameHit || nickHit || noteHit) {
            inputFocused = false;
            return true;
        }

        float profileX = leftX;
        float profileW = LEFT_WIDTH - PANEL_PAD * 2.0F;
        if (inside(mouseX, mouseY, gearX(profileX, profileW), contentY + 4.0F, GEAR_SIZE, GEAR_SIZE)) {
            if (form == Form.SETTINGS) closeForm();
            else openForm(Form.SETTINGS);
            return true;
        }

        float navY = pageY + NAV_TOP_OFFSET;
        float navW = LEFT_WIDTH - PANEL_PAD * 2.0F;
        for (Section item : Section.values()) {
            float rowY = navY + item.ordinal() * (NAV_HEIGHT + NAV_GAP);
            if (inside(mouseX, mouseY, leftX, rowY, navW, NAV_HEIGHT)) {
                boolean same = section == item && form == Form.NONE;
                section = item;
                closeForm();
                scroll = 0.0D;
                listScroll = 0.0D;
                service.clearError();
                inputFocused = false;
                input = "";
                cursor = selectionAnchor = 0;
                attachOpen = false;
                if (!same || item == Section.DIRECT || item == Section.GROUPS) closeConversation();
                if (item == Section.REQUESTS) service.refreshRequestsAsync();
                return true;
            }
        }

        float previewY = pageY + FRIEND_PREVIEW_TOP_OFFSET + 14.0F;
        List<MessengerService.Friend> previewFriends = service.getFriends();
        for (int index = 0; index < Math.min(5, previewFriends.size()); index++) {
            if (inside(mouseX, mouseY, leftX, previewY + index * 20.0F,
                    LEFT_WIDTH - PANEL_PAD * 2.0F, 18.0F)) {
                selectProfile(previewFriends.get(index).id(), previewFriends.get(index).username());
                return true;
            }
        }

        if (clickDetails(mouseX, mouseY, rightX + PANEL_PAD, pageY + PANEL_PAD,
                RIGHT_WIDTH - PANEL_PAD * 2.0F, pageH - PANEL_PAD * 2.0F)) {
            return true;
        }

        if (form != Form.NONE) {
            clickForm(mouseX, mouseY, contentX, contentY, contentW, contentH);
            return inside(mouseX, mouseY, pageX, pageY, pageW, pageH);
        }

        boolean conversationOpen = conversationOpen();
        if (conversationOpen && onHeaderBack(mouseX, mouseY, contentX, contentY)) {
            closeConversation();
            return true;
        }
        if (!conversationOpen && section == Section.GROUPS
                && inside(mouseX, mouseY, plusX(contentX, contentW), contentY, HEADER_CONTROL_HEIGHT, HEADER_CONTROL_HEIGHT)) {
            openForm(Form.CREATE_GROUP);
            return true;
        }
        if (!conversationOpen && section == Section.REQUESTS) {
            float[][] tabs = requestTabRects(contentX, contentY, contentW);
            for (int index = 0; index < 2; index++) {
                if (inside(mouseX, mouseY, tabs[index][0], tabs[index][1], tabs[index][2], tabs[index][3])) {
                    requestTab = RequestTab.values()[index];
                    listScroll = 0.0D;
                    return true;
                }
            }
        }

        boolean hasInput = section == Section.GLOBAL || conversationOpen;
        float inputX = inputBoxX(contentX);
        float inputW = inputBoxWidth(contentX, contentW);
        if (hasInput && inside(mouseX, mouseY, contentX + contentW - SEND_WIDTH, inputY, SEND_WIDTH, INPUT_HEIGHT)) {
            submit();
            return true;
        }
        if (conversationOpen && inside(mouseX, mouseY, contentX, inputY, INPUT_HEIGHT, INPUT_HEIGHT)) {
            attachOpen = !attachOpen;
            return true;
        }
        if (hasInput && inside(mouseX, mouseY, inputX, inputY, inputW, INPUT_HEIGHT)) {
            inputFocused = true;
            int clicked = cursorFromMouse(mouseX, inputX, inputW);
            if ((GLFW.glfwGetKey(MinecraftClient.getInstance().getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(MinecraftClient.getInstance().getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS)) {
                cursor = clicked;
            } else {
                cursor = clicked;
                selectionAnchor = clicked;
            }
            draggingSelection = true;
            return true;
        }
        inputFocused = false;

        float listTop = contentY + (conversationOpen ? DIRECT_HEADER_HEIGHT + 5.0F : 27.0F);
        float listBottom = listBottom(contentY, contentH, hasInput) - (hasInput ? inputErrorReserve() : 0.0F);
        if (hasInput) {
            MessengerService.Message card = messageAt(mouseX, mouseY, contentX, listTop, contentW, listBottom, true);
            if (card != null) {
                runAttachment(card);
                return true;
            }
            MessengerService.Message message = messageAt(mouseX, mouseY, contentX, listTop, contentW, listBottom, false);
            if (message != null) {
                if (!isHiddenMessage(message)) selectProfile(message.senderId(), message.senderUsername());
                return true;
            }
            if (inside(mouseX, mouseY, centerX, pageY, centerW, pageH)) return true;
        }

        if (section == Section.GROUPS && !conversationOpen) {
            List<MessengerService.GroupInvite> invites = service.getGroupInvites();
            List<MessengerService.Group> groups = service.getGroups();
            int index = scrolledIndex(mouseX, mouseY, contentX, listTop, contentW,
                    DIRECT_CARD_HEIGHT, DIRECT_ROW_STEP, listBottom, invites.size() + groups.size());
            if (index >= 0) {
                float rowY = listTop - (float) listScroll + index * DIRECT_ROW_STEP;
                if (index < invites.size()) {
                    MessengerService.GroupInvite invite = invites.get(index);
                    float buttonY = rowY + CARD_AVATAR_INSET;
                    if (inside(mouseX, mouseY, requestAcceptX(contentX, contentW), buttonY, REQUEST_BUTTON_SIZE, REQUEST_BUTTON_SIZE)) {
                        service.acceptGroupInvite(invite.id(), groupId -> {
                            if (groupId == null) showActionError(actionErrorFor(service.getStatus()));
                            else pendingGroupId = groupId;
                        });
                    } else if (inside(mouseX, mouseY, requestDeclineX(contentX, contentW), buttonY, REQUEST_BUTTON_SIZE, REQUEST_BUTTON_SIZE)) {
                        service.declineGroupInvite(invite.id(), null);
                    } else {
                        selectProfile(invite.inviterId(), invite.inviterUsername());
                    }
                } else {
                    openGroup(groups.get(index - invites.size()).id());
                }
                return true;
            }
        } else if (section == Section.DIRECT && !conversationOpen) {
            List<MessengerService.Conversation> currentConversations = service.getConversations();
            int index = scrolledIndex(mouseX, mouseY, contentX, listTop, contentW,
                    DIRECT_CARD_HEIGHT, DIRECT_ROW_STEP, listBottom, currentConversations.size());
            if (index >= 0) {
                MessengerService.Conversation conversation = currentConversations.get(index);
                selectedConversationId = conversation.id();
                selectedFriendId = conversation.participantId();
                selectProfile(conversation.participantId(), conversation.username());
                inputFocused = false;
                input = "";
                cursor = selectionAnchor = 0;
                scroll = 0.0D;
                service.clearError();
                service.selectConversation(selectedConversationId);
                return true;
            }
        } else if (section == Section.REQUESTS) {
            float fieldW = contentW - ADD_BUTTON_WIDTH - GAP;
            if (inside(mouseX, mouseY, contentX + fieldW + GAP, listTop, ADD_BUTTON_WIDTH, MessengerTextField.HEIGHT)) {
                addFriendByNick();
                return true;
            }
            float rowsTop = listTop + ADD_ROW_RESERVE;
            List<MessengerService.FriendRequest> rows = requestTab == RequestTab.INCOMING
                    ? service.getRequests() : service.getOutgoingRequests();
            int index = scrolledIndex(mouseX, mouseY, contentX, rowsTop, contentW,
                    REQUEST_CARD_HEIGHT, REQUEST_ROW_STEP, listBottom, rows.size());
            if (index >= 0) {
                float buttonY = rowsTop - (float) listScroll + index * REQUEST_ROW_STEP + CARD_AVATAR_INSET;
                MessengerService.FriendRequest request = rows.get(index);
                if (requestTab == RequestTab.INCOMING) {
                    if (inside(mouseX, mouseY, requestAcceptX(contentX, contentW), buttonY,
                            REQUEST_BUTTON_SIZE, REQUEST_BUTTON_SIZE)) {
                        service.acceptFriendRequest(request.id());
                    } else if (inside(mouseX, mouseY, requestDeclineX(contentX, contentW), buttonY,
                            REQUEST_BUTTON_SIZE, REQUEST_BUTTON_SIZE)) {
                        service.declineFriendRequest(request.id(), success -> {
                            if (!success) showActionError(actionErrorFor(service.getStatus()));
                        });
                    } else {
                        selectProfile(request.requesterId(), request.requesterUsername());
                    }
                } else if (inside(mouseX, mouseY, requestDeclineX(contentX, contentW), buttonY,
                        REQUEST_BUTTON_SIZE, REQUEST_BUTTON_SIZE)) {
                    service.cancelFriendRequest(request.id(), success -> {
                        if (!success) showActionError(actionErrorFor(service.getStatus()));
                    });
                } else {
                    selectProfile(request.targetId(), request.targetUsername());
                }
                return true;
            }
        } else if (section == Section.BLOCKED) {
            List<MessengerService.Friend> currentBlocked = service.getBlocked();
            int index = scrolledIndex(mouseX, mouseY, contentX, listTop, contentW,
                    BLOCKED_ROW_HEIGHT, BLOCKED_ROW_STEP, listBottom, currentBlocked.size());
            if (index >= 0) {
                MessengerService.Friend blocked = currentBlocked.get(index);
                float unblockW = unblockWidth(Lang.t("messenger.unblock"));
                if (inside(mouseX, mouseY, unblockX(contentX, contentW, unblockW),
                        unblockY(listTop - (float) listScroll + index * BLOCKED_ROW_STEP), unblockW, UNBLOCK_HEIGHT)) {
                    service.unblockUser(blocked.id(), this::showActionResult);
                } else {
                    selectProfile(blocked.id(), blocked.username());
                }
                return true;
            }
        }

        if (inside(mouseX, mouseY, centerX, pageY, centerW, pageH)) return true;
        return inside(mouseX, mouseY, pageX, pageY, pageW, pageH);
    }

    private static boolean blur(MessengerTextField field) {
        field.setFocused(false);
        return false;
    }

    private void addFriendByNick() {
        String nick = nickField.value().strip();
        if (nick.isEmpty() || formPending) return;
        formPending = true;
        service.addFriendByUsername(nick, success -> {
            formPending = false;
            long now = System.currentTimeMillis();
            if (success) {
                actionMessage = friendRequestNotice(service.getStatus(), now);
                pendingNickClear = true;
            } else {
                actionMessage = friendRequestFailure(service.getStatus(), now);
            }
        });
    }

    private volatile boolean pendingNickClear;

    private boolean clickDetails(double mouseX, double mouseY, float panelX, float panelY, float panelW, float panelH) {
        if (!inside(mouseX, mouseY, panelX - PANEL_PAD, panelY - PANEL_PAD, panelW + PANEL_PAD * 2.0F, panelH + PANEL_PAD * 2.0F)) {
            return false;
        }
        if (groupPanelVisible()) {
            MessengerService.Group group = selectedGroup();
            List<DetailButton> buttons = groupButtons(group);
            float buttonsTop = groupButtonsTop(panelY, panelH, buttons.size());
            for (int index = 0; index < buttons.size(); index++) {
                if (inside(mouseX, mouseY, panelX, buttonsTop + index * DETAIL_BUTTON_STEP, panelW, DETAIL_BUTTON_HEIGHT)) {
                    buttons.get(index).action().run();
                    return true;
                }
            }
            float listTop = panelY + 14.0F;
            float listBottom = buttonsTop - 6.0F;
            if (mouseY >= listTop && mouseY <= listBottom) {
                int index = (int) ((mouseY - listTop + membersScroll) / MEMBER_STEP);
                float rowY = listTop - (float) membersScroll + index * MEMBER_STEP;
                if (index >= 0 && index < group.members().size() && mouseY <= rowY + MEMBER_ROW) {
                    MessengerService.GroupMember member = group.members().get(index);
                    selectProfile(member.id(), member.username());
                }
            }
            return true;
        }
        MessengerService.Friend selected = selectedProfileFriend();
        if (selected == null) return true;
        if (section == Section.GROUPS && selectedGroup() != null && inside(mouseX, mouseY, panelX, panelY, GEAR_SIZE, GEAR_SIZE)) {
            clearProfile();
            return true;
        }
        if (!isMe(selected.id()) && !editingNote
                && inside(mouseX, mouseY, panelX, panelY + 70.0F, panelW, MessengerTextField.HEIGHT)) {
            startNoteEdit(selected.id());
            return true;
        }
        float buttonY = detailButtonsTop(panelY);
        List<DetailButton> buttons = detailButtons(selected);
        for (int index = 0; index < buttons.size(); index++) {
            float rowY = buttonY + index * DETAIL_BUTTON_STEP;
            if (rowY + DETAIL_BUTTON_HEIGHT > panelY + panelH) break;
            if (inside(mouseX, mouseY, panelX, rowY, panelW, DETAIL_BUTTON_HEIGHT)) {
                buttons.get(index).action().run();
                return true;
            }
        }
        return true;
    }

    private void clickForm(double mouseX, double mouseY, float contentX, float contentY, float contentW, float contentH) {
        if (onHeaderBack(mouseX, mouseY, contentX, contentY)) {
            closeForm();
            return;
        }
        float top = contentY + DIRECT_HEADER_HEIGHT + 6.0F;
        float bottom = contentY + contentH;
        switch (form) {
            case CREATE_GROUP, INVITE -> {
                if (inside(mouseX, mouseY, contentX, bottom - FORM_BUTTON_HEIGHT, contentW, FORM_BUTTON_HEIGHT)) {
                    submitForm();
                    return;
                }
                List<MessengerService.Friend> friends = pickableFriends();
                float listTop = formListTop(contentY);
                int index = scrolledIndex(mouseX, mouseY, contentX, listTop, contentW,
                        DIRECT_CARD_HEIGHT, DIRECT_ROW_STEP, formListBottom(contentY, contentH), friends.size());
                if (index >= 0) {
                    String id = friends.get(index).id();
                    if (!pickedFriends.remove(id) && pickedFriends.size() < 19) pickedFriends.add(id);
                }
            }
            case RENAME -> {
                if (inside(mouseX, mouseY, contentX, top + MessengerTextField.HEIGHT + 6.0F, contentW, FORM_BUTTON_HEIGHT)) {
                    submitForm();
                }
            }
            case SETTINGS -> {
                for (int index = 0; index < settingLabels().length; index++) {
                    float rowY = top + index * DIRECT_ROW_STEP;
                    if (rowY + DIRECT_CARD_HEIGHT > bottom - 12.0F) break;
                    if (inside(mouseX, mouseY, contentX, rowY, contentW, DIRECT_CARD_HEIGHT)) {
                        toggleSetting(index);
                        return;
                    }
                }
            }
            case NONE -> {
            }
        }
    }

    private static float headerRightTextX(float contentX, float contentW, float textWidth) {
        return contentX + contentW - textWidth - HEADER_TEXT_INSET;
    }

    private static float listBottom(float contentY, float contentH, boolean hasInput) {
        return contentY + contentH - (hasInput ? INPUT_HEIGHT + 5.0F : 2.0F);
    }

    private static int cardIndex(double mouseX, double mouseY, float listX, float listTop, float listW,
                          float cardHeight, float step, float listBottom, int size) {
        if (!inside(mouseX, mouseY, listX, listTop, listW, Math.max(0.0F, listBottom - listTop))) {
            return -1;
        }
        int index = (int) ((mouseY - listTop) / step);
        if (index < 0 || index >= size) return -1;
        float rowY = listTop + index * step;
        if (rowY + cardHeight > listBottom) return -1;
        return inside(mouseX, mouseY, listX, rowY, listW, cardHeight) ? index : -1;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!open) return false;
        float pageX = x + SIDE;
        float pageW = width - SIDE * 2.0F;
        float rightX = pageX + pageW - RIGHT_WIDTH;
        if (mouseX >= rightX) {
            if (!groupPanelVisible()) return false;
            membersScroll = Math.max(0.0D, membersScroll - amount * MEMBER_STEP);
            return true;
        }
        if (form == Form.NONE && (section == Section.GLOBAL || conversationOpen())) {
            // Wheel up (positive) moves towards older messages; render clamps the upper end.
            scroll = Math.max(0.0D, scroll + amount * MESSAGE_ROW);
            return true;
        }
        listScroll = Math.max(0.0D, listScroll - amount * LIST_SCROLL_STEP);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!open || !draggingSelection || button != 0) return false;
        float pageX = x + SIDE;
        float pageW = width - SIDE * 2.0F;
        float centerX = pageX + LEFT_WIDTH + GAP;
        float rightX = pageX + pageW - RIGHT_WIDTH;
        float contentX = centerX + PANEL_PAD;
        float contentW = rightX - GAP - centerX - PANEL_PAD * 2.0F;
        cursor = cursorFromMouse(mouseX, inputBoxX(contentX), inputBoxWidth(contentX, contentW));
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) draggingSelection = false;
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!open) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (attachOpen) {
                attachOpen = false;
                return true;
            }
            if (editingNote) {
                finishNoteEdit();
                return true;
            }
            if (nameField.focused() || nickField.focused()) {
                nameField.setFocused(false);
                nickField.setFocused(false);
                return true;
            }
            return false;
        }
        boolean enter = keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER;
        if (editingNote) {
            if (enter) finishNoteEdit();
            else noteField.keyPressed(keyCode, modifiers);
            return true;
        }
        if (nameField.focused()) {
            if (enter) submitForm();
            else nameField.keyPressed(keyCode, modifiers);
            return true;
        }
        if (nickField.focused()) {
            if (enter) addFriendByNick();
            else nickField.keyPressed(keyCode, modifiers);
            return true;
        }
        if (!inputFocused || !canEditInput()) return false;
        boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (control && keyCode == GLFW.GLFW_KEY_A) {
            selectionAnchor = 0;
            cursor = input.length();
            inputFocused = true;
            return true;
        }
        if (control && keyCode == GLFW.GLFW_KEY_C) {
            if (hasSelection()) MinecraftClient.getInstance().keyboard.setClipboard(selectedText());
            return true;
        }
        if (control && keyCode == GLFW.GLFW_KEY_X) {
            if (!sendPending && hasSelection()) {
                MinecraftClient.getInstance().keyboard.setClipboard(selectedText());
                deleteSelection();
            }
            return true;
        }
        if (enter) {
            submit();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !input.isEmpty()) {
            if (sendPending) return true;
            if (hasSelection()) deleteSelection();
            else if (cursor > 0) {
                int start = input.offsetByCodePoints(cursor, -1);
                input = input.substring(0, start) + input.substring(cursor);
                cursor = start;
                selectionAnchor = cursor;
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE && !sendPending) {
            if (hasSelection()) deleteSelection();
            else if (cursor < input.length()) {
                int end = input.offsetByCodePoints(cursor, 1);
                input = input.substring(0, cursor) + input.substring(end);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            if (cursor > 0) cursor = input.offsetByCodePoints(cursor, -1);
            if (!control) selectionAnchor = cursor;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            if (cursor < input.length()) cursor = input.offsetByCodePoints(cursor, 1);
            if (!control) selectionAnchor = cursor;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME) { cursor = 0; if (!control) selectionAnchor = cursor; return true; }
        if (keyCode == GLFW.GLFW_KEY_END) { cursor = input.length(); if (!control) selectionAnchor = cursor; return true; }
        if (keyCode == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            String clipboard = MinecraftClient.getInstance().keyboard.getClipboard();
            if (clipboard != null && !clipboard.isEmpty() && !sendPending) {
                replaceSelection(clipboard);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!open) return false;
        MessengerTextField field = editingNote ? noteField
                : nameField.focused() ? nameField : nickField.focused() ? nickField : null;
        if (field != null) {
            field.charTyped(chr);
            return true;
        }
        if (!inputFocused || !canEditInput() || sendPending || Character.isISOControl(chr)) return false;
        replaceSelection(String.valueOf(chr));
        return true;
    }

    private void submit() {
        if (!canEditInput() || input.isEmpty() || sendPending || nextSendAt > System.currentTimeMillis()) return;
        MessengerService.Conversation direct = section == Section.DIRECT ? selectedConversation() : null;
        if (direct != null && service.isBlockedBy(direct.participantId())) {
            service.reportBlocked(MessengerService.BLOCKED_BY_RECIPIENT);
            return;
        }
        if (direct != null && isBlocked(direct.participantId())) {
            service.reportBlocked(MessengerService.RECIPIENT_BLOCKED);
            return;
        }
        beginSend();
        String conversationId = openConversationId();
        if (conversationOpen() && conversationId != null) {
            service.send(conversationId, pendingText, this::finishSend);
            return;
        }
        if (section != Section.GLOBAL) {
            sendPending = false;
            pendingText = null;
            return;
        }
        service.sendGlobal(pendingText, this::finishSend);
    }

    private boolean canEditInput() {
        return form == Form.NONE && (section == Section.GLOBAL || conversationOpen());
    }

    private void applyAsyncState() {
        if (pendingConversationId != null) {
            selectedConversationId = pendingConversationId;
            pendingConversationId = null;
            section = Section.DIRECT;
            selectedGroupId = null;
            closeForm();
            service.selectConversation(selectedConversationId);
        }
        if (pendingGroupId != null) {
            String groupId = pendingGroupId;
            pendingGroupId = null;
            section = Section.GROUPS;
            closeForm();
            openGroup(groupId);
        }
        if (pendingCloseForm) {
            pendingCloseForm = false;
            closeForm();
        }
        if (pendingCloseGroup) {
            pendingCloseGroup = false;
            closeConversation();
        }
        if (pendingProfileReset) {
            pendingProfileReset = false;
            clearProfile();
        }
        if (pendingNickClear) {
            pendingNickClear = false;
            nickField.setValue("");
        }
        // Removed from a group (kicked, or deleted elsewhere) while it was open.
        if (section == Section.GROUPS && selectedGroupId != null && selectedGroup() == null) {
            closeConversation();
            if (form == Form.INVITE || form == Form.RENAME) closeForm();
        }
        if (sendResultReady) {
            sendResultReady = false;
            sendPending = false;
            if (sendResultSuccess && pendingText != null && input.equals(pendingText)) {
                input = "";
                cursor = selectionAnchor = 0;
                // Start slowmode only after MessengerService accepted the
                // message into the local list, which is the same point at
                // which it is already visible in the chat.
                nextSendAt = System.currentTimeMillis() + 5_000L;
            }
            pendingText = null;
        }
    }

    private boolean hasSelection() {
        return selectionStart() != selectionEnd();
    }

    private int selectionStart() {
        return Math.min(cursor, selectionAnchor);
    }

    private int selectionEnd() {
        return Math.max(cursor, selectionAnchor);
    }

    private String selectedText() {
        return input.substring(selectionStart(), selectionEnd());
    }

    private void deleteSelection() {
        int start = selectionStart();
        int end = selectionEnd();
        input = input.substring(0, start) + input.substring(end);
        cursor = selectionAnchor = start;
    }

    private void replaceSelection(String value) {
        if (sendPending || value == null || value.isEmpty()) return;
        value = value.replace('\r', ' ').replace('\n', ' ');
        int start = selectionStart();
        int end = selectionEnd();
        String candidate = input.substring(0, start) + value + input.substring(end);
        if (candidate.codePointCount(0, candidate.length()) > MAX_INPUT_CODEPOINTS) return;
        input = candidate;
        cursor = start + value.length();
        selectionAnchor = cursor;
        inputFocused = true;
    }

    private int cursorFromMouse(double mouseX, float inputX, float inputW) {
        float available = inputW - 14.0F;
        int[] range = visibleInputRange(available);
        int start = range[0];
        int end = range[1];
        float target = (float) mouseX - inputX - 7.0F;
        int best = start;
        float bestDistance = Float.MAX_VALUE;
        for (int index = start; index <= end; ) {
            float width = renderedTextWidth(input.substring(start, index), INPUT_TEXT_SIZE);
            float distance = Math.abs(width - target);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = index;
            }
            if (index >= end) break;
            index = input.offsetByCodePoints(index, 1);
        }
        return best;
    }

    /** Keeps the caret visible while rendering the input as one physical line. */
    private int[] visibleInputRange(float available) {
        if (input.isEmpty() || renderedTextWidth(input, INPUT_TEXT_SIZE) <= available) {
            return new int[]{0, input.length()};
        }

        int start = 0;
        int end = input.length();
        if (cursor >= input.length()) {
            while (start < end && renderedTextWidth(input.substring(start, end), INPUT_TEXT_SIZE) > available) {
                start = input.offsetByCodePoints(start, 1);
            }
            return new int[]{start, end};
        }

        // When editing in the middle, reserve most of the line for the text
        // before the caret, then append as much text after it as fits.
        start = cursor;
        while (start > 0) {
            int previous = input.offsetByCodePoints(start, -1);
            if (renderedTextWidth(input.substring(previous, cursor), INPUT_TEXT_SIZE)
                    > available * 0.55F) break;
            start = previous;
        }
        end = cursor;
        while (end < input.length()) {
            int next = input.offsetByCodePoints(end, 1);
            if (renderedTextWidth(input.substring(start, next), INPUT_TEXT_SIZE) > available) break;
            end = next;
        }
        return new int[]{start, Math.max(start, end)};
    }

    /** The send error line sits above the input; the message list stops above it instead of under it. */
    private float inputErrorReserve() {
        return inputError() != null ? INPUT_ERROR_RESERVE : 0.0F;
    }

    private void showActionError(String text) {
        actionMessage = new ActionMessage(text, ERROR_COLOR, System.currentTimeMillis());
    }

    static ActionMessage friendRequestNotice(String status, long now) {
        if ("Friend added".equals(status)) return new ActionMessage(Lang.t("messenger.friend_added"), MenuStyle.ACCENT_GREEN, now);
        if ("Friend request pending".equals(status)) {
            return new ActionMessage(Lang.t("messenger.friend_request_already_sent"), NOTICE_COLOR, now);
        }
        return new ActionMessage(Lang.t("messenger.friend_request_sent"), MenuStyle.ACCENT_GREEN, now);
    }

    static ActionMessage friendRequestFailure(String status, long now) {
        if (MessengerService.FRIEND_REQUEST_SELF.equals(status)) {
            return new ActionMessage(Lang.t("messenger.friend_request_self"), NOTICE_COLOR, now);
        }
        return new ActionMessage(actionErrorFor(status), ERROR_COLOR, now);
    }

    private void showActionResult(boolean success) {
        if (success) actionMessage = null;
        else showActionError(actionErrorFor(service.getStatus()));
    }

    private String inputError() {
        return inputErrorFor(service.getStatus(), service.getStatusChangedAt(), System.currentTimeMillis());
    }

    /** The line above the input: only real failures, and each one for {@link #MESSAGE_VISIBLE_MS}. */
    static String inputErrorFor(String status, long changedAt, long now) {
        if (status == null || NON_ERROR_STATUSES.contains(status) || isFriendActionStatus(status)) return null;
        if (now - changedAt >= MESSAGE_VISIBLE_MS) return null;
        if (status.equals(MessengerService.BLOCKED_BY_RECIPIENT)) return Lang.t("messenger.error_blocked_by");
        if (status.equals(MessengerService.RECIPIENT_BLOCKED)) return Lang.t("messenger.error_recipient_blocked");
        if (status.startsWith(MessengerService.MESSAGE_BLOCKED)) {
            return moderationText(status.substring(MessengerService.MESSAGE_BLOCKED.length()));
        }
        if (status.equals("chat_slow_down")) return Lang.t("messenger.error_slowmode");
        if (status.equals("Chat server is outdated")) return Lang.t("messenger.error_server");
        if (status.equals("User blocked")) return Lang.t("messenger.error_blocked_user");
        return Lang.t("messenger.error_send");
    }

    /** The reason code the server's moderation refused a message with. */
    static String moderationText(String reason) {
        return Lang.t(switch (reason) {
            case "spam" -> "messenger.mod_spam";
            case "repeated_flood" -> "messenger.mod_flood";
            case "abusive_content" -> "messenger.mod_abuse";
            case "politics" -> "messenger.mod_politics";
            case "hate" -> "messenger.mod_hate";
            case "advertising" -> "messenger.mod_ads";
            default -> "messenger.error_blocked";
        });
    }

    /** Moderation refusals are rule reminders, shown in amber rather than as failures. */
    static boolean isModerationStatus(String status) {
        return status != null && status.startsWith(MessengerService.MESSAGE_BLOCKED);
    }

    /** Friend and group actions report in their own panel, never as a send error above the input. */
    private static boolean isFriendActionStatus(String status) {
        return status.startsWith("friend_") || status.startsWith("group_") || status.equals("too_many_pending_requests")
                || status.equals("Friend request failed") || status.equals("Request failed")
                || status.equals("Group failed") || status.equals("invalid_group_name")
                || status.equals("lookup_slow_down") || status.equals("Player not found")
                || status.equals("user_not_found") || status.equals("invalid_username");
    }

    static String actionErrorFor(String status) {
        if (status == null || NON_ERROR_STATUSES.contains(status)) return Lang.t("messenger.error_action");
        if (status.equals("group_too_many_groups")) return Lang.t("messenger.error_too_many_groups");
        if (status.equals("group_group_full")) return Lang.t("messenger.error_group_full");
        if (status.equals("group_not_friend")) return Lang.t("messenger.error_not_friend");
        if (status.equals("invalid_group_name")) return Lang.t("messenger.error_group_name");
        if (status.equals("group_slow_down") || status.equals("lookup_slow_down")) return Lang.t("messenger.error_slowdown");
        if (status.equals("group_forbidden") || status.equals("group_already_member")) {
            return Lang.t("messenger.error_group_forbidden");
        }
        if (status.contains("conversation_forbidden")) return Lang.t("messenger.error_friend_required");
        if (status.contains("friend_target_unavailable")) return Lang.t("messenger.error_friend_unavailable");
        if (status.contains("friend_request_slow_down")) return Lang.t("messenger.error_friend_slowdown");
        if (status.contains("too_many_pending_requests")) return Lang.t("messenger.error_too_many_requests");
        if (status.contains("blocked") || status.contains("forbidden")) return Lang.t("messenger.error_blocked_user");
        if (status.contains("not_found") || status.contains("not found") || status.equals("invalid_username")) {
            return Lang.t("messenger.error_user_not_found");
        }
        if (status.contains("already_friends") || status.contains("pending")) return Lang.t("messenger.error_already_friend");
        return Lang.t("messenger.error_action");
    }

    private String fitSingleLine(String text, float width, float size) {
        return fitSingleLine(text, width, size, "…");
    }

    private String fitSingleLine(String text, float width, float size, String ellipsis) {
        String value = text == null ? "" : text.replace('\r', ' ').replace('\n', ' ');
        if (renderedTextWidth(value, size) <= width) return value;
        while (value.length() > 1 && renderedTextWidth(value + ellipsis, size) > width) {
            value = value.substring(0, value.length() - 1);
        }
        return value + ellipsis;
    }

    private void drawMarqueeLabel(DrawContext context, String key, String text, float size,
                                  float textX, float textY, float right, float top, float height,
                                  int color, boolean bold, boolean hovered) {
        String value = text == null ? "" : text.replace('\r', ' ').replace('\n', ' ');
        SettingLabel label = marqueeLabels.computeIfAbsent(key, ignored -> new SettingLabel());
        label.updateHover(hovered);
        // SettingLabel intentionally uses the same bold MSDF atlas as module
        // labels, including the existing edge fade and hover marquee.
        label.renderMsdf(context.getMatrices(), value, size, textX, textY,
                right, top, height, applyGlobalAlpha(color));
    }

    private static float renderedTextWidth(String text, float size) {
        return MsdfFonts.medium().getWidth(text, size)
                + Math.max(0, text.length() - 1) * 0.025F * size;
    }

    private float animatedHover(String key, boolean hovered) {
        renderedHoverKeys.add(key);
        Animation animation = hoverAnimations.computeIfAbsent(key, ignored -> new DecelerateAnimation()
                .setMs(HOVER_MS).setValue(1).setDirectionAndFinish(Direction.BACKWARDS));
        animation.setDirection(open && hovered ? Direction.FORWARDS : Direction.BACKWARDS);
        if (open && hovered) CursorManager.requestHand();
        return animation.getOutputFloat();
    }

    /** A smooth on/off value for switches and checks; unlike hover it never changes the cursor. */
    private float animatedState(String key, boolean on) {
        Animation animation = stateAnimations.computeIfAbsent(key, ignored -> new DecelerateAnimation()
                .setMs(HOVER_MS).setValue(1).setDirectionAndFinish(on ? Direction.FORWARDS : Direction.BACKWARDS));
        animation.setDirection(on ? Direction.FORWARDS : Direction.BACKWARDS);
        if (stateAnimations.size() > 256) stateAnimations.clear();
        return animation.getOutputFloat();
    }

    private void drawButtonSurface(DrawContext context, float drawX, float drawY, float drawW, float drawH,
                                   int color, int border, float hover) {
        drawSurface(context, drawX, drawY, drawW, drawH,
                MenuStyle.mix(color, 0xFFFFFFFF, hover * 0.08F),
                MenuStyle.mix(border, 0xFFFFFFFF, hover * 0.12F));
    }

    private void drawChatCard(DrawContext context, float drawX, float drawY, float drawW, float drawH,
                              float hover) {
        drawChatCard(context, drawX, drawY, drawW, drawH, hover, 1.0F);
    }

    private void drawChatCard(DrawContext context, float drawX, float drawY, float drawW, float drawH,
                              float hover, float opacity) {
        // Match module-setting rows, including their translucent fill and soft outline.
        int color = MenuStyle.withAlpha(MenuStyle.mix(MenuStyle.settingSurface(false), 0xFFFFFFFF,
                hover * 0.08F), opacity * 0.78F);
        int border = MenuStyle.withAlpha(MenuStyle.mix(MenuStyle.settingOutline(false), 0xFFFFFFFF,
                hover * 0.12F), opacity * 0.74F);
        rectangle.render(ShapeProperties.create(context.getMatrices(), drawX, drawY, drawW, drawH)
                .round(PANEL_RADIUS).softness(1.12F).thickness(1.2F)
                .outlineColor(applyGlobalAlpha(border)).color(applyGlobalAlpha(color)).build());
    }

    private void drawPanel(DrawContext context, float panelX, float panelY, float panelW, float panelH, int color) {
        drawSurface(context, panelX, panelY, panelW, panelH, color, MenuStyle.BORDER);
    }

    private void drawSurface(DrawContext context, float drawX, float drawY, float drawW, float drawH, int color) {
        drawSurface(context, drawX, drawY, drawW, drawH, color, MenuStyle.BORDER);
    }

    private void drawSurface(DrawContext context, float drawX, float drawY, float drawW, float drawH,
                             int color, int border) {
        rectangle.render(ShapeProperties.create(context.getMatrices(), drawX, drawY, drawW, drawH)
                .round(PANEL_RADIUS).thickness(1.5F)
                .outlineColor(applyGlobalAlpha(border)).color(applyGlobalAlpha(color)).build());
    }

    private void drawLine(DrawContext context, float lineX, float lineY, float lineW, int color) {
        rectangle.render(ShapeProperties.create(context.getMatrices(), lineX, lineY, lineW, 0.8F)
                .color(applyGlobalAlpha(color)).build());
    }

    private void drawBadge(DrawContext context, float rowX, float rowY, float rowW, float rowH, String value) {
        float textSize = 4.4F;
        float textWidth = MsdfFonts.bold().getWidth(value, textSize);
        float badgeWidth = Math.max(10.0F, textWidth + 2.0F);
        float badgeX = rowX + rowW - badgeWidth - 3.0F;
        // The badge has no surface now; its invisible box still gives every
        // value (one digit or several) the same automatic X/Y centering.
        drawCentered(context, value, badgeX, rowY, badgeWidth, rowH,
                textSize, MenuStyle.TEXT_PRIMARY, true);
    }

    /** "Online · HolyWorld" when a known server is shared, cut to {@code maxWidth}. */
    private void drawStatus(DrawContext context, float statusX, float statusY, boolean online, String server, float maxWidth) {
        String text = Lang.t(online ? "messenger.online" : "messenger.offline");
        if (online && server != null) text = text + " · " + server;
        drawText(context, fitSingleLine(text, Math.max(10.0F, maxWidth), 4.8F, "..."), statusX, statusY,
                4.8F, online ? MenuStyle.ACCENT_GREEN : MenuStyle.TEXT_MUTED, false);
    }

    private void drawCenteredStatus(DrawContext context, float boxX, float boxY, float boxW, String userId) {
        boolean online = service.isUserOnline(userId);
        String server = serverName(userId);
        String text = Lang.t(online ? "messenger.online" : "messenger.offline");
        if (online && server != null) text = text + " · " + server;
        text = fitSingleLine(text, boxW, 4.8F, "...");
        float textW = MsdfFonts.medium().getWidth(text, 4.8F);
        drawText(context, text, boxX + (boxW - textW) / 2.0F, boxY, 4.8F,
                online ? MenuStyle.ACCENT_GREEN : MenuStyle.TEXT_MUTED, false);
    }

    /**
     * The head texture already carries its rounded corners, so an avatar is one textured quad
     * at exact float coordinates. A user who blocked the local player is shown anonymised.
     */
    private void renderAvatar(DrawContext context, float avatarX, float avatarY, float size,
                              String userId, String name) {
        Identifier head = service.isBlockedBy(userId) ? skins.anonymousHead() : skins.head(name);
        drawTexture(context, head, avatarX, avatarY, size, applyGlobalAlpha(0xFFFFFFFF));
    }

    /** One square textured quad at float coordinates, drawn above the pending batched rectangles. */
    private static void drawTexture(DrawContext context, Identifier texture, float drawX, float drawY,
                                    float size, int color) {
        BatchedRectangle.flushIfBatching();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(matrix, drawX, drawY, 0.0F).texture(0.0F, 0.0F).color(color);
        buffer.vertex(matrix, drawX, drawY + size, 0.0F).texture(0.0F, 1.0F).color(color);
        buffer.vertex(matrix, drawX + size, drawY + size, 0.0F).texture(1.0F, 1.0F).color(color);
        buffer.vertex(matrix, drawX + size, drawY, 0.0F).texture(1.0F, 0.0F).color(color);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.disableBlend();
    }

    private static float avatarRadius(float size) {
        return size * SkinAvatarService.HEAD_CORNER;
    }

    private void drawCentered(DrawContext context, String text, float boxX, float boxY, float boxW,
                              float boxH, float size, int color, boolean bold) {
        MsdfFont font = bold ? MsdfFonts.bold() : MsdfFonts.medium();
        drawText(context, text, MenuStyle.centerMsdfTextX(font, text, size, boxX, boxW),
                MenuStyle.centerMsdfTextY(size, boxY, boxH), size, color, bold);
    }

    private void drawText(DrawContext context, String text, float textX, float textY, float size,
                          int color, boolean bold) {
        MsdfRenderer.renderText(bold ? MsdfFonts.bold() : MsdfFonts.medium(), text, size,
                applyGlobalAlpha(color), context.getMatrices().peek().getPositionMatrix(), textX, textY, 0.0F);
    }

    private static boolean inside(double mouseX, double mouseY, float boxX, float boxY, float boxW, float boxH) {
        return mouseX >= boxX && mouseX <= boxX + boxW && mouseY >= boxY && mouseY <= boxY + boxH;
    }

    private static String clip(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, Math.max(1, max - 1)) + "…";
    }

    private static String formatTime(long timestamp) {
        return TIME.format(Instant.ofEpochMilli(timestamp));
    }
}
