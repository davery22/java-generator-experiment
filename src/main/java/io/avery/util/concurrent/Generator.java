package io.avery.util.concurrent;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.function.Consumer;

/**
 * A handle to an underlying "generator task" (technically, a {@link GeneratorCallable}), that enables resuming the task
 * when it is suspended at yield-points (see {@link #next(Object, Consumer) next()}). The eventual result of the task
 * can also be examined via {@link #future()}, and the task can be cancelled via {@link #close()}.
 *
 * <p>Note that every generator task is initially yielding, and starts upon the first call to
 * {@link #next(Object, Consumer) next()}.
 *
 * @param <In> the type of elements that are passed in to the generator at yield-points
 * @param <Out> the type of elements that are passed out of the generator at yield-points
 * @param <R> the return type of the generator when it completes
 */
public class Generator<In, Out, R> implements AutoCloseable {
    private final PingPong<In, Out> pingPong;
    private final GeneratorFuture future;
    
    /**
     * Creates a new Generator, including submitting the given generator task to the given executor.
     *
     * @param es the Executor to run the generator task in
     * @param generatorRunnable the generator task
     * @throws NullPointerException if either argument is null
     */
    public Generator(Executor es, GeneratorRunnable<In, Out> generatorRunnable) {
        this(es, Generators.callable(generatorRunnable, null));
    }
    
    /**
     * Creates a new Generator, including submitting the given generator task to the given executor.
     *
     * @param es the Executor to run the generator task in
     * @param generatorCallable the generator task
     * @throws NullPointerException if either argument is null
     */
    public Generator(Executor es, GeneratorCallable<In, Out, R> generatorCallable) {
        Objects.requireNonNull(generatorCallable);
        this.pingPong = new PingPong<>();
        this.future = new GeneratorFuture(generatorCallable);
        es.execute(future);
    }
    
    /**
     * Advances the underlying generator task by passing the given element in at the current yield-point, and waiting
     * for a subsequent yield-point to be reached. Returns {@code true} if a subsequent yield-point was reached,
     * in which case the given action was called with the next yielded element. Returns {@code false} if the generator
     * task completed (for any reason) before reaching a subsequent yield-point.
     *
     * <p>Upon returning {@code false}, the associated {@link #future()} will be complete, and can be examined to
     * determine whether the generator task completed normally or exceptionally.
     *
     * <p>Note that the first call to this method starts the generator task, and the input element will be discarded.
     *
     * @param item the element to pass to the yielding generator task
     * @param action the action to run with the next yielded element
     * @throws InterruptedException if the Thread is interrupted while waiting for the generator to yield
     */
    public boolean next(In item, Consumer<? super Out> action) throws InterruptedException {
        return pingPong.ping().next(item, action);
    }
    
    /**
     * A future representing the eventual result of the underlying generator task. This future will resolve to an
     * Exception if the underlying task completes exceptionally or is cancelled.
     *
     * <p>The future is known to be complete after {@link #next(Object, Consumer) next()} returns {@code false}.
     */
    public Future<R> future() {
        return future;
    }
    
    /**
     * Closes the generator, by cancelling and interrupting the underlying generator task if not already completed.
     */
    public void close() {
        future.cancel(true);
    }
    
    /**
     * This subclass of FutureTask is used so that:
     * <ul>
     *     <li>If we cancel the generator task before it runs, the PingPong is still closed, so {@link #next} stops
     *     blocking.
     *     <li>The PingPong is closed strictly after the Future completes. So if {@link #next} returns false, the Future
     *     is guaranteed to be complete, making methods like {@link Future#resultNow} and {@link Future#exceptionNow}
     *     safe to call (after checking {@link Future#state}). Likewise, if {@link #next} returns false, subsequent
     *     calls to {@link Future#cancel} (including via {@link #close}) will do nothing, and will not affect the
     *     generator's result.
     * </ul>
     */
    private class GeneratorFuture extends FutureTask<R> {
        public GeneratorFuture(GeneratorCallable<In, Out, R> generatorCallable) {
            super(() -> {
                var chan = new Channel<>(pingPong.pong());
                chan.yield(null);
                return generatorCallable.call(chan);
            });
        }
        
        @Override
        protected void done() {
            pingPong.pong().close();
        }
    }
}
