package com.avilaos.core.model;

/** Regresión de inicialización; ejecutar su main con Java. */
public class PcbInitializationTest {
    public static void main(String[] args) {
        ProcessControlBlock previous = new ProcessControlBlock();
        for (ProcessType type : new ProcessType[]{ProcessType.PRODUCER, ProcessType.CONSUMER}) {
            ProcessControlBlock pcb = new ProcessControlBlock(
                    "Prueba", 2, type, 10, 8, 3, 30, "B1", 2, 5, 4);
            if (pcb.getId() <= 0 || pcb.getId() == previous.getId()) {
                throw new AssertionError("IDs positivos y distintos entre constructores");
            }
            if (!"Prueba".equals(pcb.getName()) || pcb.getComputerId() != 2
                    || pcb.getType() != type || pcb.getTotalInstructions() != 10
                    || pcb.getRemainingTime() != 10 || pcb.getAssignedMemory() != 8
                    || pcb.getPriority() != 3 || pcb.getDeadline() != 30
                    || pcb.getRemainingDeadline() != 30 || pcb.getMemoryPointer() != 4
                    || !"B1".equals(pcb.getTargetBufferId())
                    || pcb.getCyclesPerOperation() != 2 || pcb.getRequiredElements() != 5) {
                throw new AssertionError("Atributos incompletos: " + type);
            }
            if (pcb.getState() != ProcessState.NUEVO || pcb.getBlockReason() != BlockReason.NONE
                    || pcb.getStartCycle() != -1 || pcb.getCompletionCycle() != -1
                    || pcb.getArrivalCycle() != 0 || pcb.getProgramCounter() != 0
                    || pcb.getMemoryAddressRegister() != 0 || pcb.getProcessedElements() != 0) {
                throw new AssertionError("Contexto inicial inconsistente: " + type);
            }
            pcb.setAssignedMemory(0); // La terminación libera la memoria asignada.
            if (pcb.getRequiredMemory() != 8 || pcb.getAssignedMemory() != 0) {
                throw new AssertionError("La liberación perdió la memoria requerida: " + type);
            }
            previous = pcb;
        }
        ProcessControlBlock fallback = new ProcessControlBlock(
                " ", 1, null, 10, 8, 3, 30, "B1", 2, 5, 4);
        if (!fallback.getName().equals("Process-" + fallback.getId())
                || fallback.getType() != ProcessType.CONSUMER) {
            throw new AssertionError("Conservar valores por defecto del constructor especial");
        }
        System.out.println("PASS: identidad, atributos y contexto inicial del PCB");
    }
}
