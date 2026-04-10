package net.typicartist.events.listeners;

import java.lang.invoke.MethodHandle;

public class MethodListener extends EventListener {
    private final MethodHandle handle;
    
    public MethodListener(Object owner, Class<?> type, MethodHandle handle, int priority, boolean once, long order) {
        super(owner, type, priority, once, order);
        this.handle = handle;
    }

    @Override
    public void invoke(Object event) throws Throwable {
        handle.invoke(event);
    }
}