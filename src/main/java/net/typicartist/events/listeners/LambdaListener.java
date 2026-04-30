package net.typicartist.events.listeners;

import java.util.function.Consumer;

public class LambdaListener<T> extends EventListener {
    private final Consumer<? super T> action;

    public LambdaListener(Object owner, Class<?> type, Consumer<? super T> action, int priority, boolean once) {
        super(owner, type, priority, once);
        this.action = action;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void invoke(Object event) throws Throwable {
        action.accept((T) event);
    }
}
