package vorga.phazeclient.api.system.discord.callbacks;

import com.sun.jna.Callback;

public interface JoinGameCallback extends Callback {
    void onJoinGame(String joinSecret);
}
