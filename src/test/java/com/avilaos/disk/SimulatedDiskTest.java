package com.avilaos.disk;

import com.avilaos.core.model.IOOperationType;
import com.avilaos.core.structures.MyLinkedList;

/**
 * Pruebas unitarias de los Issues #4, #5 y #6 (rama feat/disk):
 * - Issue #4: Modelo de bloques, área de swap y protección con Semaphore.
 * - Issue #5: Asignación encadenada, liberación de cadenas y reserva/liberación en Swap.
 * - Issue #6: IORequest, cola única de E/S protegida con Semaphore e IClockTickable.
 */
public class SimulatedDiskTest {

    public static void main(String[] args) throws InterruptedException {
        testIssue4DiskAndSwapConfiguration();
        testIssue5ChainedAllocationAndRelease();
        testIssue5SwapAllocationAndRelease();
        testIssue6ClockTickHeadMovementAndSingleService();
        testSemaphoreConcurrencyOnBlocksAndQueue();
        System.out.println("TODAS LAS PRUEBAS DE SIMULATED DISK (ISSUES #4, #5 Y #6) PASARON.");
    }

    private static void testIssue4DiskAndSwapConfiguration() {
        SimulatedDisk disk = new SimulatedDisk(200, 100, 50, 20);
        check(disk.getTotalBlocks() == 200, "totalBlocks = 200");
        check(disk.getSwapSizeBlocks() == 100, "swapSizeBlocks = 100");
        check(disk.getTotalPhysicalBlocks() == 300, "totalPhysicalBlocks = 300");
        check(disk.getFreeDataBlocks() == 200, "freeDataBlocks iniciales = 200");
        check(disk.getFreeSwapBlocks() == 100, "freeSwapBlocks iniciales = 100");
        check(!disk.getBlock(0).isSwapBlock() && !disk.getBlock(199).isSwapBlock(), "bloques de datos [0..199]");
        check(disk.getBlock(200).isSwapBlock() && disk.getBlock(299).isSwapBlock(), "bloques de swap [200..299]");
        check(disk.getBlock(0).getNextBlock() == DiskBlock.END_OF_CHAIN, "nextBlock libre = -1");
    }

    private static void testIssue5ChainedAllocationAndRelease() {
        SimulatedDisk disk = new SimulatedDisk(100, 50, 0, 10);

        int startA = disk.allocateChainedBlocks("/sistema/boot.bin", 4, 1);
        check(startA == 0, "primer bloque asignado = 0");
        check(disk.getFreeDataBlocks() == 96, "quedan 96 bloques libres");

        MyLinkedList<Integer> chainA = disk.getChainBlocks(startA);
        check(chainA.size() == 4, "cadena de 4 bloques");
        check(disk.getBlock(0).getNextBlock() == 1, "0 -> 1");
        check(disk.getBlock(1).getNextBlock() == 2, "1 -> 2");
        check(disk.getBlock(2).getNextBlock() == 3, "2 -> 3");
        check(disk.getBlock(3).getNextBlock() == -1, "3 -> -1 (fin de archivo)");
        check("/sistema/boot.bin".equals(disk.getBlock(2).getOccupiedBy()), "occupiedBy registrado");

        // Liberar un bloque intermedio y verificar asignación no contigua encadenada
        int startB = disk.allocateChainedBlocksFrom(10, "/usuarios/notas.txt", 3, 2);
        check(startB == 10, "startBlock explicito = 10");
        check(disk.getChainBlocks(startB).size() == 3, "cadena B de 3 bloques");

        // Liberar cadena A y comprobar que un nuevo archivo reutiliza y enlaza bloques libres
        int freedA = disk.freeChainedBlocks(startA);
        check(freedA == 4, "se liberaron 4 bloques de A");
        check(disk.getFreeDataBlocks() == 97, "97 bloques libres tras liberar A");
        check(disk.getBlock(0).isFree() && disk.getBlock(0).getNextBlock() == -1, "bloque 0 libre y nextBlock = -1");

        // Verificar fallo cuando se piden más bloques de los disponibles
        int impossible = disk.allocateChainedBlocks("/enorme.iso", 500, 3);
        check(impossible == -1, "rechaza archivo si no hay bloques suficientes");
    }

    private static void testIssue5SwapAllocationAndRelease() {
        SimulatedDisk disk = new SimulatedDisk(100, 32, 0, 10);

        int swapP1 = disk.allocateSwapBlocks(7, "Proc_7", 16);
        check(swapP1 == 100, "primer bloque de swap = 100");
        check(disk.getFreeSwapBlocks() == 16, "quedan 16 bloques de swap");
        check(disk.getBlock(100).isSwapBlock() && disk.getBlock(100).isOccupied(), "bloque 100 ocupado en swap");
        check(disk.getBlock(100).getOwnerProcessId() == 7, "ownerProcessId = 7");

        // No caben 20 unidades si solo quedan 16 bloques libres en swap
        check(disk.allocateSwapBlocks(8, "Proc_8", 20) == -1, "rechaza swap cuando no alcanza");

        int freed = disk.freeSwapBlocks(swapP1);
        check(freed == 16 && disk.getFreeSwapBlocks() == 32, "libera los 16 bloques de swap");
    }

    private static void testIssue6ClockTickHeadMovementAndSingleService() {
        // Cabezal en 50, velocidad = 25 bloques/tick
        SimulatedDisk disk = new SimulatedDisk(200, 50, 50, 25);
        MyLinkedList<Integer> completedOrder = new MyLinkedList<>();
        disk.setCompletionListener((req, cycle) -> completedOrder.append(req.getTargetBlock()));

        // Solicitud 1: bloque 95 (distancia 45 -> 2 ticks de movimiento: 50->75->95 + 2 bloques de transferencia = 4 ticks)
        // Solicitud 2: bloque 35 (distancia 60 desde 95 -> 3 ticks de movimiento: 95->70->45->35 + 1 bloque = 4 ticks)
        disk.enqueueRequest(null, IOOperationType.READ, "/sistema/config.sys", 95, 2, 0);
        disk.enqueueRequest(null, IOOperationType.UPDATE, "/usuarios/notas.txt", 35, 1, 0);

        // Tick 1: selecciona req#1 (bloque 95), mueve cabezal 50 -> 75 (dir ↑)
        disk.tick(1);
        check(disk.getHeadPosition() == 75, "tick 1: cabezal en 75");
        check(disk.getHeadDirection() == HeadDirection.UP, "tick 1: dirección ↑");
        check(disk.getActiveRequest() != null && disk.getActiveRequest().getTargetBlock() == 95, "atiende 95");
        check(disk.getQueueSize() == 1, "la segunda solicitud espera en cola");

        // Tick 2: mueve cabezal 75 -> 95
        disk.tick(2);
        check(disk.getHeadPosition() == 95, "tick 2: cabezal llega a 95");
        check(completedOrder.isEmpty(), "aún no transfirió bloques");

        // Tick 3: transfiere bloque 1 de 2
        disk.tick(3);
        check(completedOrder.isEmpty(), "tick 3: falta 1 bloque por transferir");

        // Tick 4: transfiere bloque 2 de 2 y completa solicitud 1
        disk.tick(4);
        check(completedOrder.size() == 1 && completedOrder.get(0) == 95, "tick 4: completó solicitud 95");
        check(disk.getActiveRequest() == null, "libre para tomar la siguiente en el próximo tick");

        // Tick 5..8: atiende solicitud hacia 35 (dirección ↓)
        disk.tick(5);
        check(disk.getHeadDirection() == HeadDirection.DOWN, "tick 5: dirección ↓");
        check(disk.getHeadPosition() == 70, "tick 5: cabezal en 70");
        disk.tick(6);
        check(disk.getHeadPosition() == 45, "tick 6: cabezal en 45");
        disk.tick(7);
        check(disk.getHeadPosition() == 35, "tick 7: cabezal en 35");
        disk.tick(8);
        check(completedOrder.size() == 2 && completedOrder.get(1) == 35, "tick 8: completó solicitud 35");
        check(disk.getTotalHeadMovement() == 45 + 60, "desplazamiento total = 105 bloques");
    }

    private static void testSemaphoreConcurrencyOnBlocksAndQueue() throws InterruptedException {
        SimulatedDisk disk = new SimulatedDisk(200, 100, 50, 50);
        Thread[] threads = new Thread[8];
        for (int t = 0; t < threads.length; t++) {
            final int idx = t;
            threads[t] = new Thread(() -> {
                int start = disk.allocateChainedBlocks("/file_" + idx, 5, idx);
                check(start >= 0, "asignación concurrente exitosa");
                disk.enqueueRequest(null, IOOperationType.READ, "/file_" + idx, start, 1, 0);
            });
            threads[t].start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        check(disk.getFreeDataBlocks() == 200 - (8 * 5), "bloques libres consistentes tras concurrencia");
        check(disk.getQueueSize() == 8, "8 solicitudes encoladas concurrentemente");
    }

    private static void check(boolean ok, String msg) {
        if (!ok) {
            throw new AssertionError("Fallo: " + msg);
        }
    }
}

