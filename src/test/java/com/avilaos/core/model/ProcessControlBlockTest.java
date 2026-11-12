package com.avilaos.core.model;

/**
 * Pruebas unitarias para ProcessControlBlock (Fase 2).
 */
public class ProcessControlBlockTest {

    public static void main(String[] args) {
        testIdGeneration();
        testRunningExecution();
        testDeadlineExpiration();
        testProducerConsumerProgress();
        System.out.println("TODAS LAS PRUEBAS DE ProcessControlBlockTest PASARON EXITOSAMENTE.");
    }

    private static void testIdGeneration() {
        ProcessControlBlock.resetIdGenerator(1);
        ProcessControlBlock pcb1 = new ProcessControlBlock();
        ProcessControlBlock pcb2 = new ProcessControlBlock();
        ProcessControlBlock pcb3 = new ProcessControlBlock("MiProceso", 2, ProcessType.IO_BOUND, 20, 512, 3, 50);

        if (pcb1.getId() != 1) throw new AssertionError("ID de pcb1 debe ser 1");
        if (pcb2.getId() != 2) throw new AssertionError("ID de pcb2 debe ser 2");
        if (pcb3.getId() != 3) throw new AssertionError("ID de pcb3 debe ser 3");
        if (!"MiProceso".equals(pcb3.getName())) throw new AssertionError("Nombre incorrecto");
        if (pcb3.getComputerId() != 2) throw new AssertionError("ComputerId incorrecto");
        if (pcb3.getPriority() != 3) throw new AssertionError("Prioridad incorrecta");
        if (pcb3.getAssignedMemory() != 512) throw new AssertionError("Memoria incorrecta");
    }

    private static void testRunningExecution() {
        ProcessControlBlock pcb = new ProcessControlBlock("CpuTask", 1, ProcessType.CPU_BOUND, 3, 100, 1, 10);
        pcb.setState(ProcessState.EJECUTANDO);

        if (pcb.getProgramCounter() != 0) throw new AssertionError("PC inicial debe ser 0");
        if (pcb.getMemoryAddressRegister() != 0) throw new AssertionError("MAR inicial debe ser 0");

        // Ciclo 1
        pcb.executeOneCycle(1);
        if (pcb.getProgramCounter() != 1) throw new AssertionError("PC debe ser 1");
        if (pcb.getMemoryAddressRegister() != 0) throw new AssertionError("MAR debe ser 0: dirección de la primera instrucción");
        if (pcb.getRemainingTime() != 2) throw new AssertionError("remainingTime debe ser 2");
        if (pcb.getState() != ProcessState.EJECUTANDO) throw new AssertionError("Estado debe seguir siendo RUNNING");
        if (pcb.getStartCycle() != 1) throw new AssertionError("startCycle debe ser 1");

        // Ciclo 2
        pcb.executeOneCycle(2);
        if (pcb.getProgramCounter() != 2) throw new AssertionError("PC debe ser 2");
        if (pcb.getRemainingTime() != 1) throw new AssertionError("remainingTime debe ser 1");

        // Ciclo 3 (termina instrucciones)
        pcb.executeOneCycle(3);
        if (pcb.getRemainingTime() != 0) throw new AssertionError("remainingTime debe ser 0");
        if (pcb.getState() != ProcessState.SALIENTE) throw new AssertionError("Estado debe ser TERMINATED");
        if (pcb.getCompletionCycle() != 3) throw new AssertionError("completionCycle debe ser 3");
    }

    private static void testDeadlineExpiration() {
        ProcessControlBlock pcb = new ProcessControlBlock("DeadTask", 1, ProcessType.CPU_BOUND, 10, 100, 1, 2);
        pcb.setState(ProcessState.LISTO);

        // Ciclo 1: remainingDeadline pasa de 2 a 1
        pcb.updateDeadlineOnTick(1);
        if (pcb.getRemainingDeadline() != 1) throw new AssertionError("remainingDeadline debe ser 1");
        if (pcb.getState() != ProcessState.LISTO) throw new AssertionError("Debe seguir READY");

        // Ciclo 2: remainingDeadline llega a 0 -> transición inmediata a TERMINATED
        pcb.updateDeadlineOnTick(2);
        if (pcb.getRemainingDeadline() != 0) throw new AssertionError("remainingDeadline debe ser 0");
        if (pcb.getState() != ProcessState.SALIENTE) throw new AssertionError("Debe transicionar a TERMINATED");
        if (pcb.getCompletionCycle() != 2) throw new AssertionError("completionCycle debe ser 2");
    }

    private static void testProducerConsumerProgress() {
        ProcessControlBlock producer = new ProcessControlBlock("Prod1", 1, ProcessType.PRODUCER,
                5, 200, 2, 20, "BUF-0", 2, 3);

        if (!"BUF-0".equals(producer.getTargetBufferId())) throw new AssertionError("Buffer ID incorrecto");
        if (producer.getRequiredElements() != 3) throw new AssertionError("Elementos requeridos incorrectos");

        producer.registerElementProcessed(1);
        producer.registerElementProcessed(2);
        if (producer.getState() == ProcessState.SALIENTE) throw new AssertionError("Aun no debe terminar");

        producer.registerElementProcessed(3);
        if (producer.getState() != ProcessState.SALIENTE) throw new AssertionError("Debe terminar al cumplir cuota");
    }
}
