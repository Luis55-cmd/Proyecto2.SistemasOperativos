package com.avilaos.simulation;

import com.avilaos.core.model.BlockReason;
import com.avilaos.core.model.IOOperationType;
import com.avilaos.core.model.ProcessControlBlock;
import com.avilaos.core.model.ProcessState;
import com.avilaos.core.model.ProcessType;
import com.avilaos.core.model.SchedulingPolicyType;
import com.avilaos.core.structures.MyLinkedList;
import com.avilaos.disk.IORequest;
import com.avilaos.disk.SimulatedDisk;
import com.avilaos.disk.policies.SSTFDiskPolicy;
import com.avilaos.hardware.Computer;

/**
 * Prueba unitaria e integral que valida:
 * 1. Decisiones del planificador de disco: "El disco atiende la solicitud de P4 en el bloque 87 (SSTF)"
 * 2. Decisiones del planificador a mediano plazo: "Se suspende al proceso P7 para admitir a P9"
 * 3. Ciclo completo de 7 estados creando todos los procesos en Ciclo 0 (flujo real de GUI / JSON):
 *    NUEVO -> LISTO -> EJECUTANDO -> BLOQUEADO -> SUSPENDIDO_BLOQUEADO -> SUSPENDIDO_LISTO -> LISTO -> TERMINADO
 * 4. Selección por menor prioridad (1 = baja antes que 10 = alta) y bloqueo de procesos FILE_IO en disco.
 */
public class DiskAndSwapLogTest {

    public static void main(String[] args) {
        testDiskSchedulerDecisionLog();
        testMidTermSuspensionAndReactivationLogs();
        testRealSimulationCycle0SuspensionAndReactivation();
        testPrioritySelectionAndFileIOBlock();
        System.out.println("ALL LOG AND 7-STATE MID-TERM SCHEDULER TESTS PASSED!");
    }

    private static boolean containsLog(MyLinkedList<String> logs, String fragment) {
        try {
            if (!javax.swing.SwingUtilities.isEventDispatchThread()) {
                javax.swing.SwingUtilities.invokeAndWait(() -> {});
            }
        } catch (Exception ignored) {
        }
        synchronized (logs) {
            for (int i = 0; i < logs.size(); i++) {
                String log = logs.get(i);
                if (log != null && log.contains(fragment)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void testDiskSchedulerDecisionLog() {
        MyLinkedList<String> logs = new MyLinkedList<>();
        SimulatedDisk disk = new SimulatedDisk(200, 100, 50, 10);
        disk.setSchedulingPolicy(new SSTFDiskPolicy());
        disk.setEventListener(log -> { synchronized (logs) { logs.append(log); } });

        ProcessControlBlock p4 = new ProcessControlBlock("P4", 1, ProcessType.FILE_IO, 10, 16, 1, 100);
        IORequest req = new IORequest(1, p4, IOOperationType.READ, "archivo.txt", 87, 1, 0);
        disk.enqueueRequest(req);

        // Al ejecutar un tick, el planificador selecciona la solicitud y emite el evento
        disk.tick(1);

        String expected = "El disco atiende la solicitud de P4 en el bloque 87 (SSTF)";
        if (!containsLog(logs, expected)) {
            throw new AssertionError("Falta evento del planificador de disco. Logs recibidos: " + logs);
        }
        System.out.println("PASS: testDiskSchedulerDecisionLog");
    }

    public static void testMidTermSuspensionAndReactivationLogs() {
        SimulationController controller = SimulationController.getInstance();
        controller.reset();

        MyLinkedList<String> logs = new MyLinkedList<>();
        controller.addLogListener(log -> { synchronized (logs) { logs.append(log); } });

        // Crear computador con RAM limitada (32 unidades)
        Computer comp = controller.createComputer("PC-Test", 32, SchedulingPolicyType.FCFS, 3);

        // Crear proceso P7 que ocupa 24 de RAM (quedan 8 libres)
        ProcessControlBlock p7 = controller.createProcess("P7", comp.getId(), ProcessType.CPU_BOUND, 20, 24, 5, 200);

        // P7 se bloquea por E/S
        comp.getKernel().blockProcess(comp, p7);
        p7.setBlockReason(BlockReason.WAITING_IO);
        p7.setIoCycleCountdown(10);

        // Intentar crear/admitir proceso P9 que requiere 20 de RAM (no cabe en 8 libres)
        // El planificador a mediano plazo debe seleccionar a P7 (bloqueado) y suspenderlo
        ProcessControlBlock p9 = controller.createProcess("P9", comp.getId(), ProcessType.CPU_BOUND, 20, 20, 2, 200);

        String expectedSuspension = "Se suspende al proceso P7 para admitir a P9";
        if (!containsLog(logs, expectedSuspension)) {
            throw new AssertionError("Falta evento de decisión del planificador a mediano plazo: " + expectedSuspension
                    + "\nLogs: " + logs);
        }

        // Avanzar el reloj para que el disco atienda la solicitud SUSPEND (swap-out)
        while (p7.getState() != ProcessState.SUSPENDIDO_BLOQUEADO) {
            controller.stepSimulation();
        }

        if (!containsLog(logs, "Swap-out completado: el proceso P7 pasa a Bloqueado/Suspendido")) {
            throw new AssertionError("Falta log de swap-out completado. Logs: " + logs);
        }

        // P9 debió haber sido admitido automáticamente al liberarse la RAM de P7
        if (p9.getState() != ProcessState.LISTO && p9.getState() != ProcessState.EJECUTANDO) {
            throw new AssertionError("P9 debió ser admitido tras swap-out de P7. Estado: " + p9.getState());
        }

        // El evento de E/S de P7 ocurre (su countdown llega a 0): debe pasar a SUSPENDIDO_LISTO
        p7.setIoCycleCountdown(1);
        controller.stepSimulation();

        if (!containsLog(logs, "Evento completado para P7: pasa de Bloqueado/Suspendido a Listo/Suspendido")) {
            throw new AssertionError("Falta log de transición Bloqueado/Suspendido a Listo/Suspendido. Logs: " + logs);
        }

        // Ahora terminamos P9 para liberar RAM y permitir la reactivación de P7
        comp.getKernel().terminateProcess(comp, p9);

        // Se evalúa reactivación: swap-in de P7
        if (!containsLog(logs, "Se reactiva al proceso P7: iniciando swap-in")) {
            throw new AssertionError("Falta log de reactivación/swap-in. Logs: " + logs);
        }

        // Avanzar reloj hasta completar swap-in
        while (p7.getState() != ProcessState.LISTO && p7.getState() != ProcessState.EJECUTANDO) {
            controller.stepSimulation();
        }

        if (!containsLog(logs, "Swap-in completado: el proceso P7 pasa a Listo")) {
            throw new AssertionError("Falta log de swap-in completado. Logs: " + logs);
        }

        System.out.println("PASS: testMidTermSuspensionAndReactivationLogs");
    }

    /**
     * Simula el flujo real de la GUI: todos los procesos se crean en el Ciclo 0 antes de iniciar el reloj.
     * P1 (IO_BOUND) entra a LISTO ocupando la RAM; P2 entra a NUEVO porque no cabe en RAM.
     * Al avanzar el reloj, P1 ejecuta y se bloquea por E/S -> el planificador a mediano plazo detecta que P2
     * espera en NUEVO y suspende a P1 automáticamente sin intervención manual.
     */
    public static void testRealSimulationCycle0SuspensionAndReactivation() {
        SimulationController controller = SimulationController.getInstance();
        controller.reset();

        Computer comp = controller.createComputer("PC-Real", 20, SchedulingPolicyType.FCFS, 3);

        // Ciclo 0: Crear P1 (IO_BOUND, 15 de RAM, bloqueo cada 1 ciclo por 2 ticks, deadline corto de 20 como el default de GUI)
        ProcessControlBlock p1 = controller.createProcess(
                "P1_IO", comp.getId(), ProcessType.IO_BOUND, 4, 15, 3, 20, 1, 2);
        // Ciclo 0: Crear P2 (CPU_BOUND, 15 de RAM -> no cabe en los 5 libres, queda en NUEVO)
        ProcessControlBlock p2 = controller.createProcess(
                "P2_CPU", comp.getId(), ProcessType.CPU_BOUND, 3, 15, 5, 100);

        if (p1.getState() != ProcessState.LISTO) {
            throw new AssertionError("P1 debe iniciar en LISTO");
        }
        if (p2.getState() != ProcessState.NUEVO) {
            throw new AssertionError("P2 debe iniciar en NUEVO por falta de RAM");
        }

        boolean sawSuspendedBlocked = false;
        boolean sawSuspendedReady = false;
        int guard = 0;
        while ((!p1.isTerminated() || !p2.isTerminated()) && guard < 250) {
            controller.stepSimulation();
            if (p1.getState() == ProcessState.SUSPENDIDO_BLOQUEADO) {
                sawSuspendedBlocked = true;
            }
            if (p1.getState() == ProcessState.SUSPENDIDO_LISTO) {
                sawSuspendedReady = true;
            }
            guard++;
        }

        if (!sawSuspendedBlocked) {
            throw new AssertionError("P1 debió pasar por SUSPENDIDO_BLOQUEADO durante la simulación");
        }
        if (!sawSuspendedReady) {
            throw new AssertionError("P1 debió pasar de SUSPENDIDO_BLOQUEADO a SUSPENDIDO_LISTO al ocurrir su evento");
        }
        if (p1.getState() != ProcessState.TERMINADO || p2.getState() != ProcessState.TERMINADO) {
            throw new AssertionError("Ambos procesos debieron completarse (P1=" + p1.getState() + ", P2=" + p2.getState() + ")");
        }
        if (comp.getSuspensionCount() != 1 || comp.getReactivationCount() != 1) {
            throw new AssertionError("Se esperaba exactamente 1 suspensión y 1 reactivación (obtenido: susp="
                    + comp.getSuspensionCount() + ", react=" + comp.getReactivationCount() + ")");
        }
        if (p1.getSuspensions() != 1 || p1.getReactivations() != 1
                || p2.getSuspensions() != 0 || p2.getReactivations() != 0) {
            throw new AssertionError("Los contadores deben corresponder al proceso suspendido y reactivado");
        }
        System.out.println("PASS: testRealSimulationCycle0SuspensionAndReactivation");
    }

    /**
     * Verifica que:
     * 1. Entre dos procesos bloqueados (prioridad 1 = baja y prioridad 9 = alta), el planificador a mediano plazo
     *    suspende al de menor prioridad (1).
     * 2. Un proceso FILE_IO al ejecutarse en CPU genera solicitud de disco en modo KERNEL y pasa a BLOQUEADO.
     */
    public static void testPrioritySelectionAndFileIOBlock() {
        SimulationController controller = SimulationController.getInstance();
        controller.reset();

        Computer comp = controller.createComputer("PC-Prio", 30, SchedulingPolicyType.FCFS, 3);
        ProcessControlBlock pHigh = controller.createProcess("P_Alta", comp.getId(), ProcessType.FILE_IO, 2, 14, 9, 200);
        pHigh.setIoOperation(IOOperationType.CREATE);
        pHigh.setTargetPath("/archivo_alta.txt");
        pHigh.setFileSizeBlocks(2);

        ProcessControlBlock pLow = controller.createProcess("P_Baja", comp.getId(), ProcessType.FILE_IO, 2, 14, 1, 200);
        pLow.setIoOperation(IOOperationType.CREATE);
        pLow.setTargetPath("/archivo_baja.txt");
        pLow.setFileSizeBlocks(2);

        // Ciclo 1: P_Alta ejecuta su syscall en modo KERNEL y pasa a BLOQUEADO (WAITING_DISK)
        controller.stepSimulation();
        if (pHigh.getState() != ProcessState.BLOQUEADO || pHigh.getBlockReason() != BlockReason.WAITING_DISK) {
            throw new AssertionError("P_Alta (FILE_IO) debió pasar a BLOQUEADO por WAITING_DISK");
        }

        // Ciclo 2: P_Baja ejecuta su syscall en modo KERNEL y pasa a BLOQUEADO (WAITING_DISK)
        controller.stepSimulation();
        if (pLow.getState() != ProcessState.BLOQUEADO || pLow.getBlockReason() != BlockReason.WAITING_DISK) {
            throw new AssertionError("P_Baja (FILE_IO) debió pasar a BLOQUEADO por WAITING_DISK");
        }

        // Ahora llega P_Nuevo que necesita 14 de RAM (solo quedan 2 libres): debe elegir como víctima a P_Baja (prioridad 1 < 9)
        ProcessControlBlock pNew = controller.createProcess("P_Nuevo", comp.getId(), ProcessType.CPU_BOUND, 2, 14, 5, 200);
        if (pNew.getState() != ProcessState.NUEVO) {
            throw new AssertionError("P_Nuevo debe entrar en NUEVO");
        }
        if (pLow.getBlockReason() != BlockReason.SWAPPING_OUT) {
            throw new AssertionError("El planificador a mediano plazo debió elegir a P_Baja (prioridad 1) para swap-out, no a P_Alta");
        }
        if (pHigh.getBlockReason() == BlockReason.SWAPPING_OUT) {
            throw new AssertionError("P_Alta (prioridad 9) NO debió ser suspendido teniendo a P_Baja (prioridad 1)");
        }

        controller.reset();
        System.out.println("PASS: testPrioritySelectionAndFileIOBlock");
    }
}
