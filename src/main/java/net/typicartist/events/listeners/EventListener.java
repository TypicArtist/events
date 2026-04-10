package net.typicartist.events.listeners;

public abstract class EventListener {
    protected final Object owner;
    protected final Class<?> type;

    protected final int priority;
    protected final boolean once;
    protected final long order;
    
    protected volatile boolean active = true;

    public EventListener(Object owner, Class<?> type, int priority, boolean once, long order) {
        this.owner = owner;
        this.type = type;
        this.priority = priority;
        this.once = once;
        this.order = order;
    }
    
    public Object getOwner() { return owner; }
    public Class<?> getType() { return type; }
    
    public long getOrder() { return order; }

    public int getPriority() { return priority; }
    public boolean isOnce() { return once; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public abstract void invoke(Object event) throws Throwable;
}