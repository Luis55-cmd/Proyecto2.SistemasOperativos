package com.avilaos.disk;

import com.avilaos.core.model.IOOperationType;
import com.avilaos.core.model.ProcessControlBlock;
import com.avilaos.core.model.ProcessType;

/** Comprueba que una lectura recorra la cadena, incluso cuando tiene huecos. */
public class FragmentedReadTest {
    public static void main(String[] args) {
        SimulatedDisk disk = new SimulatedDisk(20, 0, 0, 1);
        disk.allocateChainedBlocks("/ocupado0", 1, 1);
        int hueco = disk.allocateChainedBlocks("/temporal", 1, 2);
        disk.allocateChainedBlocks("/ocupado2", 1, 3);
        disk.freeChainedBlocks(hueco);
        int inicio = disk.allocateChainedBlocks("/fragmentado", 3, 4);
        if (inicio != 1) throw new AssertionError("Inicio inesperado");
        disk.getBlock(1).setNextBlock(4);
        disk.getBlock(4).setNextBlock(3);
        disk.getBlock(3).setNextBlock(DiskBlock.END_OF_CHAIN);
        var proceso = new ProcessControlBlock("lector", 1, ProcessType.FILE_IO, 2, 2, 5, 100);
        disk.setHeadPosition(inicio);
        disk.enqueueRequest(proceso, IOOperationType.READ, "/fragmentado", inicio, 3, 0);
        boolean visitoSegundo = false;
        for (int ciclo = 1; ciclo <= 20 && disk.getCompletedRequestsCount() == 0; ciclo++) {
            disk.tick(ciclo);
            if (disk.getHeadPosition() == 4) visitoSegundo = true;
        }
        if (!visitoSegundo || disk.getCompletedRequestsCount() != 1 || disk.getHeadPosition() != 3)
            throw new AssertionError("La lectura debía recorrer 1 → 4 → 3 y terminar en 3");
        System.out.println("PASS FragmentedReadTest");
    }
}
