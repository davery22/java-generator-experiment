package io.avery.util.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public class Generator<In, Out, R> implements AutoCloseable {
    private final PingPong<In, Out>.Ping ping;
    private final Future<R> future;
    
    public Generator(ExecutorService es, GeneratorRunnable<In, Out> generatorRunnable) {
        this(es, Generators.callable(generatorRunnable, null));
    }
    
    public Generator(ExecutorService es, GeneratorCallable<In, Out, R> generatorCallable) {
        var pingPong = new PingPong<In, Out>();
        this.ping = pingPong.ping();
        this.future = es.submit(() -> {
            try (pingPong) {
                var chan = new Channel<>(pingPong.pong());
                chan.yield(null);
                return generatorCallable.call(chan);
            }
        });
    }
    
    public boolean next(In item, Consumer<? super Out> action) throws InterruptedException {
        return ping.next(item, action);
    }
    
    public Future<R> result() {
        return future;
    }
    
    public void close() {
        future.cancel(true);
    }
}
