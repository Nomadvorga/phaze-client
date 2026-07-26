package vorga.phazeclient.api.system.discord.callbacks;

import com.sun.jna.Callback;
import vorga.phazeclient.api.system.discord.utils.DiscordUser;

public interface ReadyCallback extends Callback {
    void onReady(DiscordUser user);
}
