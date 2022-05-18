package io.avery.util.concurrent;

import java.util.Spliterators;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Generators {
    private Generators() {} // Utility
    
    private static final Consumer<Object> NOP = item -> {};
    
    @SuppressWarnings("unchecked")
    public static <T> Consumer<T> nop() { return (Consumer<T>) NOP; }
    
    public static <In, Out, R> GeneratorCallable<In, Out, R> callable(GeneratorRunnable<In, Out> generator, R result) {
        return chan -> { generator.run(chan); return result; };
    }
    
    private static class GeneratorSpliterator<T> extends Spliterators.AbstractSpliterator<T> {
        private final Generator<Void, T, ?> generator;
        
        GeneratorSpliterator(Generator<Void, T, ?> generator) {
            super(Long.MAX_VALUE, 0);
            this.generator = generator;
        }
        
        @Override
        public boolean tryAdvance(Consumer<? super T> action) {
            try {
                return generator.next(null, action);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e); // TODO: idk
            }
        }
    }
    public static <T> Stream<T> stream(Generator<Void, T, ?> generator) {
        return StreamSupport.stream(new GeneratorSpliterator<>(generator), false);
    }
    
    public static <Out> Void yieldAll(
        Channel<Void, Out> chan,
        GeneratorRunnable<Void, Out> generatorRunnable
    ) throws ExecutionException, InterruptedException {
        return yieldAll(chan, Generators.callable(generatorRunnable, null));
    }
    
    public static <Out, R> R yieldAll(
        Channel<Void, Out> chan,
        GeneratorCallable<Void, Out, R> generatorCallable
    ) throws ExecutionException, InterruptedException {
        try (var exec = Executors.newVirtualThreadPerTaskExecutor();
             var generator = new Generator<>(exec, generatorCallable)
        ) {
            return yieldAll(chan, generator);
        }
    }
    
    @SuppressWarnings("unchecked")
    public static <Out, R> R yieldAll(
        Channel<Void, Out> chan,
        Generator<Void, Out, R> generator
    ) throws ExecutionException, InterruptedException {
        Object[] box = { null };
        while (generator.next(null, i -> box[0] = i)) {
            chan.yield((Out) box[0]);
        }
        return generator.result().get();
    }
}
