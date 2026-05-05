package vorga.phazeclient.api.system.discord.callbacks;

import com.sun.jna.Callback;

public interface SpectateGameCallback extends Callback {
    void onSpectateGame(String spectateSecret);
}
