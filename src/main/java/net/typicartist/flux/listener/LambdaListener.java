package net.typicartist.flux.listener;

import java.util.function.Consumer;

public class LambdaListener<T> extends EventListener {
    private final Consumer<? super T> action;

    public LambdaListener(Class<?> type, Consumer<? super T> action, int priority, boolean once) {
        super(type, priority, once);
        this.action = action;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void invoke(Object event) throws Throwable {
        action.accept((T) event);
    }
}
