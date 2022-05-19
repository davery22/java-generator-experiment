package io.avery.util.concurrent;

/**
 * Executes a value-returning task, or throws an exception if unable to do so, possibly suspending and resuming
 * execution multiple times to yield/receive elements to/from a {@link Channel}.
 *
 * @param <In> the type of input elements received from the Channel
 * @param <Out> the type of output elements yielded to the Channel
 * @param <R> the result type
 */
@FunctionalInterface
public interface GeneratorCallable<In, Out, R> {
    /**
     * Executes a value-returning task, or throws an exception if unable to do so, possibly suspending and resuming
     * execution multiple times to yield/receive elements to/from a {@link Channel}.
     *
     * @param chan the Channel
     * @return task result
     * @throws Exception if unable to finish executing the task
     */
    R call(Channel<In, Out> chan) throws Exception;
}
