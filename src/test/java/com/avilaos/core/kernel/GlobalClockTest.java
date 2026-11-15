package com.avilaos.core.kernel;

import java.util.concurrent.Semaphore;

/**
 * Pruebas unitarias para GlobalClock (Fase 2).
 */
public class GlobalClockTest {
    private static class TickCounter {
        private int value;
        private final Semaphore mutex = new Semaphore(1);
        TickCounter(int initial) { value = initial; }
        int get() {
            mutex.acquireUninterruptibly();
            try { return value; } finally { mutex.release(); }
        }
        void incrementAndGet() {
            mutex.acquireUninterruptibly();
            try { value++; } finally { mutex.release(); }
        }
    }


    public static void main(String[] args) throws InterruptedException {
        testSingletonAndStep();
        testMultipleSubscribersSync();
        testThreadStartPauseResume();
        System.out.println("TODAS LAS PRUEBAS DE GlobalClockTest PASARON EXITOSAMENTE.");
    }

    private static void testSingletonAndStep() {
        GlobalClock clock1 = GlobalClock.getInstance();
        GlobalClock clock2 = GlobalClock.getInstance();
        if (clock1 != clock2) throw new AssertionError("GlobalClock debe ser Singleton");

        clock1.reset();
        if (clock1.getCycleCounter() != 0) throw new AssertionError("cycleCounter debe iniciar en 0");

        TickCounter tickCount = new TickCounter(0);
        IClockTickable subscriber = cycle -> tickCount.incrementAndGet();

        clock1.addSubscriber(subscriber);
        clock1.step();
        clock1.step();

        if (clock1.getCycleCounter() != 2) throw new AssertionError("cycleCounter debe ser 2 tras 2 steps");
        if (tickCount.get() != 2) throw new AssertionError("Suscriptor debio recibir 2 ticks");

        clock1.removeSubscriber(subscriber);
        clock1.step();
        if (tickCount.get() != 2) throw new AssertionError("Suscriptor removido no debe recibir ticks");
        if (clock1.getCycleCounter() != 3) throw new AssertionError("cycleCounter debe ser 3");
    }

    private static void testMultipleSubscribersSync() {
        GlobalClock clock = GlobalClock.getInstance();
        clock.reset();

        TickCounter cpu1Ticks = new TickCounter(0);
        TickCounter cpu2Ticks = new TickCounter(0);

        IClockTickable cpu1 = cycle -> {
            if (cycle != cpu1Ticks.get() + 1) throw new IllegalStateException("Ciclo inconsistente en CPU1");
            cpu1Ticks.incrementAndGet();
        };

        IClockTickable cpu2 = cycle -> {
            if (cycle != cpu2Ticks.get() + 1) throw new IllegalStateException("Ciclo inconsistente en CPU2");
            cpu2Ticks.incrementAndGet();
        };

        clock.addSubscriber(cpu1);
        clock.addSubscriber(cpu2);

        for (int i = 0; i < 5; i++) {
            clock.step();
        }

        if (clock.getCycleCounter() != 5) throw new AssertionError("cycleCounter debe ser 5");
        if (cpu1Ticks.get() != 5 || cpu2Ticks.get() != 5) {
            throw new AssertionError("Ambas CPUs deben avanzar sincronizadas exactamente 5 ciclos");
        }
    }

    private static void testThreadStartPauseResume() throws InterruptedException {
        GlobalClock clock = GlobalClock.getInstance();
        clock.reset();
        clock.setCycleDurationMs(30); // Ciclos rápidos de 30ms para pruebas

        TickCounter ticks = new TickCounter(0);
        clock.addSubscriber(cycle -> ticks.incrementAndGet());

        clock.start();
        Thread.sleep(110); // Debe avanzar ~3 ciclos
        if (ticks.get() < 2) throw new AssertionError("El hilo del reloj debe avanzar en tiempo real");

        clock.pause();
        int ticksAtPause = ticks.get();
        Thread.sleep(100);
        if (ticks.get() != ticksAtPause) {
            throw new AssertionError("El reloj pausado no debe avanzar ciclos");
        }

        clock.resume();
        Thread.sleep(110);
        if (ticks.get() <= ticksAtPause) {
            throw new AssertionError("El reloj reanudado debe continuar avanzando");
        }

        clock.stop();
        clock.reset();
    }
}
