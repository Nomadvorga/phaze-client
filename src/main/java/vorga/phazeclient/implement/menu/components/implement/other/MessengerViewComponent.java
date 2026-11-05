package vorga.phazeclient.implement.menu.components.implement.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import vorga.phazeclient.api.system.animation.Animation;
import vorga.phazeclient.api.system.animation.Direction;
import vorga.phazeclient.api.system.animation.implement.DecelerateAnimation;
import vorga.phazeclient.api.system.cursor.CursorManager;
import vorga.phazeclient.api.system.font.msdf.MsdfFont;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.MessengerService;
import vorga.phazeclient.base.util.SkinAvatarService;
import vorga.phazeclient.base.util.Lang;
import vorga.phazeclient.base.util.render.Render2DUtil;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.MenuLayout;
import vorga.phazeclient.implement.menu.components.AbstractComponent;
import vorga.phazeclient.implement.menu.components.implement.settings.AbstractSettingComponent;
import vorga.phazeclient.implement.menu.components.implement.settings.SettingLabel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Full-width global messenger page. Layout calculations intentionally live in
 * one place so every panel keeps the same gap, padding and vertical rhythm at
 * every menu size and theme.
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
    private static final float MESSAGE_ROW = 22.0F;
    private static final float COMPACT_FRIEND_NAME_OFFSET = (18.0F - (7.0F + 4.8F)) / 2.0F;
    private static final float COMPACT_FRIEND_STATUS_OFFSET = COMPACT_FRIEND_NAME_OFFSET + 7.0F;
    private static final float FRIEND_CARD_HEIGHT = AbstractSettingComponent.ROW_HEIGHT;
    private static final float FRIEND_ROW_STEP = FRIEND_CARD_HEIGHT + 5.0F;
    private static final float DIRECT_CARD_HEIGHT = FRIEND_CARD_HEIGHT;
    private static final float DIRECT_ROW_STEP = DIRECT_CARD_HEIGHT + 5.0F;
    private static final float CARD_AVATAR_SIZE = 17.5F;
    private static final float CARD_AVATAR_INSET = (FRIEND_CARD_HEIGHT - CARD_AVATAR_SIZE) / 2.0F;
    private static final float CARD_TEXT_INSET = CARD_AVATAR_INSET + CARD_AVATAR_SIZE + 6.0F;
    private static final float CARD_TEXT_LINE_STEP = 10.0F;
    private static final float FRIEND_CARD_NAME_OFFSET = (FRIEND_CARD_HEIGHT - (CARD_TEXT_LINE_STEP + 4.8F)) / 2.0F;
    private static final float DIRECT_CARD_NAME_OFFSET = (DIRECT_CARD_HEIGHT - (CARD_TEXT_LINE_STEP + 5.2F)) / 2.0F;
    private static final float REQUEST_CARD_HEIGHT = 43.0F;
    private static final float REQUEST_ROW_STEP = REQUEST_CARD_HEIGHT + 5.0F;
    private static final float CARD_RADIUS = 5.0F;
    private static final float DIRECT_HEADER_HEIGHT = 27.0F;
    private static final float DIRECT_BACK_WIDTH = 25.0F;
    private static final float DIRECT_BACK_ICON_SIZE = 10.0F;
    private static final float DIRECT_BACK_ICON_X_INSET = (DIRECT_BACK_WIDTH - DIRECT_BACK_ICON_SIZE) / 2.0F;
    private static final float DIRECT_BACK_ICON_Y_INSET = (DIRECT_HEADER_HEIGHT - DIRECT_BACK_ICON_SIZE) / 2.0F;
    private static final float INPUT_HEIGHT = 18.0F;
    private static final float INPUT_TEXT_SIZE = 7.2F;
    private static final int HOVER_MS = 170;
    private static final int MAX_INPUT_CODEPOINTS = 255;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault());

    private final MessengerService service = MessengerService.getInstance();
    private final SkinAvatarService skins = SkinAvatarService.getInstance();
    private final Animation pageAnimation = new DecelerateAnimation().setMs(180).setValue(1);
    private boolean open;
    private Section section = Section.GLOBAL;
    private String input = "";
    private int cursor;
    private int selectionAnchor;
    private boolean inputFocused;
    private boolean draggingSelection;
    private long nextSendAt;
    private double scroll;
    private volatile String selectedFriendId;
    private volatile String selectedProfileId;
    private volatile String selectedProfileUsername;
    private volatile String selectedConversationId;
    private volatile long conversationRequestGeneration;
    private volatile String pendingConversationId;
    private volatile boolean sendResultReady;
    private volatile boolean sendResultSuccess;
    private boolean sendPending;
    private String pendingText;
    private volatile String actionError;
    private long nextRequestsRefreshAt;
    private final Map<String, SettingLabel> marqueeLabels = new HashMap<>();
    private final Map<String, Animation> hoverAnimations = new HashMap<>();
    private final Set<String> renderedHoverKeys = new HashSet<>();

    public MessengerViewComponent() {
        pageAnimation.setDirectionAndFinish(Direction.BACKWARDS);
    }

    private enum Section {
        GLOBAL("messenger.global"),
        FRIENDS("messenger.friends"),
        DIRECT("messenger.direct"),
        REQUESTS("messenger.requests"),
        BLOCKED("messenger.blocked");

        private final String label;

        Section(String label) {
            this.label = label;
        }
    }

    public void open() {
        if (!open) hoverAnimations.clear();
        open = true;
        pageAnimation.setDirection(Direction.FORWARDS);
        service.start();
        service.refreshRequestsAsync();
    }

    public void close() {
        open = false;
        pageAnimation.setDirection(Direction.BACKWARDS);
        input = "";
        cursor = 0;
        selectionAnchor = 0;
        inputFocused = false;
        sendPending = false;
        pendingText = null;
        sendResultReady = false;
        pendingConversationId = null;
        conversationRequestGeneration++;
    }

    public boolean isOpen() {
        return open || pageAnimation.getOutputFloat() > 0.002F;
    }

    public boolean shouldRender() {
        return open || pageAnimation.getOutputFloat() > 0.002F;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!shouldRender()) return;
        float inheritedAlpha = globalAlpha;
        globalAlpha *= pageAnimation.getOutputFloat();
        applyAsyncState();
        if (section == Section.REQUESTS) {
            long now = System.currentTimeMillis();
            if (now >= nextRequestsRefreshAt) {
                nextRequestsRefreshAt = now + 4_000L;
                service.refreshRequestsAsync();
            }
        }

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

        renderCenter(context, centerX + PANEL_PAD, pageY + PANEL_PAD,
                centerW - PANEL_PAD * 2.0F, pageH - PANEL_PAD * 2.0F, mouseX, mouseY);
        renderDetails(context, rightX + PANEL_PAD, pageY + PANEL_PAD,
                RIGHT_WIDTH - PANEL_PAD * 2.0F, pageH - PANEL_PAD * 2.0F, mouseX, mouseY);
        } finally {
            hoverAnimations.keySet().retainAll(renderedHoverKeys);
            globalAlpha = inheritedAlpha;
        }
    }

    private void renderProfile(DrawContext context, float panelX, float panelY, float panelW,
                               int mouseX, int mouseY) {
        drawSurface(context, panelX, panelY, panelW, PROFILE_HEIGHT, MenuStyle.CARD_INNER);
        String username = service.getCurrentUser() == null
                ? "Player" : service.getCurrentUser().username();
        renderAvatar(context, panelX + 6.0F, panelY + 8.0F, 25.0F, username, MenuStyle.CHIP_ACTIVE);
        drawMarqueeLabel(context, "profile", username, 6.2F, panelX + 36.0F, panelY + 9.0F,
                panelX + panelW - 2.0F, panelY + 5.0F, 12.0F,
                MenuStyle.TEXT_PRIMARY, true,
                inside(mouseX, mouseY, panelX + 34.0F, panelY + 4.0F, panelW - 34.0F, 16.0F));
        drawStatus(context, panelX + 36.0F, panelY + 18.0F, service.getStatus().equals("Online"));
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
            if (item == Section.FRIENDS && !service.getFriends().isEmpty()) {
                drawBadge(context, navX, rowY, navW, NAV_HEIGHT, String.valueOf(service.getFriends().size()));
            } else if (item == Section.DIRECT && !service.getConversations().isEmpty()) {
                drawBadge(context, navX, rowY, navW, NAV_HEIGHT, String.valueOf(service.getConversations().size()));
            } else if (item == Section.REQUESTS && !service.getRequests().isEmpty()) {
                drawBadge(context, navX, rowY, navW, NAV_HEIGHT, String.valueOf(service.getRequests().size()));
            }
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
        renderAvatar(context, rowX, rowY, 15.0F, friend.username(), MenuStyle.CHIP_ACTIVE);
        drawMarqueeLabel(context, "compact:" + friend.id(), friend.username(), 5.0F,
                rowX + 20.0F, rowY + COMPACT_FRIEND_NAME_OFFSET, rowX + rowW, rowY, 12.0F,
                MenuStyle.TEXT_PRIMARY, false,
                inside(mouseX, mouseY, rowX + 18.0F, rowY, rowW - 18.0F, 18.0F));
        drawStatus(context, rowX + 20.0F, rowY + COMPACT_FRIEND_STATUS_OFFSET, service.isUserOnline(friend.id()));
    }

    private void renderCenter(DrawContext context, float contentX, float contentY, float contentW,
                              float contentH, int mouseX, int mouseY) {
        MessengerService.Conversation conversation = selectedConversation();
        boolean directConversationOpen = section == Section.DIRECT && conversation != null;
        if (directConversationOpen) {
            renderConversationHeader(context, conversation, contentX, contentY, contentW, mouseX, mouseY);
        } else {
            String title = section == Section.GLOBAL ? Lang.t("messenger.title") : Lang.t(section.label);
            drawText(context, title, contentX + HEADER_TEXT_INSET, contentY + 2.0F, 8.2F, MenuStyle.TEXT_PRIMARY, true);
        }
        if (section == Section.GLOBAL) {
            String online = service.getOnlineCount() < 0 ? "—" : service.getOnlineCount() + " online";
            float onlineWidth = MsdfFonts.medium().getWidth(online, 5.0F);
            drawText(context, online, headerRightTextX(contentX, contentW, onlineWidth), contentY + 4.0F,
                    5.0F, MenuStyle.TEXT_MUTED, false);
        }
        if (!directConversationOpen) {
            drawLine(context, contentX, contentY + 21.0F, contentW, MenuStyle.BORDER);
        }

        float inputY = contentY + contentH - INPUT_HEIGHT;
        float listTop = contentY + (directConversationOpen ? DIRECT_HEADER_HEIGHT + 5.0F : 27.0F);
        float listBottom = listBottom(contentY, contentH, directConversationOpen || section == Section.GLOBAL);
        context.enableScissor((int) contentX, (int) listTop, (int) (contentX + contentW), (int) listBottom);
        if (section == Section.GLOBAL) {
            renderGlobalMessages(context, contentX, listTop, contentW, listBottom, mouseX, mouseY);
        } else if (directConversationOpen) {
            renderDirectMessages(context, contentX, listTop, contentW, listBottom, mouseX, mouseY);
        } else {
            renderSectionRows(context, contentX, listTop, contentW, listBottom, mouseX, mouseY);
        }
        context.disableScissor();

        if (section == Section.GLOBAL || directConversationOpen) {
            renderInput(context, contentX, inputY, contentW, mouseX, mouseY);
        }
    }

    private void renderConversationHeader(DrawContext context, MessengerService.Conversation conversation,
                                          float contentX, float contentY, float contentW, int mouseX, int mouseY) {
        drawSurface(context, contentX, contentY, contentW, DIRECT_HEADER_HEIGHT,
                MenuStyle.CARD_OPTIONS, MenuStyle.BORDER);
        float hover = animatedHover("conversation:back",
                inside(mouseX, mouseY, contentX, contentY, DIRECT_BACK_WIDTH, DIRECT_HEADER_HEIGHT));
        if (hover > 0.0F) {
            drawChatCard(context, contentX + 3.0F, contentY + 3.0F, DIRECT_BACK_WIDTH - 6.0F,
                    DIRECT_HEADER_HEIGHT - 6.0F, hover, hover);
        }
        float backIconX = contentX + DIRECT_BACK_ICON_X_INSET;
        float backIconY = contentY + DIRECT_BACK_ICON_Y_INSET;
        // Image shifts X by its width; compensate just as the module-settings arrow does.
        image.setTexture("textures/back_arrow.png")
                .render(ShapeProperties.create(context.getMatrices(),
                                backIconX - DIRECT_BACK_ICON_SIZE, backIconY,
                                DIRECT_BACK_ICON_SIZE, DIRECT_BACK_ICON_SIZE)
                        .rotation(0)
                        .color(applyGlobalAlpha(MenuStyle.mix(MenuStyle.TEXT_PRIMARY, 0xFFFFFFFF, hover * 0.18F)))
                        .build());
        renderAvatar(context, contentX + 28.0F, contentY + 3.0F, 21.0F,
                conversation.username(), MenuStyle.CHIP_ACTIVE);
        drawMarqueeLabel(context, "conversation:" + conversation.id(), conversation.username(), 6.0F,
                contentX + 54.0F, contentY + 3.0F, contentX + contentW - 6.0F,
                contentY + 2.0F, 12.0F, MenuStyle.TEXT_PRIMARY, true, false);
        drawStatus(context, contentX + 54.0F, contentY + 12.0F,
                service.isUserOnline(conversation.participantId()));
    }

    private void renderGlobalMessages(DrawContext context, float contentX, float listTop,
                                      float contentW, float listBottom, int mouseX, int mouseY) {
        List<MessengerService.Message> list = service.getGlobalMessages();
        if (list.isEmpty()) {
            drawText(context, Lang.t("messenger.empty_global"), contentX + 7.0F,
                    listTop + 15.0F, 5.8F, MenuStyle.TEXT_MUTED, false);
            return;
        }
        renderMessageList(context, list, contentX, listTop, contentW, listBottom, mouseX, mouseY);
    }

    private void renderDirectMessages(DrawContext context, float contentX, float listTop,
                                      float contentW, float listBottom, int mouseX, int mouseY) {
        List<MessengerService.Message> list = selectedConversationId == null
                ? List.of() : service.getMessages(selectedConversationId);
        if (list.isEmpty()) {
            drawText(context, Lang.t("messenger.empty_direct"), contentX + 7.0F,
                    listTop + 15.0F, 5.8F, MenuStyle.TEXT_MUTED, false);
            return;
        }
        renderMessageList(context, list, contentX, listTop, contentW, listBottom, mouseX, mouseY);
    }

    private void renderMessageList(DrawContext context, List<MessengerService.Message> list,
                                   float contentX, float listTop, float contentW, float listBottom,
                                   int mouseX, int mouseY) {
        float available = listBottom - listTop;
        float total = messageListHeight(list);
        double maxUp = Math.max(0.0D, total - available);
        scroll = Math.max(-maxUp, Math.min(0.0D, scroll));
        float rowY = listBottom - total + (float) scroll;
        for (MessengerService.Message message : list) {
            if (rowY + MESSAGE_ROW >= listTop && rowY <= listBottom) {
                renderMessage(context, message, contentX + 3.0F, rowY, contentW - 6.0F, mouseX, mouseY);
            }
            rowY += MESSAGE_ROW;
        }
    }

    private float messageListHeight(List<MessengerService.Message> list) {
        return list.size() * MESSAGE_ROW;
    }

    private void renderSectionRows(DrawContext context, float contentX, float listTop, float contentW,
                                   float listBottom, int mouseX, int mouseY) {
        float rowY = listTop;
        if (section == Section.FRIENDS) {
            for (MessengerService.Friend friend : service.getFriends()) {
                if (rowY + FRIEND_CARD_HEIGHT > listBottom) break;
                renderFriendCard(context, friend, contentX, rowY, contentW, mouseX, mouseY);
                rowY += FRIEND_ROW_STEP;
            }
        } else if (section == Section.DIRECT) {
            for (MessengerService.Conversation conversation : service.getConversations()) {
                if (rowY + DIRECT_CARD_HEIGHT > listBottom) break;
                renderConversationCard(context, conversation, contentX, rowY, contentW, mouseX, mouseY);
                rowY += DIRECT_ROW_STEP;
            }
            if (service.getConversations().isEmpty()) {
                drawText(context, Lang.t("messenger.no_conversations"), contentX + 7.0F,
                        listTop + 15.0F, 5.8F, MenuStyle.TEXT_MUTED, false);
            }
        } else if (section == Section.REQUESTS) {
            for (MessengerService.FriendRequest request : service.getRequests()) {
                if (rowY + REQUEST_CARD_HEIGHT > listBottom) break;
                renderFriendRequestCard(context, request, contentX, rowY, contentW, mouseX, mouseY);
                rowY += REQUEST_ROW_STEP;
            }
        } else {
            for (MessengerService.Friend user : service.getBlocked()) {
                if (rowY + 25.0F > listBottom) break;
                renderSelectableUser(context, user.id(), user.username(), contentX, rowY, contentW,
                        mouseX, mouseY);
                rowY += 29.0F;
            }
        }
    }

    private void renderFriendCard(DrawContext context, MessengerService.Friend friend,
                                  float rowX, float rowY, float rowW, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, rowX, rowY, rowW, FRIEND_CARD_HEIGHT);
        float hover = animatedHover("friend-card:" + friend.id(), hovered);
        drawChatCard(context, rowX, rowY, rowW, FRIEND_CARD_HEIGHT, hover);
        renderAvatar(context, rowX + CARD_AVATAR_INSET, rowY + CARD_AVATAR_INSET, CARD_AVATAR_SIZE,
                friend.username(), MenuStyle.CHIP_ACTIVE);
        float textX = rowX + CARD_TEXT_INSET;
        float nameY = rowY + FRIEND_CARD_NAME_OFFSET;
        drawMarqueeLabel(context, "friend-card:" + friend.id(), friend.username(), 6.0F,
                textX, nameY, rowX + rowW - 6.0F,
                rowY, FRIEND_CARD_HEIGHT, MenuStyle.TEXT_PRIMARY, true, hovered);
        drawStatus(context, textX, nameY + CARD_TEXT_LINE_STEP,
                service.isUserOnline(friend.id()));
    }

    private void renderConversationCard(DrawContext context, MessengerService.Conversation conversation,
                                        float rowX, float rowY, float rowW, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, rowX, rowY, rowW, DIRECT_CARD_HEIGHT);
        float hover = animatedHover("conversation-card:" + conversation.id(), hovered);
        drawChatCard(context, rowX, rowY, rowW, DIRECT_CARD_HEIGHT, hover);
        renderAvatar(context, rowX + CARD_AVATAR_INSET, rowY + CARD_AVATAR_INSET, CARD_AVATAR_SIZE,
                conversation.username(), MenuStyle.CHIP_ACTIVE);
        float textX = rowX + CARD_TEXT_INSET;
        float nameY = rowY + DIRECT_CARD_NAME_OFFSET;

        String time = conversation.lastCreatedAt() > 0L
                ? formatTime(conversation.lastCreatedAt()) : "";
        float timeWidth = time.isEmpty() ? 0.0F : renderedTextWidth(time, 4.8F);
        float right = rowX + rowW - 7.0F - timeWidth;
        drawMarqueeLabel(context, "conversation-card:" + conversation.id(), conversation.username(), 6.0F,
                textX, nameY, right - 5.0F,
                rowY, DIRECT_CARD_HEIGHT, MenuStyle.TEXT_PRIMARY, true, hovered);
        if (!time.isEmpty()) {
            drawText(context, time, right, nameY + 0.6F, 4.8F, MenuStyle.TEXT_MUTED, false);
        }
        String preview = conversation.lastText() == null || conversation.lastText().isBlank()
                ? Lang.t("messenger.message_placeholder") : conversation.lastText();
        drawText(context, fitSingleLine(preview, Math.max(24.0F, right - textX), 5.2F, "..."),
                textX, nameY + CARD_TEXT_LINE_STEP, 5.2F, MenuStyle.TEXT_MUTED, false);
    }

    private void renderFriendRequestCard(DrawContext context, MessengerService.FriendRequest request,
                                          float rowX, float rowY, float rowW, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, rowX, rowY, rowW, REQUEST_CARD_HEIGHT);
        float hover = animatedHover("request:" + request.id(), hovered);
        drawChatCard(context, rowX, rowY, rowW, REQUEST_CARD_HEIGHT, hover);
        renderAvatar(context, rowX + 6.0F, rowY + 6.0F, 20.0F,
                request.requesterUsername(), MenuStyle.CHIP_ACTIVE);
        drawMarqueeLabel(context, "request:" + request.id(), request.requesterUsername(), 5.8F,
                rowX + 32.0F, rowY + 8.0F, rowX + rowW - 6.0F,
                rowY + 5.0F, 12.0F, MenuStyle.TEXT_PRIMARY, true, hovered);

        float actionY = rowY + 26.0F;
        float actionX = rowX + 6.0F;
        float actionW = (rowW - 16.0F) / 2.0F;
        renderCompactActionButton(context, "request:accept:" + request.id(), Lang.t("messenger.accept"), actionX, actionY, actionW,
                mouseX, mouseY, MenuStyle.ACCENT_GREEN);
        renderCompactActionButton(context, "request:decline:" + request.id(), Lang.t("messenger.decline"), actionX + actionW + 4.0F,
                actionY, actionW, mouseX, mouseY, 0xFFE76868);
    }

    private void renderSelectableUser(DrawContext context, String id, String username, float rowX,
                                      float rowY, float rowW, int mouseX, int mouseY) {
        boolean selected = id.equals(selectedFriendId) || id.equals(selectedConversationId);
        boolean hovered = inside(mouseX, mouseY, rowX, rowY, rowW, 25.0F);
        float hover = animatedHover("user:" + id, hovered);
        drawButtonSurface(context, rowX, rowY, rowW, 25.0F,
                selected ? MenuStyle.PANEL_ROW_ACTIVE : MenuStyle.mix(MenuStyle.PANEL_ROW, MenuStyle.CARD_INNER,
                        0.10F), selected ? MenuStyle.CHIP_ACTIVE : MenuStyle.BORDER, hover);
        renderAvatar(context, rowX + 5.0F, rowY + 5.0F, 15.0F, username, MenuStyle.CHIP_ACTIVE);
        drawMarqueeLabel(context, "user:" + id, username, 5.8F, rowX + 26.0F, rowY + 5.0F,
                rowX + rowW - (section == Section.BLOCKED ? 56.0F : 6.0F), rowY + 2.0F,
                19.0F, MenuStyle.TEXT_PRIMARY, false, hovered);
        if (section == Section.BLOCKED) {
            float unblockHover = animatedHover("unblock:" + id,
                    inside(mouseX, mouseY, rowX + rowW - 56.0F, rowY, 50.0F, 25.0F));
            drawButtonSurface(context, rowX + rowW - 50.0F, rowY + 4.0F, 44.0F, 17.0F,
                    MenuStyle.CARD_INNER, MenuStyle.BORDER, unblockHover);
            drawCentered(context, Lang.t("messenger.unblock"), rowX + rowW - 50.0F, rowY + 4.0F,
                    44.0F, 17.0F, 4.2F,
                    MenuStyle.mix(MenuStyle.TEXT_MUTED, MenuStyle.TEXT_PRIMARY, unblockHover * 0.45F), true);
        }
    }

    private void renderMessage(DrawContext context, MessengerService.Message message, float rowX,
                               float rowY, float rowW, int mouseX, int mouseY) {
        float avatarHover = animatedHover("message-avatar:" + message.id(),
                inside(mouseX, mouseY, rowX, rowY + 1.0F, 17.0F, 17.0F));
        renderAvatar(context, rowX, rowY + 1.0F, 17.0F, message.senderUsername(), MenuStyle.CHIP_ACTIVE);
        if (avatarHover > 0.0F) {
            rectangle.render(ShapeProperties.create(context.getMatrices(), rowX, rowY + 1.0F, 17.0F, 17.0F)
                    .round(2.0F)
                    .color(applyGlobalAlpha(MenuStyle.withAlpha(0xFFFFFFFF, avatarHover * 0.08F))).build());
        }
        int senderColor = message.senderId().equals(service.getCurrentUser() == null
                ? "" : service.getCurrentUser().id()) ? MenuStyle.CHIP_ACTIVE : MenuStyle.TEXT_PRIMARY;
        drawMarqueeLabel(context, "sender:" + message.senderId(), message.senderUsername(), 6.2F,
                rowX + 22.0F, rowY + 1.0F, rowX + rowW - 30.0F,
                rowY, 10.0F, senderColor, true,
                inside(mouseX, mouseY, rowX + 22.0F, rowY, Math.max(1.0F, rowW - 52.0F), MESSAGE_ROW));
        drawText(context, formatTime(message.createdAt()), rowX + rowW - 23.0F, rowY + 2.0F,
                5.0F, MenuStyle.TEXT_MUTED, false);
        String text = fitSingleLine(message.text(), Math.max(30.0F, rowW - 52.0F), 6.0F);
        drawText(context, text, rowX + 22.0F, rowY + 11.0F, 6.0F, MenuStyle.TEXT_PRIMARY, false);
    }

    private void renderInput(DrawContext context, float inputX, float inputY, float inputW, int mouseX, int mouseY) {
        float sendW = 42.0F;
        float boxW = inputW - sendW - GAP;
        drawSurface(context, inputX, inputY, boxW, INPUT_HEIGHT, MenuStyle.CARD_INNER, MenuStyle.BORDER);
        String value = input.isEmpty()
                ? Lang.t(section == Section.DIRECT ? "messenger.message_placeholder" : "messenger.input")
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
            drawText(context, error, inputX + 2.0F, inputY - 8.0F, 4.8F, 0xFFE76868, false);
        }
        float sendX = inputX + boxW + GAP;
        long remaining = Math.max(0L, nextSendAt - System.currentTimeMillis());
        boolean enabled = !input.isEmpty() && !sendPending && remaining == 0L;
        float hover = animatedHover("input:send", enabled && inside(mouseX, mouseY, sendX, inputY, sendW, INPUT_HEIGHT));
        drawButtonSurface(context, sendX, inputY, sendW, INPUT_HEIGHT,
                enabled ? MenuStyle.CHIP_ACTIVE : MenuStyle.PANEL_ROW, MenuStyle.BORDER_LIGHT, hover);
        drawCentered(context, remaining > 0L ? String.format("%.1f", remaining / 1000.0D) : (sendPending ? "..." : Lang.t("messenger.send")), sendX, inputY, sendW, INPUT_HEIGHT,
                5.8F, enabled ? MenuStyle.TEXT_PRIMARY : MenuStyle.TEXT_MUTED, true);
    }

    private void renderDetails(DrawContext context, float panelX, float panelY, float panelW,
                               float panelH, int mouseX, int mouseY) {
        MessengerService.Friend selected = selectedProfileFriend();
        if (selected == null) {
            drawCentered(context, Lang.t("messenger.no_selected"), panelX, panelY + panelH / 2.0F - 8.0F,
                    panelW, 16.0F, 5.8F, MenuStyle.TEXT_MUTED, false);
            return;
        }
        renderAvatar(context, panelX + (panelW - 34.0F) / 2.0F, panelY + 10.0F,
                34.0F, selected.username(), MenuStyle.CHIP_ACTIVE);
        drawCentered(context, clip(selected.username(), 16), panelX, panelY + 49.0F,
                panelW, 12.0F, 6.4F, MenuStyle.TEXT_PRIMARY, true);
        drawCenteredStatus(context, panelX, panelY + 61.0F, panelW, service.isUserOnline(selected.id()));

        float buttonY = panelY + 82.0F;
        boolean blockedUser = isBlocked(selected.id());
        boolean friend = isFriend(selected.id());
        renderActionButton(context, "details:message", blockedUser ? Lang.t("messenger.unblock") : Lang.t("messenger.message"), panelX, buttonY, panelW, mouseX, mouseY,
                 selectedFriendId != null && selectedFriendId.equals(selected.id()), false);
        if (!blockedUser) {
            renderActionButton(context, "details:friend", friend ? Lang.t("messenger.remove_friend") : Lang.t("messenger.add_friend"),
                    panelX, buttonY + 22.0F, panelW, mouseX, mouseY, false, friend);
            renderActionButton(context, "details:block", Lang.t("messenger.block"), panelX, buttonY + 44.0F, panelW, mouseX, mouseY, false, false);
        }
        if (actionError != null) {
            drawText(context, actionError, panelX, buttonY + 68.0F, 3.8F, 0xFFE76868, false);
        }
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
        drawCentered(context, label, buttonX, buttonY, buttonW, 18.0F, 5.2F,
                MenuStyle.mix(text, MenuStyle.TEXT_PRIMARY, hover * 0.45F), true);
    }

    private void renderCompactActionButton(DrawContext context, String key, String label, float buttonX, float buttonY,
                                            float buttonW, int mouseX, int mouseY, int accent) {
        boolean hovered = inside(mouseX, mouseY, buttonX, buttonY, buttonW, 13.0F);
        float hover = animatedHover(key, hovered);
        int fill = MenuStyle.mix(MenuStyle.CARD_INNER, accent, 0.18F);
        drawButtonSurface(context, buttonX, buttonY, buttonW, 13.0F, fill,
                MenuStyle.mix(MenuStyle.BORDER, accent, 0.45F), hover);
        drawCentered(context, label, buttonX, buttonY, buttonW, 13.0F, 4.1F,
                MenuStyle.TEXT_PRIMARY, true);
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
        MessengerService.Conversation conversation = selectedConversation();
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
        for (MessengerService.Friend blocked : service.getBlocked()) {
            if (blocked.id().equals(userId)) return true;
        }
        return false;
    }

    private boolean isFriend(String userId) {
        for (MessengerService.Friend friend : service.getFriends()) {
            if (friend.id().equals(userId)) return true;
        }
        return false;
    }

    private void selectProfile(String id, String username) {
        selectedProfileId = id;
        selectedProfileUsername = username;
        selectedFriendId = id;
    }

    private MessengerService.Message messageAtAvatar(double mouseX, double mouseY,
                                                     float contentX, float listTop,
                                                     float contentW, float listBottom) {
        List<MessengerService.Message> list = section == Section.GLOBAL
                ? service.getGlobalMessages() : service.getMessages(selectedConversationId);
        float total = messageListHeight(list);
        float available = listBottom - listTop;
        double maxUp = Math.max(0.0D, total - available);
        double effectiveScroll = Math.max(-maxUp, Math.min(0.0D, scroll));
        float rowY = listBottom - total + (float) effectiveScroll;
        float rowX = contentX + 3.0F;
        for (MessengerService.Message message : list) {
            if (inside(mouseX, mouseY, rowX, rowY + 1.0F, 17.0F, 17.0F)) return message;
            rowY += MESSAGE_ROW;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!open || button != 0) return false;
        float pageX = x + SIDE;
        float pageY = y + TOP;
        float pageW = width - SIDE * 2.0F;
        float pageH = height - TOP - SIDE;
        float leftX = pageX + PANEL_PAD;
        float navY = pageY + NAV_TOP_OFFSET;
        float navW = LEFT_WIDTH - PANEL_PAD * 2.0F;
        for (Section item : Section.values()) {
            float rowY = navY + item.ordinal() * (NAV_HEIGHT + NAV_GAP);
            if (inside(mouseX, mouseY, leftX, rowY, navW, NAV_HEIGHT)) {
                section = item;
                scroll = 0.0D;
                inputFocused = false;
                input = "";
                cursor = selectionAnchor = 0;
                if (item == Section.DIRECT) {
                    conversationRequestGeneration++;
                    pendingConversationId = null;
                    selectedConversationId = null;
                    selectedFriendId = null;
                    selectedProfileId = null;
                    selectedProfileUsername = null;
                }
                if (item == Section.REQUESTS) {
                    service.refreshRequestsAsync();
                    nextRequestsRefreshAt = System.currentTimeMillis() + 4_000L;
                }
                return true;
            }
        }

        float previewY = pageY + FRIEND_PREVIEW_TOP_OFFSET + 14.0F;
        List<MessengerService.Friend> previewFriends = service.getFriends();
        for (int index = 0; index < Math.min(5, previewFriends.size()); index++) {
                if (inside(mouseX, mouseY, leftX, previewY + index * 20.0F,
                    LEFT_WIDTH - PANEL_PAD * 2.0F, 18.0F)) {
                selectedFriendId = previewFriends.get(index).id();
                selectProfile(previewFriends.get(index).id(), previewFriends.get(index).username());
                return true;
            }
        }

        float centerX = pageX + LEFT_WIDTH + GAP;
        float rightX = pageX + pageW - RIGHT_WIDTH;
        float centerW = rightX - GAP - centerX;
        float contentX = centerX + PANEL_PAD;
        float contentY = pageY + PANEL_PAD;
        float contentW = centerW - PANEL_PAD * 2.0F;
        float contentH = pageH - PANEL_PAD * 2.0F;
        float inputY = contentY + contentH - INPUT_HEIGHT;
        float inputX = contentX;
        float inputW = contentW - 42.0F - GAP;
        boolean directConversationOpen = section == Section.DIRECT && selectedConversation() != null;
        if (directConversationOpen && inside(mouseX, mouseY, contentX, contentY, DIRECT_BACK_WIDTH, DIRECT_HEADER_HEIGHT)) {
            conversationRequestGeneration++;
            selectedConversationId = null;
            selectedFriendId = null;
            selectedProfileId = null;
            selectedProfileUsername = null;
            pendingConversationId = null;
            input = "";
            cursor = selectionAnchor = 0;
            scroll = 0.0D;
            actionError = null;
            return true;
        }
        if ((section == Section.GLOBAL || directConversationOpen)
                && inside(mouseX, mouseY, contentX + contentW - 42.0F, inputY, 42.0F, INPUT_HEIGHT)) {
            submit();
            return true;
        }
        if ((section == Section.GLOBAL || directConversationOpen)
                && inside(mouseX, mouseY, inputX, inputY, inputW, INPUT_HEIGHT)) {
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

        if (section == Section.GLOBAL || directConversationOpen) {
            MessengerService.Message message = messageAtAvatar(mouseX, mouseY, contentX,
                    contentY + (section == Section.DIRECT ? DIRECT_HEADER_HEIGHT + 5.0F : 27.0F),
                    contentW, inputY - 5.0F);
            if (message != null) {
                selectProfile(message.senderId(), message.senderUsername());
                return true;
            }
        }

        if (directConversationOpen && inside(mouseX, mouseY, contentX,
                contentY + DIRECT_HEADER_HEIGHT + 5.0F, contentW,
                inputY - contentY - DIRECT_HEADER_HEIGHT - 10.0F)) {
            return true;
        }

        MessengerService.Friend detailsFriend = selectedProfileFriend();
        if (detailsFriend != null) {
            float detailsX = pageX + pageW - RIGHT_WIDTH + PANEL_PAD;
            float detailsY = pageY + PANEL_PAD + 82.0F;
            float detailsW = RIGHT_WIDTH - PANEL_PAD * 2.0F;
            if (inside(mouseX, mouseY, detailsX, detailsY, detailsW, 18.0F)) {
                if (isBlocked(detailsFriend.id())) {
                    service.unblockUser(detailsFriend.id(), success -> actionError = success ? null : actionErrorFor(service.getStatus()));
                } else {
                    selectedFriendId = detailsFriend.id();
                    long requestGeneration = ++conversationRequestGeneration;
                    service.openConversation(detailsFriend.id(), id -> {
                        if (requestGeneration != conversationRequestGeneration) return;
                        if (id == null) actionError = actionErrorFor(service.getStatus());
                        else actionError = null;
                        pendingConversationId = id;
                    });
                }
                return true;
            }
            if (inside(mouseX, mouseY, detailsX, detailsY + 22.0F, detailsW, 18.0F)) {
                if (isBlocked(detailsFriend.id())) return true;
                if (isFriend(detailsFriend.id())) {
                    service.removeFriend(detailsFriend.id(), success -> {
                        if (success) {
                            selectedFriendId = null;
                            selectedProfileId = null;
                            selectedProfileUsername = null;
                            actionError = null;
                        } else {
                            actionError = actionErrorFor(service.getStatus());
                        }
                    });
                } else {
                    service.addFriendByUsername(detailsFriend.username(), success -> actionError = success ? null : actionErrorFor(service.getStatus()));
                }
                return true;
            }
            if (inside(mouseX, mouseY, detailsX, detailsY + 44.0F, detailsW, 18.0F)) {
                if (isBlocked(detailsFriend.id())) return true;
                service.blockUser(detailsFriend.id());
                selectedFriendId = null;
                selectedProfileId = null;
                selectedProfileUsername = null;
                return true;
            }
        }

        float listTop = contentY + (directConversationOpen ? DIRECT_HEADER_HEIGHT + 5.0F : 27.0F);
        float listBottom = listBottom(contentY, contentH, section == Section.GLOBAL || directConversationOpen);
        if (section == Section.FRIENDS) {
            List<MessengerService.Friend> currentFriends = service.getFriends();
            int index = cardIndex(mouseX, mouseY, contentX, listTop, contentW,
                    FRIEND_CARD_HEIGHT, FRIEND_ROW_STEP, listBottom, currentFriends.size());
            if (index >= 0) {
                MessengerService.Friend friend = currentFriends.get(index);
                selectProfile(friend.id(), friend.username());
                return true;
            }
        } else if (section == Section.DIRECT && !directConversationOpen) {
            List<MessengerService.Conversation> currentConversations = service.getConversations();
            int index = cardIndex(mouseX, mouseY, contentX, listTop, contentW,
                    DIRECT_CARD_HEIGHT, DIRECT_ROW_STEP, listBottom, currentConversations.size());
            if (index >= 0) {
                MessengerService.Conversation conversation = currentConversations.get(index);
                selectedConversationId = conversation.id();
                selectedFriendId = conversation.participantId();
                selectProfile(conversation.participantId(), conversation.username());
                inputFocused = false;
                input = "";
                cursor = selectionAnchor = 0;
                service.selectConversation(selectedConversationId);
                return true;
            }
        } else if (section == Section.REQUESTS) {
            List<MessengerService.FriendRequest> currentRequests = service.getRequests();
            int index = cardIndex(mouseX, mouseY, contentX, listTop, contentW,
                    REQUEST_CARD_HEIGHT, REQUEST_ROW_STEP, listBottom, currentRequests.size());
            if (index >= 0) {
                float rowY = listTop + index * REQUEST_ROW_STEP;
                float actionY = rowY + 26.0F;
                float actionX = contentX + 6.0F;
                float actionW = (contentW - 16.0F) / 2.0F;
                long requestId = currentRequests.get(index).id();
                if (inside(mouseX, mouseY, actionX, actionY, actionW, 13.0F)) {
                    service.acceptFriendRequest(requestId);
                } else if (inside(mouseX, mouseY, actionX + actionW + 4.0F, actionY, actionW, 13.0F)) {
                    service.declineFriendRequest(requestId, success -> {
                        if (!success) actionError = actionErrorFor(service.getStatus());
                    });
                }
                return true;
            }
        } else if (section == Section.BLOCKED) {
            List<MessengerService.Friend> currentBlocked = service.getBlocked();
            int index = cardIndex(mouseX, mouseY, contentX, listTop, contentW,
                    25.0F, 29.0F, listBottom, currentBlocked.size());
            if (index >= 0) {
                MessengerService.Friend blocked = currentBlocked.get(index);
                if (inside(mouseX, mouseY, contentX + contentW - 56.0F, listTop + index * 29.0F,
                        50.0F, 25.0F)) {
                    service.unblockUser(blocked.id(), success -> actionError = success ? null : actionErrorFor(service.getStatus()));
                } else {
                    selectProfile(blocked.id(), blocked.username());
                }
                return true;
            }
        }

        if (inside(mouseX, mouseY, centerX, pageY, centerW, pageH)) return true;
        return inside(mouseX, mouseY, pageX, pageY, pageW, pageH);
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
        if (!open || !(section == Section.GLOBAL
                || (section == Section.DIRECT && selectedConversation() != null))) return false;
        scroll += amount * MESSAGE_ROW;
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!open || !draggingSelection || button != 0) return false;
        float pageX = x + SIDE;
        float pageY = y + TOP;
        float pageW = width - SIDE * 2.0F;
        float centerX = pageX + LEFT_WIDTH + GAP;
        float rightX = pageX + pageW - RIGHT_WIDTH;
        float contentX = centerX + PANEL_PAD;
        float contentW = rightX - GAP - centerX - PANEL_PAD * 2.0F;
        cursor = cursorFromMouse(mouseX, contentX, contentW - 42.0F - GAP);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) draggingSelection = false;
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!open || !inputFocused || !canEditInput()) return false;
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
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
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
        if (!open || !inputFocused || !canEditInput() || sendPending || Character.isISOControl(chr)) return false;
        replaceSelection(String.valueOf(chr));
        return true;
    }

    private void submit() {
        if (!canEditInput() || input.isEmpty() || sendPending || nextSendAt > System.currentTimeMillis()) return;
        pendingText = input.toString();
        sendPending = true;
        if (section == Section.DIRECT && selectedConversation() != null) {
            service.send(selectedConversationId, pendingText, success -> {
                sendResultSuccess = success;
                sendResultReady = true;
            });
            return;
        }
        if (section != Section.GLOBAL) {
            sendPending = false;
            pendingText = null;
            return;
        }
        service.sendGlobal(pendingText, success -> {
            sendResultSuccess = success;
            sendResultReady = true;
        });
    }

    private boolean canEditInput() {
        return section == Section.GLOBAL
                || (section == Section.DIRECT && selectedConversation() != null);
    }

    private void applyAsyncState() {
        if (pendingConversationId != null) {
            selectedConversationId = pendingConversationId;
            pendingConversationId = null;
            section = Section.DIRECT;
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

    private String inputError() {
        String status = service.getStatus();
        if (status == null || status.equals("Online") || status.equals("Offline") || status.equals("Messenger reconnecting")) return null;
        if (status.startsWith("Message blocked")) return Lang.t("messenger.error_blocked");
        if (status.equals("chat_slow_down")) return Lang.t("messenger.error_slowmode");
        if (status.equals("Chat server is outdated")) return Lang.t("messenger.error_server");
        if (status.equals("User blocked")) return Lang.t("messenger.error_blocked_user");
        return Lang.t("messenger.error_send");
    }

    private String actionErrorFor(String status) {
        if (status == null || status.equals("Online") || status.equals("Friend request sent")
                || status.equals("User unblocked")) return Lang.t("messenger.error_send");
        if (status.contains("conversation_forbidden")) return Lang.t("messenger.error_friend_required");
        if (status.contains("blocked") || status.contains("forbidden")) return Lang.t("messenger.error_blocked_user");
        if (status.contains("not_found") || status.contains("not found")) return Lang.t("messenger.error_user_not_found");
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

    private void drawStatus(DrawContext context, float statusX, float statusY, boolean online) {
        drawText(context, Lang.t(online ? "messenger.online" : "messenger.offline"), statusX, statusY,
                4.8F, online ? MenuStyle.ACCENT_GREEN : MenuStyle.TEXT_MUTED, false);
    }

    private void drawCenteredStatus(DrawContext context, float boxX, float boxY, float boxW, boolean online) {
        float textW = MsdfFonts.medium().getWidth(Lang.t(online ? "messenger.online" : "messenger.offline"), 4.8F);
        float start = boxX + (boxW - textW) / 2.0F;
        drawStatus(context, start, boxY, online);
    }

    private void renderAvatar(DrawContext context, float avatarX, float avatarY, float size,
                              String name, int accent) {
        Identifier skin = skins.get(name);
        drawSurface(context, avatarX, avatarY, size, size, MenuStyle.mix(MenuStyle.CARD_INNER, accent, 0.12F), MenuStyle.BORDER);
        Render2DUtil.drawHead(context,
                skin == null ? Identifier.ofVanilla("textures/entity/player/wide/steve.png") : skin,
                avatarX, avatarY, size, 2.0F, 0x00000000, applyGlobalAlpha(0xFFFFFFFF),
                skin == null || skins.hasOverlay(name));
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
