package io.avery.util.concurrent;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class GeneratorTest {
    
    @Disabled
    @Test
    void testPerformance() throws InterruptedException {
        // Try changing this to Executors.newSingleThreadExecutor()
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
    
    @Test
    void testNormalResult() throws InterruptedException {
        try (var exec = Executors.newVirtualThreadPerTaskExecutor();
             var gen = new Generator<>(exec, GeneratorTest::greeter)
        ) {
            StringBuilder sb = new StringBuilder();
            gen.next(null, sb::append);
            gen.next("Salutations", sb::append);
            gen.next("Planet", sb::append);
        
            assertEquals("Greeting?Name?", sb.toString());
            assertEquals("Salutations, Planet!", gen.future().resultNow());
        }
    }
    
    @Test
    void testExceptionalResult() throws InterruptedException {
        try (var exec = Executors.newVirtualThreadPerTaskExecutor();
             var gen = new Generator<>(exec, GeneratorTest::greeter)
        ) {
            StringBuilder sb = new StringBuilder();
            gen.next(null, sb::append);
            gen.next("Hello", sb::append);
            gen.next("World", sb::append);
            
            assertEquals("Greeting?Name?", sb.toString());
            assertInstanceOf(StaleGreetingException.class, gen.future().exceptionNow());
        }
    }
    
    @Test
    void testTwoWayCommunication() throws InterruptedException {
       try (var exec = Executors.newVirtualThreadPerTaskExecutor();
            var gen = new Generator<>(exec, (Channel<Integer, Integer> chan) -> {
                int steps = 0;
                int num = chan.yield(1);
                while (steps < 10) {
                    num = chan.yield(num * 2);
                    steps++;
                }
                return steps;
            })
       ) {
           class Box { int n; }
           Box box = new Box();
           
           gen.next(null, i -> box.n = i);
           
           while (gen.next(box.n + 3, i -> box.n = i)) ;
           
           assertEquals(7162, box.n);
           assertEquals(10, gen.future().resultNow());
       }
    }
    
    @Test
    void testNormalCancellation() throws InterruptedException {
        try (var exec = Executors.newVirtualThreadPerTaskExecutor();
             var gen = new Generator<>(exec, GeneratorTest::counter)
        ) {
            List<Integer> actual = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                gen.next(null, actual::add);
            }
            gen.close();
            assertFalse(gen.next(null, actual::add));
            assertEquals(List.of(0, 1, 2), actual);
            assertTrue(gen.future().isCancelled());
        }
    }
    
    @Test
    void testLateCancellation() throws InterruptedException {
        try (var exec = Executors.newVirtualThreadPerTaskExecutor();
             var gen = new Generator<>(exec, GeneratorTest::counter)
        ) {
            List<Integer> actual = new ArrayList<>();
            while (gen.next(null, actual::add));
            gen.close();
            assertEquals(IntStream.range(0, 10).boxed().toList(), actual);
            assertNull(gen.future().resultNow());
        }
    }
   
    @Test
    void testYieldAll() throws InterruptedException {
        try (var exec = Executors.newVirtualThreadPerTaskExecutor();
             var gen = new Generator<>(exec, GeneratorTest::repeater)
        ) {
            var actual = new ArrayList<Integer>();
            while (gen.next(null, actual::add)) ;
            
            var expected = IntStream.range(0, 10).flatMap(i -> IntStream.range(0, 10)).boxed().toList();
            assertEquals(expected, actual);
            assertEquals("done!", gen.future().resultNow());
        }
    }
    
    private static class StaleGreetingException extends Exception {}
    
    private static String greeter(Channel<String, String> chan) throws InterruptedException, StaleGreetingException {
        String greeting = chan.yield("Greeting?");
        String name = chan.yield("Name?");
        if ("Hello".equals(greeting) && "World".equals(name)) throw new StaleGreetingException();
        return "%s, %s!".formatted(greeting, name);
    }
    
    private static String repeater(Channel<Void, Integer> chan) throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            Generators.yieldAll(chan, GeneratorTest::counter);
        }
        return "done!";
    }
    
    private static void counter(Channel<Void, Integer> chan) throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            chan.yield(i);
        }
    }
}