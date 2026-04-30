package net.typicartist.events;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

import net.typicartist.events.annotation.Subscribe;
import net.typicartist.events.listeners.EventListener;
import net.typicartist.events.listeners.LambdaListener;
import net.typicartist.events.listeners.MethodListener;

public class EventBus {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final Comparator<EventListener> COMPARATOR = Comparator.comparingInt(EventListener::getPriority).reversed();    

    private final Set<Object> owners = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<Class<?>, CopyOnWriteArrayList<EventListener>> listeners = new ConcurrentHashMap<>();
    private final Map<Class<?>, Class<?>[]> hierarchyCache = new ConcurrentHashMap<>();

    public <T> T post(T event) {
        ICancellable cancellable = (event instanceof ICancellable) ? (ICancellable) event : null;

        for (Class<?> type : resolveHierarchy(event.getClass())) {
            List<EventListener> list = listeners.get(type);
            if (list == null || list.isEmpty()) continue;
            
            List<EventListener> toRemove = null;

            for (EventListener listener : list) {
                if (!listener.isActive()) continue;
                try { 
                    listener.invoke(event); 
                } catch (Throwable t) { 
                    t.printStackTrace();
                }
                if (listener.isOnce()) {
                    if (toRemove == null) toRemove = new ArrayList<>();
                    toRemove.add(listener);
                }
                if (cancellable != null && cancellable.isCancelled()) break;
            }
            
            if (toRemove != null) { 
                final List<EventListener> remove = toRemove; 
                list.removeIf(remove::contains);
            }
        }

        return event;
    }

    public <T> void register(Object owner, Class<T> type, Consumer<? super T> action, EventPriority priority, boolean once) {
        addListener(type, new LambdaListener<>(owner, type, action, priority.value(), once));
    }

    public void register(Object owner) {
        if (!owners.add(owner)) return;
        scanMethods(owner);
    }

    public void unregister(Object owner) {
        owners.remove(owner);
        deactivate(owner);
        for (List<EventListener> list : listeners.values()) {
            list.removeIf(l -> l.getOwner() == owner);
        }
        listeners.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    public void activate(Object owner) { setActive(owner, true); }
    public void deactivate(Object owner) { setActive(owner, false); }

    private void setActive(Object owner, boolean active) {
        for (List<EventListener> list : listeners.values()) {
            for (EventListener listener : list) {
                if (listener.getOwner() == owner) listener.setActive(active);
            }
        }
    }

    public <T> int listenerCount(Class<T> type) {
        List<EventListener> list = listeners.get(type);
        return list == null ? 0 : list.size();
    }

    public <T> boolean hasListeners(Class<T> type) {
        List<EventListener> list = listeners.get(type);
        return list != null && !list.isEmpty();
    }

    private void scanMethods(Object owner) {
        for (Class<?> cur : resolveHierarchy(owner.getClass())) {
            for (Method m : cur.getDeclaredMethods()) {
                if (!isValid(m)) continue;

                Subscribe meta = m.getAnnotation(Subscribe.class);
                Class<?> eventType = m.getParameterTypes()[0];

                try {
                    if (!m.canAccess(owner)) m.setAccessible(true);
                    MethodHandle handle = LOOKUP.unreflect(m).bindTo(owner);
                    addListener(eventType, new MethodListener(owner, eventType, handle, meta.priority().value(), meta.once()));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static boolean isValid(Method m) {
        return m.isAnnotationPresent(Subscribe.class) 
            && m.getParameterCount() == 1
            && m.getReturnType() == void.class
            && !Modifier.isStatic(m.getModifiers())
            && !Modifier.isAbstract(m.getModifiers())
            && !m.isBridge()
            && !m.isSynthetic();
    }

    private <T> void addListener(Class<T> type, EventListener listener) {
        CopyOnWriteArrayList<EventListener> list = listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>());
        synchronized (list) {
            int index = Collections.binarySearch(list, listener, COMPARATOR);
            if (index < 0) index = -index - 1;
            list.add(index, listener);
        }
    }

    private Class<?>[] resolveHierarchy(Class<?> clazz) {
        return hierarchyCache.computeIfAbsent(clazz, c -> {
            Set<Class<?>> visited = new LinkedHashSet<>();
            Deque<Class<?>> queue = new ArrayDeque<>();
            queue.add(c);
            while (!queue.isEmpty()) {
                Class<?> cur = queue.poll();
                if (cur == null || cur == Object.class || visited.contains(cur)) continue;
                visited.add(cur);
                Class<?> superclass = cur.getSuperclass();
                if (superclass != null) queue.add(superclass);
                for (Class<?> iface : cur.getInterfaces()) {
                    if (!visited.contains(iface)) queue.add(iface);
                }
            }
            return visited.toArray(Class<?>[]::new);
        });
    }
}