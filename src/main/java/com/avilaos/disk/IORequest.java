package com.avilaos.disk;

import com.avilaos.core.model.IOOperationType;
import com.avilaos.core.model.ProcessControlBlock;

/**
 * Solicitud de E/S en la cola única del disco simulado (Issue #6).
 * Registra el PCB solicitante, tipo de operación, ruta objetivo, bloque
 * objetivo, cantidad de bloques a transferir y ciclo de llegada.
 */
public class IORequest {
    private final int id;
    private final ProcessControlBlock pcb;
    private final IOOperationType operation;
    private final String path;
    private final int targetBlock;
    private final int blockCount;
    private final int arrivalCycle;
    private int startServiceCycle = -1;
    private int completionCycle = -1;

    public IORequest(int id, ProcessControlBlock pcb, IOOperationType operation,
                     String path, int targetBlock, int blockCount, int arrivalCycle) {
        this.id = id;
        this.pcb = pcb;
        this.operation = operation;
        this.path = path;
        this.targetBlock = targetBlock;
        this.blockCount = Math.max(1, blockCount);
        this.arrivalCycle = arrivalCycle;
    }

    public IORequest(int id, ProcessControlBlock pcb, IOOperationType operation,
                     String path, int targetBlock, int arrivalCycle) {
        this(id, pcb, operation, path, targetBlock, 1, arrivalCycle);
    }

    public IORequest(int id, ProcessControlBlock pcb, IOOperationType operation,
                     int targetBlock, String path, int arrivalCycle) {
        this(id, pcb, operation, path, targetBlock, 1, arrivalCycle);
    }

    public int getId() {
        return id;
    }

    public ProcessControlBlock getPcb() {
        return pcb;
    }

    public IOOperationType getOperation() {
        return operation;
    }

    public String getPath() {
        return path;
    }

    public int getTargetBlock() {
        return targetBlock;
    }

    public int getBlockCount() {
        return blockCount;
    }

    public int getArrivalCycle() {
        return arrivalCycle;
    }

    public int getStartServiceCycle() {
        return startServiceCycle;
    }

    public void setStartServiceCycle(int startServiceCycle) {
        this.startServiceCycle = startServiceCycle;
    }

    public int getCompletionCycle() {
        return completionCycle;
    }

    public void setCompletionCycle(int completionCycle) {
        this.completionCycle = completionCycle;
    }

    @Override
    public String toString() {
        String proc = pcb != null ? pcb.getName() : "SYS";
        return "IORequest#" + id + "[" + proc + ", " + operation + ", block=" + targetBlock
                + (path != null ? ", path=" + path : "") + "]";
    }
}

