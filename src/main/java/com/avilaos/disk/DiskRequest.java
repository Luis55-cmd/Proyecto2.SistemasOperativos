package com.avilaos.disk;

import com.avilaos.core.model.FileOperation;
import com.avilaos.core.model.ProcessControlBlock;

/**
 * Solicitud pendiente en la única cola de E/S del disco.
 * Objeto de datos; el estado de la cola lo protege el Disk con su Semaphore.
 */
public class DiskRequest {
    private final int id;
    private final ProcessControlBlock pcb;
    private final DiskRequestType type;
    private final FileOperation operation; // null en SWAP_OUT / SWAP_IN
    private final int targetBlock;         // bloque que posiciona el cabezal
    private final String path;             // ruta del archivo (null en swap)
    private final int arrivalCycle;

    public DiskRequest(int id, ProcessControlBlock pcb, DiskRequestType type, FileOperation operation,
                       int targetBlock, String path, int arrivalCycle) {
        this.id = id;
        this.pcb = pcb;
        this.type = type;
        this.operation = operation;
        this.targetBlock = targetBlock;
        this.path = path;
        this.arrivalCycle = arrivalCycle;
    }

    public int getId() { return id; }
    public ProcessControlBlock getPcb() { return pcb; }
    public DiskRequestType getType() { return type; }
    public FileOperation getOperation() { return operation; }
    public int getTargetBlock() { return targetBlock; }
    public String getPath() { return path; }
    public int getArrivalCycle() { return arrivalCycle; }
}