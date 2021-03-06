package io.avery.util.concurrent;

/**
 * Hacky experiment #1: Spin-loop implementation of PingPong, used to strip down the overhead of synchronization and
 * context-switching.
 *
 * Takeaway: Without the overhead, Generator has performance comparable to Python generators.
 */
public class _unusedSpinPingPong<In, Out> {
    private enum State { NEW, RUNNING, YIELDING, DONE }
    
    private final Ping ping = new Ping();
    private final Pong pong = new Pong();
    private volatile State state = State.NEW;
    private volatile Object value = null;
    
    public Ping ping() { return ping; }
    public Pong pong() { return pong; }
    
    public class Ping {
        @SuppressWarnings("unchecked")
        public Out next(In item) {
            while (state == State.NEW) Thread.onSpinWait();
            if (state == State.DONE) return null;
            // assert state == State.YIELDING;
            value = item;
            state = State.RUNNING;
            while (state == State.RUNNING) Thread.onSpinWait();
            if (state == State.DONE) return null;
            // assert state == State.YIELDING;
            return (Out) value;
        }
    }
    
    public class Pong implements AutoCloseable {
        @SuppressWarnings("unchecked")
        public In yield(Out item) {
            // assert state == State.RUNNING || state == State.NEW;
            value = item;
            state = State.YIELDING;
            while (state == State.YIELDING) Thread.onSpinWait();
            // assert state == State.RUNNING;
            return (In) value;
        }
    
        public void close() {
            state = State.DONE;
        }
    }
}

