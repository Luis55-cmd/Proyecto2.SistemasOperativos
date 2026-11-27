package com.avilaos.disk;

import com.avilaos.core.model.IOOperationType;

/** El área de swap también es parte del recorrido físico del cabezal. */
public class SwapHeadMovementTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        for (DiskSchedulingPolicyType policy : new DiskSchedulingPolicyType[] {
                DiskSchedulingPolicyType.FIFO, DiskSchedulingPolicyType.SSTF,
                DiskSchedulingPolicyType.SCAN, DiskSchedulingPolicyType.C_SCAN }) {
            SimulatedDisk disk = new SimulatedDisk(200, 100, 50, 10);
            disk.setSchedulingPolicy(policy);
            disk.allocateSwapBlocks(1, "relleno", 50);
            check(disk.allocateSwapBlocks(2, "proceso", 1) == 250, "bloque de swap reservado");
            disk.enqueueRequest(null, IOOperationType.SUSPEND, null, 250, 1, 0);
            for (int tick = 1; tick <= 20; tick++) {
                disk.tick(tick);
                check(disk.getHeadPosition() == 50 + tick * 10, policy + ": recorrido al swap tick " + tick);
                check(disk.getCompletedRequestsCount() == 0, policy + ": todavía no transfiere");
            }
            disk.tick(21);
            check(disk.getCompletedRequestsCount() == 1 && disk.getHeadPosition() == 250,
                    policy + ": termina la transferencia en swap");
        }
        for (DiskSchedulingPolicyType policy : new DiskSchedulingPolicyType[] {
                DiskSchedulingPolicyType.SCAN, DiskSchedulingPolicyType.C_SCAN }) {
            SimulatedDisk disk = new SimulatedDisk(200, 100, 250, 10);
            disk.setSchedulingPolicy(policy);
            disk.enqueueRequest(null, IOOperationType.READ, "/archivo", 40, 1, 0);
            for (int tick = 1; tick <= 4; tick++) disk.tick(tick);
            check(disk.getHeadPosition() == 290, policy + ": barre por swap hacia el extremo físico");
            for (int tick = 5; tick < 100 && disk.getCompletedRequestsCount() == 0; tick++) disk.tick(tick);
            check(disk.getCompletedRequestsCount() == 1 && disk.getHeadPosition() == 40,
                    policy + ": completa solicitud tras el barrido");
        }
        SimulatedDisk disk = new SimulatedDisk(200, 100, 50, 10);
        disk.setHeadPosition(299);
        check(disk.getHeadPosition() == 299, "permite ubicar cabezal en el último bloque físico");
        for (IOOperationType operation : new IOOperationType[] {IOOperationType.SUSPEND, IOOperationType.REACTIVATE}) {
            SimulatedDisk fragmented = new SimulatedDisk(200, 10, 200, 1);
            fragmented.allocateSwapBlocks(1, "a", 1);
            int free = fragmented.allocateSwapBlocks(2, "b", 1);
            fragmented.allocateSwapBlocks(3, "c", 1);
            fragmented.freeSwapBlocks(free);
            int start = fragmented.allocateSwapBlocks(4, "proceso", 3);
            check(start == 201, "cadena fragmentada comienza en 201");
            fragmented.setHeadPosition(start);
            fragmented.enqueueRequest(new IORequest(1, null, operation, "swap", start, 3, 0));
            fragmented.tick(1);
            check(fragmented.getHeadPosition() == 201 && fragmented.getCompletedRequestsCount() == 0, "primer bloque");
            fragmented.tick(2);
            check(fragmented.getHeadPosition() == 202 && fragmented.getCompletedRequestsCount() == 0, "recorre hueco ocupado");
            fragmented.tick(3);
            check(fragmented.getHeadPosition() == 203 && fragmented.getCompletedRequestsCount() == 0, "segundo bloque");
            fragmented.tick(4);
            check(fragmented.getHeadPosition() == 204 && fragmented.getCompletedRequestsCount() == 1, "último bloque y fin");
        }
        SimulatedDisk contiguous = new SimulatedDisk(200, 10, 200, 1);
        int start = contiguous.allocateSwapBlocks(1, "contiguo", 3);
        contiguous.enqueueRequest(null, IOOperationType.SUSPEND, "swap", start, 3, 0);
        for (int tick = 1; tick <= 3; tick++) {
            contiguous.tick(tick);
            check(contiguous.getHeadPosition() == 199 + tick, "un bloque contiguo por ciclo");
        }
        check(contiguous.getCompletedRequestsCount() == 1, "tres bloques terminan en tres ciclos");
        System.out.println("PASS SwapHeadMovementTest");
    }
}
