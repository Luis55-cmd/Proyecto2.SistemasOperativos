package com.avilaos.disk;

import com.avilaos.core.model.IOOperationType;
import com.avilaos.core.structures.MyLinkedList;

/**
 * Pruebas unitarias de los Issues #7 y #8 (rama feat/disk-scheduler):
 * - Caso P1 (FIFO), Caso P2 (SSTF), Caso P3 (SCAN), Caso P4 (C-SCAN) del enunciado.
 * - Ejemplo del Anexo (orden de atención y desplazamiento total exacto: 518, 208, 332, 192).
 * - Cambio de política en caliente (0 ticks) sin perder solicitudes en cola.
 * - Políticas opcionales LOOK y C-LOOK.
 */
public class DiskSchedulingPoliciesTest {

    private static final int[] REQUESTS_P1_TO_P4 = {95, 180, 34, 119, 11, 123, 62, 64};
    private static final int[] REQUESTS_ANEXO = {82, 17, 43, 140, 24, 16, 190};

    public static void main(String[] args) {
        testCasoP1Fifo();
        testCasoP2Sstf();
        testCasoP3Scan();
        testCasoP4CScan();
        testAnexoFifo();
        testAnexoSstf();
        testAnexoScan();
        testAnexoCScan();
        testHotPolicySwapZeroTicksAndNoLostRequests();
        testOptionalLookAndCLook();
        System.out.println("TODAS LAS PRUEBAS DE PLANIFICACIÓN DE DISCO (ISSUES #7 Y #8) PASARON.");
    }

    private static void testCasoP1Fifo() {
        int[] expected = {95, 180, 34, 119, 11, 123, 62, 64};
        SimulatedDisk disk = runScenario(DiskSchedulingPolicyType.FIFO, REQUESTS_P1_TO_P4, expected);
        check(disk.getCompletedRequestsCount() == expected.length, "P1 FIFO completados");
    }

    private static void testCasoP2Sstf() {
        int[] expected = {62, 64, 34, 11, 95, 119, 123, 180};
        SimulatedDisk disk = runScenario(DiskSchedulingPolicyType.SSTF, REQUESTS_P1_TO_P4, expected);
        check(disk.getCompletedRequestsCount() == expected.length, "P2 SSTF completados");
    }

    private static void testCasoP3Scan() {
        int[] expected = {62, 64, 95, 119, 123, 180, 34, 11};
        SimulatedDisk disk = runScenario(DiskSchedulingPolicyType.SCAN, REQUESTS_P1_TO_P4, expected);
        check(disk.getCompletedRequestsCount() == expected.length, "P3 SCAN completados");
    }

    private static void testCasoP4CScan() {
        int[] expected = {62, 64, 95, 119, 123, 180, 11, 34};
        SimulatedDisk disk = runScenario(DiskSchedulingPolicyType.C_SCAN, REQUESTS_P1_TO_P4, expected);
        check(disk.getCompletedRequestsCount() == expected.length, "P4 C-SCAN completados");
    }

    private static void testAnexoFifo() {
        int[] expected = {82, 17, 43, 140, 24, 16, 190};
        SimulatedDisk disk = runScenario(DiskSchedulingPolicyType.FIFO, REQUESTS_ANEXO, expected);
        check(disk.getTotalHeadMovement() == 518,
                "Anexo FIFO desplazamiento esperado 518, obtenido " + disk.getTotalHeadMovement());
    }

    private static void testAnexoSstf() {
        int[] expected = {43, 24, 17, 16, 82, 140, 190};
        SimulatedDisk disk = runScenario(DiskSchedulingPolicyType.SSTF, REQUESTS_ANEXO, expected);
        check(disk.getTotalHeadMovement() == 208,
                "Anexo SSTF desplazamiento esperado 208, obtenido " + disk.getTotalHeadMovement());
    }

    private static void testAnexoScan() {
        int[] expected = {82, 140, 190, 43, 24, 17, 16};
        SimulatedDisk disk = runScenario(DiskSchedulingPolicyType.SCAN, REQUESTS_ANEXO, expected);
        check(disk.getTotalHeadMovement() == 332,
                "Anexo SCAN desplazamiento esperado 332, obtenido " + disk.getTotalHeadMovement());
    }

    private static void testAnexoCScan() {
        int[] expected = {82, 140, 190, 16, 17, 24, 43};
        SimulatedDisk disk = runScenario(DiskSchedulingPolicyType.C_SCAN, REQUESTS_ANEXO, expected);
        check(disk.getTotalHeadMovement() == 192,
                "Anexo C-SCAN desplazamiento esperado 192, obtenido " + disk.getTotalHeadMovement());
    }

    private static void testHotPolicySwapZeroTicksAndNoLostRequests() {
        SimulatedDisk disk = new SimulatedDisk(200, 100, 50, 25);
        for (int b : REQUESTS_P1_TO_P4) {
            disk.enqueueRequest(null, IOOperationType.READ, "/f_" + b, b, 1, 0);
        }

        // En FIFO inicialmente, la primera en cola es 95
        check(disk.getQueueSnapshot().get(0).getTargetBlock() == 95, "FIFO inicial: 95 primero");

        // Cambio en caliente a SSTF (0 ticks): reordena la cola instantáneamente a 62, 64, 34...
        disk.setSchedulingPolicy(DiskSchedulingPolicyType.SSTF);
        MyLinkedList<IORequest> snapSstf = disk.getQueueSnapshot();
        check(snapSstf.size() == 8, "no se pierden solicitudes al cambiar a SSTF");
        check(snapSstf.get(0).getTargetBlock() == 62 && snapSstf.get(1).getTargetBlock() == 64,
                "cola reordenada instantáneamente a SSTF en 0 ticks");

        // Atender únicamente la primera solicitud (62) y luego cambiar en caliente a C-SCAN
        MyLinkedList<Integer> served = new MyLinkedList<>();
        disk.setCompletionListener((req, cycle) -> served.append(req.getTargetBlock()));
        int cycle = 1;
        while (served.size() < 1) {
            disk.tick(cycle++);
        }
        check(served.get(0) == 62 && disk.getHeadPosition() == 62, "atendió 62 y cabezal en 62");
        check(disk.getQueueSize() == 7, "quedan 7 solicitudes en cola");

        // Cambio en caliente a C-SCAN con cabezal en 62: debe atender 64, 95, 119, 123, 180, 11, 34
        disk.setSchedulingPolicy(DiskSchedulingPolicyType.C_SCAN);
        MyLinkedList<IORequest> snapCScan = disk.getQueueSnapshot();
        check(snapCScan.size() == 7, "7 solicitudes conservadas al cambiar a C-SCAN");
        check(snapCScan.get(0).getTargetBlock() == 64 && snapCScan.get(6).getTargetBlock() == 34,
                "C-SCAN reordenado instantáneamente desde cabezal 62");

        // Si se vuelve a cambiar a FIFO antes de avanzar el reloj, restaura orden de llegada (95 primero)
        disk.setSchedulingPolicy(DiskSchedulingPolicyType.FIFO);
        check(disk.getQueueSnapshot().get(0).getTargetBlock() == 95,
                "volver a FIFO restaura orden de llegada en 0 ticks");
    }

    private static void testOptionalLookAndCLook() {
        int[] expectedLook = {82, 140, 190, 43, 24, 17, 16};
        SimulatedDisk diskLook = runScenario(DiskSchedulingPolicyType.LOOK, REQUESTS_ANEXO, expectedLook);
        check(diskLook.getTotalHeadMovement() == (190 - 50) + (190 - 16), "LOOK desplazamiento 314");

        int[] expectedCLook = {82, 140, 190, 16, 17, 24, 43};
        SimulatedDisk diskCLook = runScenario(DiskSchedulingPolicyType.C_LOOK, REQUESTS_ANEXO, expectedCLook);
        check(diskCLook.getTotalHeadMovement() == (190 - 50) + (43 - 16), "C-LOOK desplazamiento 167");
    }

    private static SimulatedDisk runScenario(DiskSchedulingPolicyType policyType, int[] requests, int[] expectedOrder) {
        SimulatedDisk disk = new SimulatedDisk(200, 100, 50, 30);
        disk.setSchedulingPolicy(policyType);
        for (int b : requests) {
            disk.enqueueRequest(null, IOOperationType.READ, "/file_" + b, b, 1, 0);
        }

        MyLinkedList<Integer> completed = new MyLinkedList<>();
        disk.setCompletionListener((req, cycle) -> completed.append(req.getTargetBlock()));

        int cycle = 1;
        while (completed.size() < requests.length && cycle < 5000) {
            disk.tick(cycle++);
        }

        check(completed.size() == expectedOrder.length,
                policyType + ": cantidad completada " + completed.size() + " != " + expectedOrder.length);
        for (int i = 0; i < expectedOrder.length; i++) {
            int got = completed.get(i);
            int exp = expectedOrder[i];
            check(got == exp, policyType + " pos " + i + ": esperado " + exp + ", obtenido " + got);
        }
        return disk;
    }

    private static void check(boolean ok, String msg) {
        if (!ok) {
            throw new AssertionError("Fallo: " + msg);
        }
    }
}

