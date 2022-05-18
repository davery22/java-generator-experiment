package io.avery.util.concurrent;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class PingPong<In, Out> implements AutoCloseable {
    private enum State { NEW, RUNNING, YIELDING, DONE }
    
    private final Ping ping = new Ping();
    private final Pong pong = new Pong();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition cond = lock.newCondition();
    private State state = State.NEW;
    private Object value = null;
    
    public Ping ping() { return ping; }
    public Pong pong() { return pong; }
    
    public class Ping {
        @SuppressWarnings("unchecked")
        public boolean next(In item, Consumer<? super Out> action) throws InterruptedException {
            Out temp;
            lock.lockInterruptibly();
            try {
                while (state == State.NEW) cond.await(); // Wait for initial yield()
                if (state == State.RUNNING) throw new IllegalStateException("Concurrent call to next()");
                if (state == State.DONE) return false;
                value = item;
                state = State.RUNNING;
                cond.signalAll();
                while (state == State.RUNNING) cond.await();
                if (state == State.DONE) return false;
                temp = (Out) value;
                value = null; // help gc
            } finally {
                lock.unlock();
            }
            action.accept(temp);
            return true;
        }
    }
    
    public class Pong {
        @SuppressWarnings("unchecked")
        public In yield(Out item) throws InterruptedException {
            In temp;
            lock.lockInterruptibly();
            try {
                if (state == State.YIELDING || state == State.DONE) throw new IllegalStateException("Concurrent call to yield()");
                value = item;
                state = State.YIELDING;
                cond.signalAll();
                while (state == State.YIELDING) cond.await();
                if (state == State.DONE) throw new IllegalStateException("Concurrent call to yield()");
                temp = (In) value;
                value = null; // help gc
                return temp;
            } finally {
                lock.unlock();
            }
        }
    }
    
    public void close() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            state = State.DONE;
            cond.signalAll();
        } finally {
            lock.unlock();
        }
    }
}

