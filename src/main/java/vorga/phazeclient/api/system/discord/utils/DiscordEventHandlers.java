package vorga.phazeclient.api.system.discord.utils;

import vorga.phazeclient.api.system.discord.callbacks.*;

import com.sun.jna.CallbackReference;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import java.util.Arrays;
import java.util.List;

public class DiscordEventHandlers extends Structure {
    public Pointer disconnected;
    public Pointer joinRequest;
    public Pointer spectateGame;
    public Pointer ready;
    public Pointer errored;
    public Pointer joinGame;

    protected List<String> getFieldOrder() {
        return Arrays.asList("ready", "disconnected", "errored", "joinGame", "spectateGame", "joinRequest");
    }

    public static class Builder {
        private final DiscordEventHandlers handlers = new DiscordEventHandlers();

        public DiscordEventHandlers build() {
            return this.handlers;
        }

        public Builder disconnected(DisconnectedCallback var1) {
            this.handlers.disconnected = CallbackReference.getFunctionPointer(var1);
            return this;
        }

        public Builder errored(ErroredCallback var1) {
            this.handlers.errored = CallbackReference.getFunctionPointer(var1);
            return this;
        }

        public Builder ready(ReadyCallback var1) {
            this.handlers.ready = CallbackReference.getFunctionPointer(var1);
            return this;
        }

        public Builder joinRequest(JoinRequestCallback var1) {
            this.handlers.joinRequest = CallbackReference.getFunctionPointer(var1);
            return this;
        }

        public Builder joinGame(JoinGameCallback var1) {
            this.handlers.joinGame = CallbackReference.getFunctionPointer(var1);
            return this;
        }

        public Builder spectateGame(SpectateGameCallback var1) {
            this.handlers.spectateGame = CallbackReference.getFunctionPointer(var1);
            return this;
        }
    }
}
