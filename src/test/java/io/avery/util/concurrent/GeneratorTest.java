package io.avery.util.concurrent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class GeneratorTest {
    
    @Test
    void testNormalResult() throws InterruptedException {
        try (var exec = Executors.newVirtualThreadPerTaskExecutor();
             var gen = new Generator<>(exec, GeneratorTest::greeter)
        ) {
            String out = gen.next(null) + gen.next("Salutations");
            gen.next("Planet");
        
            assertEquals("Greeting?Name?", out);
            assertEquals("Salutations, Planet!", gen.future().resultNow());
        }
    }
    
    @Test
    void testExceptionalResult() throws InterruptedException {
        try (var exec = Executors.newVirtualThreadPerTaskExecutor();
             var gen = new Generator<>(exec, GeneratorTest::greeter)
        ) {
            String out = gen.next(null) + gen.next("Hello");
            gen.next("World");
            
            assertEquals("Greeting?Name?", out);
            assertInstanceOf(StaleGreetingException.class, gen.future().exceptionNow());
        }
    }
    
    @Test
    void testTwoWayCommunication() throws InterruptedException {
       try (var exec = Executors.newVirtualThreadPerTaskExecutor();
            var gen = new Generator<>(exec, (Channel<Integer, Integer> chan) -> {
                int num = chan.yield(1);
                for (int steps = 0; steps < 10; steps++) {
                    num = chan.yield(num * 2);
                }
                return 10;
            })
       ) {
           int n = gen.next(null);
           for (Integer nn; (nn = gen.next(n + 3)) != null;) {
               n = nn;
           }
           
           assertEquals(7162, n);
           assertEquals(10, gen.future().resultNow());
       }
    }
    
    @Test
    void testNormalCancellation() throws InterruptedException {
        try (var exec = Executors.newVirtualThreadPerTaskExecutor();
             var gen = new Generator<>(exec, GeneratorTest::counter)
        ) {
            var actual = new ArrayList<Integer>();
            for (int i = 0; i < 3; i++) {
                actual.add(gen.next(null));
            }
            gen.close();
            
            assertNull(gen.next(null));
            assertEquals(List.of(0, 1, 2), actual);
            assertTrue(gen.future().isCancelled());
        }
    }
    
    @Test
    void testLateCancellation() throws InterruptedException {
        try (var exec = Executors.newVirtualThreadPerTaskExecutor();
             var gen = new Generator<>(exec, GeneratorTest::counter)
        ) {
            var actual = new ArrayList<Integer>();
            for (Integer num; (num = gen.next(null)) != null;) {
                actual.add(num);
            }
            gen.close();
            
            assertNull(gen.next(null));
            assertEquals(IntStream.range(0, 10).boxed().toList(), actual);
            assertFalse(gen.future().isCancelled());
            assertNull(gen.future().resultNow());
        }
    }
   
    @Test
    void testYieldAll() throws InterruptedException {
        try (var exec = Executors.newVirtualThreadPerTaskExecutor();
             var gen = new Generator<>(exec, GeneratorTest::repeater)
        ) {
            var actual = new ArrayList<Integer>();
            for (Integer num; (num = gen.next(null)) != null;) {
                actual.add(num);
            }
            
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