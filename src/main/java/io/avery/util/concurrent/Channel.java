package io.avery.util.concurrent;

import java.util.Objects;

/**
 * Abstraction used by "generator tasks" ({@link GeneratorRunnable} and {@link GeneratorCallable}) to suspend execution
 * with a yielded element, and later resume with a received input.
 *
 * @param <In> the type of input elements
 * @param <Out> the type of output elements
 */
public class Channel<In, Out> {
    private final PingPong<In, Out>.Pong pong;
    
    /**
     * Creates a new Channel wrapping the Pong side of a {@link PingPong}.
     * @throws NullPointerException if argument is null
     */
    public Channel(PingPong<In, Out>.Pong pong) {
        this.pong = Objects.requireNonNull(pong);
    }
    
    /**
     * Suspends execution of the current Thread, yielding the given element to another waiting Thread, and waiting to be
     * resumed with a new input element.
     *
     * @throws InterruptedException if the Thread is interrupted while yielding
     */
    public In yield(Out item) throws InterruptedException {
        return pong.yield(item);
    }
}
