package com.avilaos.core.model;

import com.avilaos.hardware.Computer;
import com.avilaos.simulation.SimulationController;

/**
 * Pruebas unitarias para la creación de procesos con memoria configurable
 * y validaciones completas (Requisitos PRO-05, MEM-04, MEM-05, D-12).
 */
public class ProcessCreationValidationTest {

    public static void main(String[] args) {
        testConfigurableMemoryAdmitted();
        testConfigurableMemoryEnqueuedWhenFull();
        testRejectMemoryExceedingTotalRam();
        testRejectInvalidParameters();
        testRejectInvalidProducerConsumer();
        testSenderInitiatedLoadBalancing();
        System.out.println(">>> TODAS LAS PRUEBAS DE ProcessCreationValidationTest PASARON EXITOSAMENTE. <<<");
    }

    private static void testConfigurableMemoryAdmitted() {
        SimulationController controller = SimulationController.getInstance();
        controller.reset();

        // Computador con 128 unidades de RAM
        Computer comp = controller.createComputer("HostA", 128, SchedulingPolicyType.FCFS);

        // Crear proceso con 45 unidades de memoria requerida
        ProcessControlBlock pcb = controller.createProcess("P_45", comp.getId(), ProcessType.CPU_BOUND, 15, 45, 1, 50);

        if (pcb == null) throw new AssertionError("El proceso debe crearse exitosamente");
        if (pcb.getAssignedMemory() != 45) {
            throw new AssertionError("La memoria asignada debe ser 45, obtenida: " + pcb.getAssignedMemory());
        }
        if (pcb.getState() != ProcessState.LISTO) {
            throw new AssertionError("El proceso cabe en RAM libre, debe estar en LISTO");
        }
        if (comp.getMemoryManager().getUsedMemory() != 45) {
            throw new AssertionError("La RAM ocupada debe ser 45, obtenida: " + comp.getMemoryManager().getUsedMemory());
        }
        System.out.println("[PASS] testConfigurableMemoryAdmitted");
    }

    private static void testConfigurableMemoryEnqueuedWhenFull() {
        SimulationController controller = SimulationController.getInstance();
        controller.reset();

        Computer comp = controller.createComputer("HostB", 100, SchedulingPolicyType.FCFS);

        // Proceso 1 ocupa 80 de RAM -> quedan 20 libres
        ProcessControlBlock p1 = controller.createProcess("P1", comp.getId(), ProcessType.CPU_BOUND, 10, 80, 1, 50);
        if (p1.getState() != ProcessState.LISTO) throw new AssertionError("P1 debe estar en LISTO");

        // Proceso 2 pide 40 de RAM (no cabe en las 20 libres, pero cabe en las 100 totales)
        ProcessControlBlock p2 = controller.createProcess("P2", comp.getId(), ProcessType.CPU_BOUND, 10, 40, 1, 50);

        if (p2 == null) throw new AssertionError("P2 debe ser creado y encolado en NUEVOS");
        if (p2.getState() != ProcessState.NUEVO) {
            throw new AssertionError("P2 debe estar en NUEVO esperando liberación de memoria");
        }
        if (comp.getKernel().getNewQueue().size() != 1) {
            throw new AssertionError("La cola de nuevos debe contener 1 proceso");
        }
        System.out.println("[PASS] testConfigurableMemoryEnqueuedWhenFull");
    }

    private static void testRejectMemoryExceedingTotalRam() {
        SimulationController controller = SimulationController.getInstance();
        controller.reset();

        Computer comp = controller.createComputer("HostSmall", 64, SchedulingPolicyType.FCFS);

        // Intentar crear un proceso pidiendo 128 de memoria en un computador de 64
        ProcessControlBlock pcb = controller.createProcess("TooBig", comp.getId(), ProcessType.CPU_BOUND, 10, 128, 1, 50);

        if (pcb != null) {
            throw new AssertionError("Un proceso que pide más memoria que la RAM total debe ser rechazado (retornar null)");
        }
        if (comp.getKernel().getProcessList().size() != 0) {
            throw new AssertionError("No debe haber procesos registrados en el computador");
        }
        if (comp.getKernel().getNewQueue().size() != 0) {
            throw new AssertionError("La cola de nuevos debe estar vacía");
        }
        System.out.println("[PASS] testRejectMemoryExceedingTotalRam");
    }

    private static void testRejectInvalidParameters() {
        SimulationController controller = SimulationController.getInstance();
        controller.reset();

        Computer comp = controller.createComputer("HostVal", 128, SchedulingPolicyType.FCFS);

        // Nombre vacío
        if (controller.createProcess("", comp.getId(), ProcessType.CPU_BOUND, 10, 20, 1, 50) != null) {
            throw new AssertionError("Nombre vacío debe ser rechazado");
        }
        if (controller.createProcess("   ", comp.getId(), ProcessType.CPU_BOUND, 10, 20, 1, 50) != null) {
            throw new AssertionError("Nombre de solo espacios debe ser rechazado");
        }

        // Computador inexistente
        if (controller.createProcess("ValidName", 9999, ProcessType.CPU_BOUND, 10, 20, 1, 50) != null) {
            throw new AssertionError("Computador inexistente debe ser rechazado");
        }

        // Instrucciones <= 0
        if (controller.createProcess("ZeroInst", comp.getId(), ProcessType.CPU_BOUND, 0, 20, 1, 50) != null) {
            throw new AssertionError("Instrucciones 0 deben ser rechazadas");
        }
        if (controller.createProcess("NegInst", comp.getId(), ProcessType.CPU_BOUND, -5, 20, 1, 50) != null) {
            throw new AssertionError("Instrucciones negativas deben ser rechazadas");
        }

        // Memoria <= 0
        if (controller.createProcess("ZeroMem", comp.getId(), ProcessType.CPU_BOUND, 10, 0, 1, 50) != null) {
            throw new AssertionError("Memoria 0 debe ser rechazada");
        }
        if (controller.createProcess("NegMem", comp.getId(), ProcessType.CPU_BOUND, 10, -10, 1, 50) != null) {
            throw new AssertionError("Memoria negativa debe ser rechazada");
        }

        // Prioridad < 1
        if (controller.createProcess("ZeroPrio", comp.getId(), ProcessType.CPU_BOUND, 10, 20, 0, 50) != null) {
            throw new AssertionError("Prioridad 0 debe ser rechazada");
        }

        // Deadline <= 0
        if (controller.createProcess("ZeroDead", comp.getId(), ProcessType.CPU_BOUND, 10, 20, 1, 0) != null) {
            throw new AssertionError("Deadline 0 debe ser rechazado");
        }
        if (controller.createProcess("NegDead", comp.getId(), ProcessType.CPU_BOUND, 10, 20, 1, -5) != null) {
            throw new AssertionError("Deadline negativo debe ser rechazado");
        }

        System.out.println("[PASS] testRejectInvalidParameters");
    }

    private static void testRejectInvalidProducerConsumer() {
        SimulationController controller = SimulationController.getInstance();
        controller.reset();

        Computer comp = controller.createComputer("HostPC", 128, SchedulingPolicyType.FCFS);

        // Productor con ciclos <= 0
        if (controller.createProcess("P_ZeroCycles", comp.getId(), ProcessType.PRODUCER, 10, 20, 1, 50, "1", 0, 5) != null) {
            throw new AssertionError("Productor con ciclos por operación 0 debe ser rechazado");
        }

        // Consumidor con requiredElements <= 0
        if (controller.createProcess("C_ZeroElem", comp.getId(), ProcessType.CONSUMER, 10, 20, 1, 50, "1", 2, 0) != null) {
            throw new AssertionError("Consumidor con elementos requeridos 0 debe ser rechazado");
        }

        System.out.println("[PASS] testRejectInvalidProducerConsumer");
    }

    private static void testSenderInitiatedLoadBalancing() {
        SimulationController controller = SimulationController.getInstance();
        controller.reset();

        Computer compA = controller.createComputer("HostA", 128, SchedulingPolicyType.FCFS);
        Computer compB = controller.createComputer("HostB", 128, SchedulingPolicyType.FCFS);

        // Llenar CompA con 3 procesos para alcanzar el umbral de carga T=3
        controller.createProcess("A1", compA.getId(), ProcessType.CPU_BOUND, 10, 10, 1, 50);
        controller.createProcess("A2", compA.getId(), ProcessType.CPU_BOUND, 10, 10, 1, 50);
        controller.createProcess("A3", compA.getId(), ProcessType.CPU_BOUND, 10, 10, 1, 50);

        // Crear proceso con asignación automática (-1)
        ProcessControlBlock autoProcess = controller.createProcess(
                "AutoProc", -1, ProcessType.CPU_BOUND, 10, 15, 1, 50
        );

        if (autoProcess == null) {
            throw new AssertionError("El proceso automático debe crearse exitosamente");
        }

        // Debe asignarse a HostB porque HostA está en el umbral T=3 y HostB tiene carga 0 < 3
        if (autoProcess.getComputerId() != compB.getId()) {
            throw new AssertionError("Sender-Initiated debio transferir a HostB (ID " + compB.getId() 
                    + ") pero fue a ID " + autoProcess.getComputerId());
        }

        System.out.println("[PASS] testSenderInitiatedLoadBalancing");
    }

    // El reloj independiente del número de computadores se verifica en ControlesRelojGuiTest.
}
