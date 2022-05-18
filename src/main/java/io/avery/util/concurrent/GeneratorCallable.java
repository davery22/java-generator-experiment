package io.avery.util.concurrent;

@FunctionalInterface
public interface GeneratorCallable<In, Out, R> {
    R call(Channel<In, Out> chan) throws Exception;
}
