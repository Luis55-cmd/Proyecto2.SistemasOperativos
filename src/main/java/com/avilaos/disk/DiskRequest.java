package com.avilaos.disk;

import com.avilaos.core.model.IOOperationType;
import com.avilaos.core.model.ProcessControlBlock;

/**
 * Alias compatible de {@link IORequest} conservado para compatibilidad con Issue #2.
 */
public class DiskRequest extends IORequest {

    public DiskRequest(int id, ProcessControlBlock pcb, IOOperationType operation,
                       int targetBlock, String path, int arrivalCycle) {
        super(id, pcb, operation, path, targetBlock, 1, arrivalCycle);
    }

    public DiskRequest(int id, ProcessControlBlock pcb, IOOperationType operation,
                       String path, int targetBlock, int blockCount, int arrivalCycle) {
        super(id, pcb, operation, path, targetBlock, blockCount, arrivalCycle);
    }
}
