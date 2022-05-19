package io.avery.util.concurrent;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Utility methods for {@link Generator}.
 */
public class Generators {
    private Generators() {} // Utility
    
    /**
     * Returns a {@link GeneratorCallable} object that, when called, runs the given generator task and returns the given
     * result. This is analogous to {@link Executors#callable}.
     *
     * @throws NullPointerException if task is null
     */
    public static <In, Out, R> GeneratorCallable<In, Out, R> callable(GeneratorRunnable<In, Out> generator, R result) {
        Objects.requireNonNull(generator);
        return chan -> { generator.run(chan); return result; };
    }
    
    /**
     * Yields all elements from the given generator task to the given channel, and returns the generator's future
     * result.
     *
     * <p>The generator task is wrapped to return {@code null}, and executed in a new virtual thread executor. The
     * generator task is always complete when this method returns.
     *
     * @throws InterruptedException if the Thread is interrupted while yielding or waiting for the generator to yield
     */
    public static <Out> Future<Void> yieldAll(
        Channel<Void, Out> chan,
        GeneratorRunnable<Void, Out> generatorRunnable
    ) throws InterruptedException {
        return yieldAll(chan, Generators.callable(generatorRunnable, null));
    }
    
    /**
     * Yields all elements from the given generator task to the given channel, and returns the generator's future
     * result.
     *
     * <p>The generator task is executed in a new virtual thread executor. The generator task is always complete when
     * this method returns.
     *
     * @throws InterruptedException if the Thread is interrupted while yielding or waiting for the generator to yield
     */
    public static <Out, R> Future<R> yieldAll(
        Channel<Void, Out> chan,
        GeneratorCallable<Void, Out, R> generatorCallable
    ) throws InterruptedException {
        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            return yieldAll(chan, new Generator<>(exec, generatorCallable));
        }
    }
    
    /**
     * Yields all elements from the given generator to the given channel, and returns the generator's future result.
     *
     * <p>The generator is always closed when this method returns.
     *
     * @throws InterruptedException if the Thread is interrupted while yielding or waiting for the generator to yield
     */
    public static <Out, R> Future<R> yieldAll(
        Channel<Void, Out> chan,
        Generator<Void, Out, R> generator
    ) throws InterruptedException {
        // Wrap-and-unwrap the checked exception, to circumvent the no-exception signature of Consumer.
        // Alternatively, we could use a box with interior mutability, then yield outside next. Not sure which is more performant.
        try (var gen = generator) {
            while (gen.next(null, i -> {
                try {
                    chan.yield(i);
                } catch (InterruptedException e) {
                    throw new UncheckedInterruptedException(e);
                }
            })) ;
            return gen.future();
        } catch (UncheckedInterruptedException e) {
            throw (InterruptedException) e.getCause();
        }
    }
    
    private static class UncheckedInterruptedException extends RuntimeException {
        UncheckedInterruptedException(InterruptedException e) {
            super(e);
        }
    }
}
