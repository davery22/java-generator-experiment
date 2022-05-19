package io.avery.util.concurrent;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

/**
 * Hacky implementation of PingPong, used to strip down the overhead of synchronization, so that we just see the
 * overhead of context-switching.
 *
 * Takeaway: Context-switching is by far the larger overhead.
 */
public class _unusedPingPongHack<In, Out> {
    private enum State { NEW, RUNNING, YIELDING, DONE }
    
    private final Ping ping = new Ping();
    private final Pong pong = new Pong();
    private volatile State state = State.NEW;
    private volatile Object value = null;
    private final AtomicReference<Thread> waiter = new AtomicReference<>();
    
    public Ping ping() { return ping; }
    public Pong pong() { return pong; }
    
    public class Ping {
        @SuppressWarnings("unchecked")
        public boolean next(In item, Consumer<? super Out> action) {
                // Only set me to the waiter if there is no other waiter yet.
                // Success implies that state == NEW and the other thread has not attempted setting.
                Thread me = Thread.currentThread();
                waiter.compareAndSet(null, me);

            while (state == State.NEW) LockSupport.park();
            if (state == State.DONE) return false;

                // At this point we know that another thread is the waiter,
                // because the state == YIELDING
                Thread prevWaiter = waiter.getAndSet(me);

            // assert state == State.YIELDING
            value = item;
            state = State.RUNNING;

                LockSupport.unpark(prevWaiter);

            while (state == State.RUNNING) LockSupport.park();
            if (state == State.DONE) return false;
            // assert state == State.YIELDING
            action.accept((Out) value);
            return true;
        }
    }
    
    public class Pong implements AutoCloseable {
        @SuppressWarnings("unchecked")
        public In yield(Out item) {
                // Note: Before first yield (state == State.NEW), waiter MAY be null.
                Thread me = Thread.currentThread();
                Thread prevWaiter = waiter.getAndSet(me);

            // assert state == State.RUNNING || state == State.NEW;
            value = item;
            state = State.YIELDING;

                if (prevWaiter != null) LockSupport.unpark(prevWaiter);

            while (state == State.YIELDING) LockSupport.park();
            // assert state == State.RUNNING;
            return (In) value;
        }
    
        public void close() {
                Thread prevWaiter = waiter.get();
        
            state = State.DONE;
        
                if (prevWaiter != null) LockSupport.unpark(prevWaiter);
        }
    }
}

