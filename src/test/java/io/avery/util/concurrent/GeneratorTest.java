package io.avery.util.concurrent;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeneratorTest {
    @Test
    void testBasics() throws InterruptedException {
        try (var exec = Executors.newVirtualThreadPerTaskExecutor();
             var gen = new Generator<>(exec, GeneratorTest::generate1)
        ) {
            var actual = new ArrayList<Integer>();
            while (gen.next(null, actual::add)) ;
            
            var expected = IntStream.range(0, 10).flatMap(i -> IntStream.range(0, 10)).boxed().toList();
            assertEquals(expected, actual);
            assertEquals("Hello, World!", gen.future().resultNow());
        }
    }
    
    @Test
    void testPerformance() throws InterruptedException {
        try (var exec = Executors.newVirtualThreadPerTaskExecutor();
             var gen = new Generator<>(exec, (Channel<Void, Integer> chan) -> {
                 for (int i = 0; i < 1000000; i++) chan.yield(i);
             })
        ) {
            Instant start = Instant.now();
            
            long[] sum = { 0 };
            while (gen.next(null, i -> sum[0] += i)) ;
            
            Instant end = Instant.now();
            System.out.printf("Sum: %d, Elapsed: %s%n", sum[0], Duration.between(start, end));
        }
    }
    
    private static String generate1(Channel<Void, Integer> chan) throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            Generators.yieldAll(chan, GeneratorTest::generate2);
        }
        return "Hello, World!";
    }
    
    private static void generate2(Channel<Void, Integer> chan) throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            chan.yield(i);
        }
    }
    
    // TODO: More tests
}