package net.typicartist.events;

public interface ICancellable {
    void setCancelled(boolean cancelled);
    
    default void cancel() {
        setCancelled(true);
    }
    
    boolean isCancelled();
}