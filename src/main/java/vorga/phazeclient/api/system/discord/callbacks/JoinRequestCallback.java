package vorga.phazeclient.api.system.discord.callbacks;

import com.sun.jna.Callback;
import vorga.phazeclient.api.system.discord.utils.DiscordUser;

public interface JoinRequestCallback extends Callback {
    void onJoinRequest(DiscordUser user);
}
