package net.typicartist.flux;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

import net.typicartist.flux.annotation.Subscribe;
import net.typicartist.flux.listener.EventListener;
import net.typicartist.flux.listener.LambdaListener;
import net.typicartist.flux.listener.MethodListener;

public class EventBus {
    public interface Subscription {
        void unsubscribe();
        boolean isActive();        
    }

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private final Map<Class<?>, CopyOnWriteArrayList<EventListener>> eventListeners = new ConcurrentHashMap<>();
    private final Map<Object, List<EventListener>> listenerCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, Set<Class<?>>> hierarchyCache = Collections.synchronizedMap(new WeakHashMap<>());

    private final Comparator<EventListener> comparator = Comparator
        .comparingInt(EventListener::getPriority)
        .reversed()
        .thenComparingInt(System::identityHashCode);

    private final Consumer<Throwable> exceptionHandler = Throwable::printStackTrace;

    public <T> T post(T event) {
        Set<Class<?>> types = resolveHierarchy(event.getClass());

        for (Class<?> type : types) {
            CopyOnWriteArrayList<EventListener> listeners = eventListeners.get(type);
            if (listeners == null || listeners.isEmpty()) continue;

            List<EventListener> toRemove = new ArrayList<>();

            for (EventListener listener : listeners) {
                try { 
                    listener.invoke(event); 
                } catch (Throwable t) { 
                    exceptionHandler.accept(t); 
                }
                
                if (listener.isOnce()) toRemove.add(listener);
                if (event instanceof ICancellable c && c.isCancelled()) break;
            }

            listeners.removeAll(toRemove);
            if(event instanceof ICancellable c && c.isCancelled()) break;
        }

        return event;
    }

    public <T> Subscription subscribe(Class<T> type, Consumer<? super T> action, EventPriority priority, boolean once) {
        LambdaListener<T> listener = new LambdaListener<>(type, action, priority.getValue(), once);
        addListener(type, listener);

        return new Subscription() {
            private volatile boolean active = true;

            @Override
            public void unsubscribe() {
                if (!active) return;
                active = false;
                CopyOnWriteArrayList<EventListener> list = eventListeners.get(type);
                if (list != null) list.remove(listener);
            }

            @Override
            public boolean isActive() {
                return active;
            }
        };
    }
    
    public void subscribe(Object subscriber) {
        List<EventListener> listeners = listenerCache.computeIfAbsent(subscriber, s -> new ArrayList<>());

        if (!listeners.isEmpty()) {
            for (EventListener listener : listeners) {
                addListener(listener.getType(), listener);
            }
            return;
        }

        Class<?> clazz = subscriber.getClass();
        for (Method method : clazz.getDeclaredMethods()) {
            if (!isValid(method)) continue;

            Subscribe meta = method.getAnnotation(Subscribe.class);
            Class<?> type = method.getParameterTypes()[0];

            try {
                method.setAccessible(true);
                MethodHandle handle = LOOKUP.unreflect(method).bindTo(subscriber);
                MethodListener listener = new MethodListener(subscriber, type, handle, meta.priority().getValue(), meta.once());
                addListener(type, listener);
                listeners.add(listener);
            } catch (IllegalAccessException e) {
                exceptionHandler.accept(e);
            }
        }
    }

    public void unsubscribe(Object subscriber) {
        List<EventListener> listeners = listenerCache.get(subscriber);
        if (listeners != null) {
            for (EventListener listener : listeners) {
                CopyOnWriteArrayList<EventListener> list = eventListeners.get(listener.getType());
                if (list != null) list.remove(listener);
            }
            listeners.clear();
        }
    }

    private static boolean isValid(Method method) {
        if (!method.isAnnotationPresent(Subscribe.class)) return false;
        if (method.getParameterCount() != 1) return false;
        if (method.getReturnType() != void.class) return false;
        if (Modifier.isStatic(method.getModifiers()) || Modifier.isAbstract(method.getModifiers())) return false;
        if (method.isBridge() || method.isSynthetic()) return false;
        return true;
    }

    private <T> void addListener(Class<T> type, EventListener listener) {
        CopyOnWriteArrayList<EventListener> list = eventListeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>());

        int index = Collections.binarySearch(list, listener, comparator);
        if (index < 0) index = -index - 1;
        list.add(index, listener);
    }
   
    public <T> boolean hasListeners(Class<T> type) {
        CopyOnWriteArrayList<EventListener> list = eventListeners.get(type);
        return list != null && !list.isEmpty();
    }

    public <T> int listenerCount(Class<T> type) {
        CopyOnWriteArrayList<EventListener> list = eventListeners.get(type);
        return list == null ? 0 : list.size();
    }

    private Set<Class<?>> resolveHierarchy(Class<?> clazz) {
        return hierarchyCache.computeIfAbsent(clazz, c -> {
            Set<Class<?>> result = new LinkedHashSet<>();
            Queue<Class<?>> queue = new ArrayDeque<>();
            queue.add(c);

            while (!queue.isEmpty()) {
                Class<?> current = queue.poll();
                if (current == null || !result.add(current)) continue;
                queue.add(current.getSuperclass());
                queue.addAll(Arrays.asList(current.getInterfaces()));
            }
            return result;
        });
    }
}