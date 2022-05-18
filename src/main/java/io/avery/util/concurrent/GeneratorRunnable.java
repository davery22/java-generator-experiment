package io.avery.util.concurrent;

@FunctionalInterface
public interface GeneratorRunnable<In, Out> {
    void run(Channel<In, Out> chan) throws Exception;
}
