package io.avery.util.concurrent;

/**
 * Executes a task, or throws an exception if unable to do so, possibly suspending and resuming execution multiple times
 * to yield/receive elements to/from a {@link Channel}.
 *
 * @param <In> the type of input elements received from the Channel
 * @param <Out> the type of output elements yielded to the Channel
 */
@FunctionalInterface
public interface GeneratorRunnable<In, Out> {
    /**
     * Executes a task, or throws an exception if unable to do so, possibly suspending and resuming execution multiple
     * times to yield/receive elements to/from a {@link Channel}.
     *
     * @param chan the Channel
     * @throws Exception if unable to finish executing the task
     */
    void run(Channel<In, Out> chan) throws Exception;
}
