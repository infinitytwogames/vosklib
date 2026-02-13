package org.infinitytwogames.vosklib.events;

import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

@Cancelable
public class VoskVoiceEvent extends Event {
    private final String result;
    
    public VoskVoiceEvent(String result) {
        this.result = result;
    }
    
    public String getText() {
        return result;
    }
    
    // Fired when the user is currently speaking
    @Cancelable
    public static class Partial extends VoskVoiceEvent {
        public Partial(String result) { super(result); }
    }
    
    // Fired when a full sentence is recognized
    @Cancelable
    public static class Result extends VoskVoiceEvent {
        public Result(String result) { super(result); }
    }
}
