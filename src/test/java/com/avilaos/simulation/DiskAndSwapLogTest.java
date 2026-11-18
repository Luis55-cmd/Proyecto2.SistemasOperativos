package com.avilaos.simulation;

import com.avilaos.core.model.BlockReason;
import com.avilaos.core.model.IOOperationType;
import com.avilaos.core.model.ProcessControlBlock;
import com.avilaos.core.model.ProcessState;
import com.avilaos.core.model.ProcessType;
import com.avilaos.core.model.SchedulingPolicyType;
import com.avilaos.disk.IORequest;
import com.avilaos.disk.SimulatedDisk;
import com.avilaos.disk.policies.SSTFDiskPolicy;
import com.avilaos.hardware.Computer;

import java.util.ArrayList;
import java.util.List;

/**
 * Prueba unitaria que valida el registro de eventos exigidos en el enunciado:
 * 1. Decisiones del planificador de disco: "El disco atiende la solicitud de P4 en el bloque 87 (SSTF)"
 * 2. Decisiones del planificador a mediano plazo: "Se suspende al proceso P7 para admitir a P9"
 * 3. Reactivaciones y eventos de swap-in / swap-out
 */
public class DiskAndSwapLogTest {

    public static void main(String[] args) {
        testDiskSchedulerDecisionLog();
        testMidTermSuspensionAndReactivationLogs();
        System.out.println("ALL LOG TESTS PASSED!");
    }

    public static void testDiskSchedulerDecisionLog() {
        List<String> logs = new ArrayList<>();
        SimulatedDisk disk = new SimulatedDisk(200, 100, 50, 10);
        disk.setSchedulingPolicy(new SSTFDiskPolicy());
        disk.setEventListener(logs::add);

        ProcessControlBlock p4 = new ProcessControlBlock("P4", 1, ProcessType.FILE_IO, 10, 16, 1, 100);
        IORequest req = new IORequest(1, p4, IOOperationType.READ, "archivo.txt", 87, 1, 0);
        disk.enqueueRequest(req);

        // Al ejecutar un tick, el planificador selecciona la solicitud y emite el evento
        disk.tick(1);

        boolean found = false;
        String expected = "El disco atiende la solicitud de P4 en el bloque 87 (SSTF)";
        for (String log : logs) {
            if (log.contains(expected)) {
                found = true;
                break;
            }
        }
        if (!found) {
            throw new AssertionError("Falta evento del planificador de disco. Logs recibidos: " + logs);
        }
        System.out.println("PASS: testDiskSchedulerDecisionLog");
    }

    public static void testMidTermSuspensionAndReactivationLogs() {
        SimulationController controller = SimulationController.getInstance();
        controller.reset();

        List<String> logs = new ArrayList<>();
        controller.addLogListener(logs::add);

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

        boolean foundSuspensionDecision = false;
        String expectedSuspension = "Se suspende al proceso P7 para admitir a P9";
        for (String log : logs) {
            if (log.contains(expectedSuspension)) {
                foundSuspensionDecision = true;
                break;
            }
        }
        if (!foundSuspensionDecision) {
            throw new AssertionError("Falta evento de decisión del planificador a mediano plazo: " + expectedSuspension
                    + "\nLogs: " + logs);
        }

        // Avanzar el reloj para que el disco atienda la solicitud SUSPEND (swap-out)
        while (p7.getState() != ProcessState.SUSPENDIDO_BLOQUEADO) {
            controller.stepSimulation();
        }

        boolean foundSwapOutCompleted = false;
        for (String log : logs) {
            if (log.contains("Swap-out completado: el proceso P7 pasa a Bloqueado/Suspendido")) {
                foundSwapOutCompleted = true;
                break;
            }
        }
        if (!foundSwapOutCompleted) {
            throw new AssertionError("Falta log de swap-out completado. Logs: " + logs);
        }

        // P9 debió haber sido admitido automáticamente al liberarse la RAM de P7
        if (p9.getState() != ProcessState.LISTO && p9.getState() != ProcessState.EJECUTANDO) {
            throw new AssertionError("P9 debió ser admitido tras swap-out de P7. Estado: " + p9.getState());
        }

        // El evento de E/S de P7 ocurre (su countdown llega a 0): debe pasar a SUSPENDIDO_LISTO
        p7.setIoCycleCountdown(1);
        controller.stepSimulation();

        boolean foundTransicionListoSusp = false;
        for (String log : logs) {
            if (log.contains("Evento completado para P7: pasa de Bloqueado/Suspendido a Listo/Suspendido")) {
                foundTransicionListoSusp = true;
                break;
            }
        }
        if (!foundTransicionListoSusp) {
            throw new AssertionError("Falta log de transición Bloqueado/Suspendido a Listo/Suspendido. Logs: " + logs);
        }

        // Ahora terminamos P9 para liberar RAM y permitir la reactivación de P7
        comp.getKernel().terminateProcess(comp, p9);

        // Se evalúa reactivación: swap-in de P7
        boolean foundReactivation = false;
        for (String log : logs) {
            if (log.contains("Se reactiva al proceso P7: iniciando swap-in")) {
                foundReactivation = true;
                break;
            }
        }
        if (!foundReactivation) {
            throw new AssertionError("Falta log de reactivación/swap-in. Logs: " + logs);
        }

        // Avanzar reloj hasta completar swap-in
        while (p7.getState() != ProcessState.LISTO && p7.getState() != ProcessState.EJECUTANDO) {
            controller.stepSimulation();
        }

        boolean foundSwapInCompleted = false;
        for (String log : logs) {
            if (log.contains("Swap-in completado: el proceso P7 pasa a Listo")) {
                foundSwapInCompleted = true;
                break;
            }
        }
        if (!foundSwapInCompleted) {
            throw new AssertionError("Falta log de swap-in completado. Logs: " + logs);
        }

        System.out.println("PASS: testMidTermSuspensionAndReactivationLogs");
    }
}
