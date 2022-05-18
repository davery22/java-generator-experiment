package io.avery.util.concurrent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GeneratorTest {
    @Test
    void test1() throws InterruptedException, ExecutionException {
        try (var exec = Executors.newVirtualThreadPerTaskExecutor();
             var gen = new Generator<>(exec, GeneratorTest::generate1)
        ) {
            var actual = new ArrayList<Integer>();
            while (gen.next(null, actual::add)) ;
            
            var expected = IntStream.range(0, 10).flatMap(i -> IntStream.range(0, 10)).boxed().toList();
            assertEquals(expected, actual);
            assertNull(gen.result().get());
        }
    }
    
    @Test
    void test2() throws InterruptedException {
        try (var exec = Executors.newVirtualThreadPerTaskExecutor();
             var gen = new Generator<>(exec, (Channel<Void, Integer> chan) -> {
                 for (int i = 0; i < 200000; i++) chan.yield(i);
             })
        ) {
            long start = System.currentTimeMillis();
            long[] sum = { 0 };
            while (gen.next(null, i -> sum[0] += i)) ;
            long end = System.currentTimeMillis();
            System.out.printf("Sum: %d, Elapsed: %d ms%n", sum[0], end-start);
        }
    }
    
    private static void generate1(Channel<Void, Integer> chan) throws InterruptedException, ExecutionException {
        for (int i = 0; i < 10; i++) {
            Generators.yieldAll(chan, GeneratorTest::generate2);
        }
    }
    
    private static void generate2(Channel<Void, Integer> chan) throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            chan.yield(i);
        }
    }
}