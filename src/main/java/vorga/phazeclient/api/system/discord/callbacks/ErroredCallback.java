package vorga.phazeclient.api.system.discord.callbacks;

import com.sun.jna.Callback;

public interface ErroredCallback extends Callback {
    void onError(int errorCode, String message);
}
