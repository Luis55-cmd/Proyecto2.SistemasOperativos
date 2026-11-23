package com.avilaos.disk;

import com.avilaos.core.model.IOOperationType;
import com.avilaos.core.structures.MyLinkedList;
import com.avilaos.hardware.Computer;
import com.avilaos.simulation.SimulationSnapshot;

/**
 * Pruebas unitarias de las métricas de rendimiento del disco:
 * 1. Desplazamiento total del cabezal.
 * 2. Tiempo promedio de espera de las solicitudes en la cola de E/S.
 * 3. Cantidad de solicitudes atendidas por cada política (FIFO, SSTF, SCAN, C-SCAN, LOOK, C-LOOK).
 * 4. Desplazamiento acumulado por política para comparación.
 * 5. Captura histórica en SimulationSnapshot para graficar el desplazamiento acumulado vs tiempo.
 */
public class DiskPerformanceMetricsTest {

    public static void main(String[] args) {
        testHeadMovementAndRequestsServicedByPolicy();
        testAverageWaitTimeCalculation();
        testPolicyComparisonMovementTracking();
        testChangePolicyWhileServing();
        testSnapshotDiskMetricsCapture();
        testResetMetrics();
        System.out.println("TODAS LAS PRUEBAS DE MÉTRICAS DE RENDIMIENTO DE DISCO PASARON EXITOSAMENTE.");
    }

    private static void testHeadMovementAndRequestsServicedByPolicy() {
        SimulatedDisk disk = new SimulatedDisk(200, 50, 50, 10);
        disk.setSchedulingPolicy(DiskSchedulingPolicyType.FIFO);

        // Encolar solicitudes en ciclo 1
        disk.enqueueRequest(null, IOOperationType.READ, "/file1.txt", 70, 1, 1);
        disk.enqueueRequest(null, IOOperationType.READ, "/file2.txt", 30, 1, 1);

        int cycle = 1;
        while (disk.getCompletedRequestsCount() < 2 && cycle < 100) {
            disk.tick(cycle++);
        }

        check(disk.getCompletedRequestsCount() == 2, "Deben haberse completado 2 solicitudes");
        check(disk.getAtendidasFIFO() == 2, "FIFO debe haber atendido 2 solicitudes, obtenido: " + disk.getAtendidasFIFO());
        check(disk.getAtendidasSSTF() == 0, "SSTF debe ser 0");
        check(disk.getTotalHeadMovement() > 0, "Desplazamiento total debe ser mayor que 0: " + disk.getTotalHeadMovement());
        check(disk.getMovementFIFO() == disk.getTotalHeadMovement(), "El movimiento de FIFO debe coincidir con el total");
    }

    private static void testAverageWaitTimeCalculation() {
        SimulatedDisk disk = new SimulatedDisk(200, 50, 50, 10);
        disk.setSchedulingPolicy(DiskSchedulingPolicyType.FIFO);

        // Solicitud 1 llega en ciclo 1, objetivo 50 (donde ya está el cabezal) -> inicia servicio en ciclo 1 (espera = 0)
        disk.enqueueRequest(null, IOOperationType.READ, "/fileA.txt", 50, 1, 1);
        // Solicitud 2 llega en ciclo 1, pero será atendida después -> espera > 0
        disk.enqueueRequest(null, IOOperationType.READ, "/fileB.txt", 60, 1, 1);

        int cycle = 1;
        while (disk.getCompletedRequestsCount() < 2 && cycle < 100) {
            disk.tick(cycle++);
        }

        check(disk.getCompletedRequestsCount() == 2, "2 solicitudes completadas");
        double avgWait = disk.getAverageWaitTimeCycles();
        check(avgWait == 0.5, "La espera media debe ser 0.5: " + avgWait);
        check(disk.getAverageWaitTimeFIFO() == 0.5, "La espera FIFO debe ser 0.5");
    }

    private static void testChangePolicyWhileServing() {
        SimulatedDisk disk = new SimulatedDisk(200, 50, 50, 10);
        disk.setSchedulingPolicy(DiskSchedulingPolicyType.FIFO);
        disk.enqueueRequest(null, IOOperationType.READ, "/primera", 90, 1, 1);
        disk.enqueueRequest(null, IOOperationType.READ, "/segunda", 100, 1, 1);
        disk.tick(1);
        disk.setSchedulingPolicy(DiskSchedulingPolicyType.SSTF);
        for (int cycle = 2; cycle < 100 && disk.getCompletedRequestsCount() < 2; cycle++) disk.tick(cycle);
        check(disk.getAtendidasFIFO() == 1 && disk.getMovementFIFO() == 40, "La solicitud activa conserva FIFO");
        check(disk.getAtendidasSSTF() == 1 && disk.getMovementSSTF() == 10, "La siguiente solicitud usa SSTF");
        check(disk.getAverageWaitTimeFIFO() == 0.0 && disk.getAverageWaitTimeSSTF() == 5.0,
                "La espera pertenece a la política que seleccionó cada solicitud");
    }

    private static void testPolicyComparisonMovementTracking() {
        SimulatedDisk disk = new SimulatedDisk(200, 50, 50, 10);

        // Fase 1: FIFO
        disk.setSchedulingPolicy(DiskSchedulingPolicyType.FIFO);
        disk.enqueueRequest(null, IOOperationType.READ, "/fifo1.txt", 90, 1, 1);

        int cycle = 1;
        while (disk.getCompletedRequestsCount() < 1 && cycle < 100) {
            disk.tick(cycle++);
        }
        check(disk.getAtendidasFIFO() == 1, "FIFO atendió 1");
        int fifoMov = disk.getMovementFIFO();
        check(fifoMov == 40, "Movimiento FIFO esperado 40, obtenido: " + fifoMov);

        // Fase 2: Cambiar en caliente a SSTF
        disk.setSchedulingPolicy(DiskSchedulingPolicyType.SSTF);
        disk.enqueueRequest(null, IOOperationType.READ, "/sstf1.txt", 100, 1, cycle);

        while (disk.getCompletedRequestsCount() < 2 && cycle < 200) {
            disk.tick(cycle++);
        }
        check(disk.getAtendidasSSTF() == 1, "SSTF atendió 1");
        int sstfMov = disk.getMovementSSTF();
        check(sstfMov == 10, "Movimiento SSTF esperado 10, obtenido: " + sstfMov);

        // Desplazamiento total acumulado = 40 + 10 = 50
        check(disk.getTotalHeadMovement() == 50, "Total acumulado esperado 50, obtenido: " + disk.getTotalHeadMovement());
        check(disk.getMovementByPolicy(DiskSchedulingPolicyType.FIFO) == 40, "getMovementByPolicy FIFO");
        check(disk.getMovementByPolicy(DiskSchedulingPolicyType.SSTF) == 10, "getMovementByPolicy SSTF");
        check(disk.getServicedByPolicy(DiskSchedulingPolicyType.FIFO) == 1, "getServicedByPolicy FIFO");
        check(disk.getServicedByPolicy(DiskSchedulingPolicyType.SSTF) == 1, "getServicedByPolicy SSTF");
        check(disk.getAverageWaitTime(DiskSchedulingPolicyType.FIFO) >= 0.0, "avgWait FIFO >= 0");
        check(disk.getAverageWaitTime(DiskSchedulingPolicyType.SSTF) >= 0.0, "avgWait SSTF >= 0");
        check(disk.getAverageWaitTimeFIFO() >= 0.0, "avgWaitFIFO >= 0");
    }

    private static void testSnapshotDiskMetricsCapture() {
        SimulatedDisk disk = new SimulatedDisk(200, 50, 50, 10);
        disk.setSchedulingPolicy(DiskSchedulingPolicyType.SCAN);
        disk.enqueueRequest(null, IOOperationType.READ, "/scan.txt", 80, 1, 1);

        int cycle = 1;
        while (disk.getCompletedRequestsCount() < 1 && cycle < 100) {
            disk.tick(cycle++);
        }

        MyLinkedList<Computer> computers = new MyLinkedList<>();
        SimulationSnapshot snapshot = new SimulationSnapshot(1L, cycle, true, false, 500, computers, disk, null);

        check(snapshot.disk != null, "Snapshot debe incluir métricas de disco");
        check(snapshot.disk.completedRequestsCount() == 1, "Snapshot solicitudes completadas: " + snapshot.disk.completedRequestsCount());
        check(snapshot.disk.scanServed() == 1, "Snapshot SCAN atendidas: " + snapshot.disk.scanServed());
        check(snapshot.disk.scanMovement() == 30, "Snapshot SCAN movimiento: " + snapshot.disk.scanMovement());
        check(snapshot.disk.totalHeadMovement() == 30, "Snapshot total movimiento: " + snapshot.disk.totalHeadMovement());

        // Verificar que el histórico en samples guarda el diskUsage
        check(snapshot.history.size() == 1, "History debe tener 1 muestra");
        SimulationSnapshot.Sample sample = snapshot.history.get(0);
        check(sample.diskUsage() != null, "La muestra histórica debe contener diskUsage");
        check(sample.diskUsage().totalHeadMovement() == 30, "Histórico head movement debe ser 30");
    }

    private static void testResetMetrics() {
        SimulatedDisk disk = new SimulatedDisk(200, 50, 50, 10);
        disk.enqueueRequest(null, IOOperationType.READ, "/reset.txt", 80, 1, 1);
        int cycle = 1;
        while (disk.getCompletedRequestsCount() < 1 && cycle < 50) {
            disk.tick(cycle++);
        }
        check(disk.getTotalHeadMovement() > 0, "Antes de reset debe haber movimiento");

        disk.reset();
        check(disk.getTotalHeadMovement() == 0, "Desplazamiento total debe resetear a 0");
        check(disk.getCompletedRequestsCount() == 0, "Completadas debe resetear a 0");
        check(disk.getAtendidasFIFO() == 0, "FIFO atendidas debe resetear a 0");
        check(disk.getMovementFIFO() == 0, "FIFO movimiento debe resetear a 0");
        check(disk.getAverageWaitTimeCycles() == 0.0, "Espera promedio debe resetear a 0.0");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FALLÓ: " + message);
        }
    }
}
