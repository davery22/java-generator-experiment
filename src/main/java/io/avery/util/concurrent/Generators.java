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
        try (var gen = generator) {
            for (Out value; (value = gen.next(null)) != null;) {
                chan.yield(value);
            }
            return gen.future();
        }
    }
}
