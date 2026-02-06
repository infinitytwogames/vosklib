package org.infinitytwogames.vosklib.events;

import net.minecraftforge.eventbus.api.Event;

public class VoskChangeRecognitionState extends Event {
    private final boolean state;
    private final String[] grammar;
    
    public VoskChangeRecognitionState(boolean state, String[] grammar) {
        this.state = state;
        this.grammar = grammar;
    }
    
    public String[] getGrammar() {
        return grammar;
    }
    
    public boolean getState() {
        return state;
    }
}
