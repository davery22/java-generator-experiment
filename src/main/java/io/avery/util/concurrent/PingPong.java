package io.avery.util.concurrent;

import java.util.ConcurrentModificationException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * A synchronization mechanism used to implement a {@link Generator}. PingPong has 2 sides: a {@link Ping Ping} side and
 * a {@link Pong Pong} side. Each side is intended to be exclusively owned by its own, single, Thread. Best-effort is
 * made to throw ConcurrentModificationException if either side is called concurrently.
 *
 * <p>After an initial Pong.{@link Pong#yield(Object) yield()} suspends the Pong-side, execution proceeds in lock-step:
 * A call to Ping.{@link Ping#next(Object, Consumer) next()} wakes up the Pong-side and simultaneously suspends the
 * Ping-side until the next "yield-point", and so on.
 *
 * <p>At each yield-point, both sides exchange values. Specifically, on {@code pong.yield()}, the Pong-side wakes up the
 * waiting Ping-side and passes a value for it to consume in {@code ping.next()}, then Pong suspends itself. On
 * {@code ping.next()}, the Ping-side wakes up the waiting Pong-side and passes a value for it to return from
 * {@code pong.yield()}, then Ping suspends itself.
 *
 * <p>This proceeds until the Pong-side calls Pong.{@link Pong#close() close()}, at which point any suspended threads
 * are woken up, further calls to {@code next()} return {@code false}, and further calls to {@code yield()} throw an
 * unspecified Exception (probably CME).
 *
 * @param <In> the type of "input" elements (passed in to Ping.{@link Ping#next(Object, Consumer) next()}, returned from
 *            Pong.{@link Pong#yield(Object) yield()})
 * @param <Out> the type of "output" elements (passed in to Pong.{@link Pong#yield(Object) yield()}, consumed by
 *             Ping.{@link Ping#next(Object, Consumer) next()}
 */
public class PingPong<In, Out> {
    private enum State { NEW, RUNNING, YIELDING, DONE }
    
    private final Ping ping = new Ping();
    private final Pong pong = new Pong();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition cond = lock.newCondition();
    private State state = State.NEW;
    private Object value = null;
    
    /**
     * Returns the Ping side of this PingPong.
     */
    public Ping ping() {
        return ping;
    }
    
    /**
     * Returns the Pong side of this PingPong.
     */
    public Pong pong() {
        return pong;
    }
    
    
    /**
     * The Ping side of a {@link PingPong}.
     */
    public class Ping {
        
        /**
         * Wakes up a waiting Pong-side, passing the given value to it, and suspending until one of 3 events happens:
         * <ul>
         *     <li>The Pong-side calls {@link Pong#yield(Object) yield()}, passing in a value that is consumed by the
         *     given action, and this method returns {@code true}.
         *     <li>The Pong-side calls {@link Pong#close() close()}, and this method returns {@code false}.
         *     <li>The thread is interrupted, and this method throws InterruptedException.
         * </ul>
         *
         * @param item the value to pass to the Pong-side
         * @param action the action to call with the eventual value received from the Pong-side
         * @return true if Pong-side yields again; false if it closes
         * @throws InterruptedException if the Thread is interrupted while waiting for the next yield
         */
        public boolean next(In item, Consumer<? super Out> action) throws InterruptedException {
            Object temp;
            lock.lockInterruptibly();
            try {
                while (state == State.NEW) cond.await(); // Wait for initial yield()
                if (state == State.RUNNING) throw new ConcurrentModificationException("Concurrent call to next()");
                if (state == State.DONE) return false;
                // assert state == State.YIELDING;
                value = item;
                state = State.RUNNING;
                cond.signalAll();
                while (state == State.RUNNING) cond.await();
                if (state == State.DONE) return false;
                // assert state == State.YIELDING;
                temp = value;
                value = null; // help gc
            } finally {
                lock.unlock();
            }
            @SuppressWarnings("unchecked")
            Out out = (Out) temp;
            action.accept(out);
            return true;
        }
    }
    
    /**
     * The Pong side of a {@link PingPong}.
     */
    public class Pong implements AutoCloseable {
        
        /**
         * Wakes up a waiting Ping-side, passing the given value to it, and suspending until one of 2 events happens:
         * <ul>
         *     <li>The ping-side calls {@link Ping#next(Object, Consumer) next()}, passing in a value that is returned
         *     from this method.
         *     <li>The thread is interrupted, and this method throws InterruptedException.
         * </ul>
         *
         * @param item the value to pass to the Ping-side
         * @return the eventual value received from the Ping-side
         * @throws InterruptedException if the Thread is interrupted while yielding
         */
        public In yield(Out item) throws InterruptedException {
            lock.lockInterruptibly();
            try {
                if (state == State.YIELDING || state == State.DONE) throw new ConcurrentModificationException("Concurrent call to yield()");
                // assert state == State.RUNNING || state == State.NEW;
                value = item;
                state = State.YIELDING;
                cond.signalAll();
                while (state == State.YIELDING) cond.await();
                if (state == State.DONE) throw new ConcurrentModificationException("Concurrent call to yield()");
                // assert state == State.RUNNING;
                @SuppressWarnings("unchecked")
                In in = (In) value;
                value = null; // help gc
                return in;
            } finally {
                lock.unlock();
            }
        }
    
        /**
         * Closes the PingPong by setting the state to DONE and waking up all threads.
         */
        public void close() {
            lock.lock();
            try {
                state = State.DONE;
                cond.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }
}

