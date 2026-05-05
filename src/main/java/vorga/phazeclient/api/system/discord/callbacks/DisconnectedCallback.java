package vorga.phazeclient.api.system.discord.callbacks;

import com.sun.jna.Callback;

public interface DisconnectedCallback extends Callback {
    void onDisconnected(int errorCode, String message);
}
