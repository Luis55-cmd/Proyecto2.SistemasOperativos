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
        System.out.println("PASS SwapHeadMovementTest");
    }
}
