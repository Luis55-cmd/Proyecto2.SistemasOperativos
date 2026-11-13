package com.avilaos.core.model;

import java.util.concurrent.Semaphore;

public class PcbIdConcurrencyTest {
    public static void main(String[] args) throws InterruptedException {
        ProcessControlBlock.resetIdGenerator(1);
        int[][] ids = new int[8][2000];
        Thread[] workers = new Thread[ids.length];
        Semaphore start = new Semaphore(0);
        for (int i = 0; i < workers.length; i++) {
            final int row = i;
            workers[i] = new Thread(() -> {
                start.acquireUninterruptibly();
                for (int j = 0; j < ids[row].length; j++) ids[row][j] = new ProcessControlBlock().getId();
            });
            workers[i].start();
        }
        start.release(workers.length);
        for (Thread worker : workers) worker.join(10000);
        boolean[] seen = new boolean[16001];
        for (int[] row : ids) for (int id : row) {
            if (id < 1 || id > 16000 || seen[id]) throw new AssertionError("UNICIDAD: ID repetido o inválido " + id);
            seen[id] = true;
        }
        if (new ProcessControlBlock().getId() != 16001) throw new AssertionError("CONTINUIDAD: siguiente ID");
        ProcessControlBlock.resetIdGenerator(40000);
        if (new ProcessControlBlock().getId() != 40000) throw new AssertionError("RESTAURACION: respeta siguiente ID");
        try {
            ProcessControlBlock.resetIdGenerator(0);
            throw new AssertionError("INVALIDO: reinicio debe ser positivo");
        } catch (IllegalArgumentException expected) { }
        if (new ProcessControlBlock().getId() != 40001) throw new AssertionError("ERROR: no altera contador");
        System.out.println("PASS PcbIdConcurrencyTest: 16000 IDs en 8 hilos, reinicio y validación");
    }
}
