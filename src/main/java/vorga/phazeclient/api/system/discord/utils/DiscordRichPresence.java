package vorga.phazeclient.api.system.discord.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DiscordRichPresence {
    public String largeImageKey;
    public String largeImageText;
    public String smallImageText;
    public String partyPrivacy;
    public long startTimestamp;
    public int instance;
    public String partyId;
    public int partySize;
    public long endTimestamp;
    public String details;
    public String joinSecret;
    public String spectateSecret;
    public String smallImageKey;
    public String matchSecret;
    public String state;
    public int partyMax;
    public String button_url_1;
    public String button_label_1;
    public String button_url_2;
    public String button_label_2;

    public JsonObject toJson() {
        JsonObject activity = new JsonObject();
        if (state != null && !state.isEmpty()) activity.addProperty("state", state);
        if (details != null && !details.isEmpty()) activity.addProperty("details", details);
        if (startTimestamp > 0 || endTimestamp > 0) {
            JsonObject timestamps = new JsonObject();
            if (startTimestamp > 0) timestamps.addProperty("start", startTimestamp);
            if (endTimestamp > 0) timestamps.addProperty("end", endTimestamp);
            activity.add("timestamps", timestamps);
        }
        if (largeImageKey != null || smallImageKey != null) {
            JsonObject assets = new JsonObject();
            if (largeImageKey != null) assets.addProperty("large_image", largeImageKey);
            if (largeImageText != null) assets.addProperty("large_text", largeImageText);
            if (smallImageKey != null) assets.addProperty("small_image", smallImageKey);
            if (smallImageText != null) assets.addProperty("small_text", smallImageText);
            activity.add("assets", assets);
        }
        JsonArray buttons = new JsonArray();
        if (button_label_1 != null && button_url_1 != null) {
            JsonObject button = new JsonObject();
            button.addProperty("label", button_label_1);
            button.addProperty("url", button_url_1);
            buttons.add(button);
        }
        if (button_label_2 != null && button_url_2 != null) {
            JsonObject button = new JsonObject();
            button.addProperty("label", button_label_2);
            button.addProperty("url", button_url_2);
            buttons.add(button);
        }
        if (!buttons.isEmpty()) activity.add("buttons", buttons);
        activity.addProperty("instance", instance != 0);
        return activity;
    }

    public static class Builder {
        private final DiscordRichPresence richPresence = new DiscordRichPresence();

        public Builder setSmallImage(String var1) {
            return this.setSmallImage(var1, "");
        }

        public Builder setDetails(String var1) {
            if (var1 != null && !var1.isEmpty()) {
                this.richPresence.details = var1.substring(0, Math.min(var1.length(), 128));
            }

            return this;
        }

        public Builder setLargeImage(String var1, String var2) {
            this.richPresence.largeImageKey = var1;
            this.richPresence.largeImageText = var2;
            return this;
        }

        public Builder setState(String var1) {
            if (var1 != null && !var1.isEmpty()) {
                this.richPresence.state = var1.substring(0, Math.min(var1.length(), 128));
            }

            return this;
        }

        public Builder setInstance(boolean var1) {
            if ((this.richPresence.button_label_1 == null || !this.richPresence.button_label_1.isEmpty()) && (this.richPresence.button_label_2 == null || !this.richPresence.button_label_2.isEmpty())) {
                this.richPresence.instance = var1 ? 1 : 0;
            }
            return this;
        }

        public Builder setButtons(RPCButton var1) {
            return this.setButtons(Collections.singletonList(var1));
        }

        public Builder setSmallImage(String var1, String var2) {
            this.richPresence.smallImageKey = var1;
            this.richPresence.smallImageText = var2;
            return this;
        }

        public Builder setButtons(List<RPCButton> buttons) {
            if (buttons != null && !buttons.isEmpty()) {
                int var2 = Math.min(buttons.size(), 2);
                this.richPresence.button_label_1 = buttons.get(0).getLabel();
                this.richPresence.button_url_1 = buttons.get(0).getUrl();
                if (var2 == 2) {
                    this.richPresence.button_label_2 = buttons.get(1).getLabel();
                    this.richPresence.button_url_2 = buttons.get(1).getUrl();
                }
            }

            return this;
        }

        public Builder setStartTimestamp(OffsetDateTime var1) {
            this.richPresence.startTimestamp = var1.toEpochSecond();
            return this;
        }

        public Builder setSecrets(String var1, String var2, String var3) {
            if ((this.richPresence.button_label_1 == null || !this.richPresence.button_label_1.isEmpty()) && (this.richPresence.button_label_2 == null || !this.richPresence.button_label_2.isEmpty())) {
                this.richPresence.matchSecret = var1;
                this.richPresence.joinSecret = var2;
                this.richPresence.spectateSecret = var3;
            }
            return this;
        }

        public Builder setButtons(RPCButton var1, RPCButton var2) {
            this.setButtons(Arrays.asList(var1, var2));
            return this;
        }

        public Builder setStartTimestamp(long var1) {
            this.richPresence.startTimestamp = var1;
            return this;
        }

        public Builder setSecrets(String var1, String var2) {
            if ((this.richPresence.button_label_1 == null || !this.richPresence.button_label_1.isEmpty()) && (this.richPresence.button_label_2 == null || !this.richPresence.button_label_2.isEmpty())) {
                this.richPresence.joinSecret = var1;
                this.richPresence.spectateSecret = var2;
            }
            return this;
        }

        public Builder setEndTimestamp(long var1) {
            this.richPresence.endTimestamp = var1;
            return this;
        }

        public Builder setEndTimestamp(OffsetDateTime var1) {
            this.richPresence.endTimestamp = var1.toEpochSecond();
            return this;
        }

        public Builder setLargeImage(String var1) {
            return this.setLargeImage(var1, "");
        }

        public DiscordRichPresence build() {
            return this.richPresence;
        }
    }
}
