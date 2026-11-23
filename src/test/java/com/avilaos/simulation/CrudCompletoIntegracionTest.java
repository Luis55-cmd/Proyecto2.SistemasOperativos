package com.avilaos.simulation;

import com.avilaos.core.model.ProcessControlBlock;
import com.avilaos.core.model.SchedulingPolicyType;
import com.avilaos.filesystem.FilePermission;
import com.avilaos.filesystem.FsNodeType;

/** Un recorrido completo del controlador: cada operación pasa por el disco. */
public class CrudCompletoIntegracionTest {
    private static void terminar(SimulationController c, ProcessControlBlock p, int esperadas) {
        for (int i = 0; i < 500 && !p.isTerminated(); i++) c.stepSimulation();
        if (!p.isTerminated() || !p.isDiskRequestIssued()
                || c.getDisk().getCompletedRequestsCount() != esperadas)
            throw new AssertionError("Operación no completada por E/S: " + p.getName());
    }
    public static void main(String[] args) {
        var c = SimulationController.getInstance();
        c.reset();
        try {
            var pc = c.createComputer("PC", 32, SchedulingPolicyType.FCFS);
            int libres = c.getDisk().getFreeDataBlocks();
            var dir = c.requestCreation("/documentos", FsNodeType.DIRECTORY, 1, "ana", FilePermission.PUBLIC);
            if (c.getFileSystemManager().findNode("/documentos") != null) throw new AssertionError("Creó antes de E/S");
            terminar(c, dir, 1);
            var archivo = c.requestCreation("/documentos/nota", FsNodeType.FILE, 3, "ana", FilePermission.PRIVATE);
            terminar(c, archivo, 2);
            var nodo = c.getFileSystemManager().findNode("/documentos/nota");
            if (nodo == null || nodo.getSizeBlocks() != 3 || c.getDisk().getFreeDataBlocks() != libres - 4)
                throw new AssertionError("Creación incoherente");
            int bloque = nodo.getFirstBlock();
            terminar(c, c.requestRead("/documentos/nota"), 3);
            terminar(c, c.requestRename("/documentos/nota", "final"), 4);
            var renombrado = c.getFileSystemManager().findNode("/documentos/final");
            if (renombrado == null || renombrado.getFirstBlock() != bloque
                    || c.getFileSystemManager().findNode("/documentos/nota") != null)
                throw new AssertionError("Renombrado incoherente");
            terminar(c, c.requestDelete("/documentos/final"), 5);
            terminar(c, c.requestDelete("/documentos"), 6);
            if (c.getFileSystemManager().findNode("/documentos") != null
                    || c.getDisk().getFreeDataBlocks() != libres || pc.getMemoryManager().getUsedMemory() != 0)
                throw new AssertionError("No se liberaron archivos, bloques y RAM");
            System.out.println("PASS: crear, leer, renombrar y eliminar mediante seis solicitudes de E/S");
        } finally { c.reset(); }
    }
}
