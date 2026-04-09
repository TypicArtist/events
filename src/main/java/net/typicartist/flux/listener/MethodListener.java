package net.typicartist.flux.listener;

import java.lang.invoke.MethodHandle;

public class MethodListener extends EventListener {
    private final Object subscriber;
    private final MethodHandle handle;
    
    public MethodListener(Object subscriber, Class<?> type, MethodHandle handle, int priority, boolean once) {
        super(type, priority, once);
        this.subscriber = subscriber;
        this.handle = handle;
    }

    public Object getSubscriber() {
        return subscriber;
    }

    @Override
    public void invoke(Object event) throws Throwable {
        handle.invoke(event);
    }
}