package net.typicartist.flux.listener;

public abstract class EventListener {
    protected final Class<?> type;

    protected final int priority;
    protected final boolean once;

    public EventListener(Class<?> type, int priority, boolean once) {
        this.type = type;
        this.priority = priority;
        this.once = once;
    }
    
    public Class<?> getType() { return type; }
    public int getPriority() { return priority; }
    public boolean isOnce() { return once; }

    public abstract void invoke(Object event) throws Throwable;
}