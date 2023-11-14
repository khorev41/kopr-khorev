package sk.upjs.kopr.tools;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public class ThreadSafeLong {

    private final AtomicLong value;
    private final List<ValueChangeListener> listeners;

    public ThreadSafeLong() {
        this.value = new AtomicLong(0);
        this.listeners = new CopyOnWriteArrayList<>();
    }

    public long get() {
        return value.get();
    }

    public void set(long newValue) {
        long oldValue = value.getAndSet(newValue);
        notifyListeners(oldValue, newValue);
    }

    public void addListener(ValueChangeListener listener) {
        listeners.add(listener);
    }

    private void notifyListeners(long oldValue, long newValue) {
        for (ValueChangeListener listener : listeners) {
            listener.valueChanged(oldValue, newValue);
        }
    }

    public interface ValueChangeListener {
        void valueChanged(long oldValue, long newValue);
    }

}
