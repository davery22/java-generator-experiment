package io.avery.util.concurrent;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        // Try changing this to Executors.newSingleThreadExecutor()
        try (var exec = Executors.newVirtualThreadPerTaskExecutor();
             var gen = new Generator<>(exec, (Channel<Void, Integer> chan) -> {
                 for (int i = 0; i < 1000000; i++) chan.yield(i);
             })
        ) {
            Instant start = Instant.now();
        
            long sum = 0;
            for (Integer n; (n = gen.next(null)) != null;) {
                sum += n;
            }
        
            Instant end = Instant.now();
            System.out.printf("Sum: %d, Elapsed: %s%n", sum, Duration.between(start, end));
        }
    }
}
