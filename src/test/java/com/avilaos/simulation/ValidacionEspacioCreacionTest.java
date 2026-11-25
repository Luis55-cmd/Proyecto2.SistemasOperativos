package com.avilaos.simulation;

import com.avilaos.core.model.SchedulingPolicyType;
import com.avilaos.filesystem.FilePermission;
import com.avilaos.filesystem.FsNodeType;

/** Rechaza solicitudes que ya exceden el espacio disponible, sin crear procesos. */
public class ValidacionEspacioCreacionTest {
    public static void main(String[] args) {
        var c = SimulationController.getInstance();
        c.reset();
        try {
            c.getDisk().setHeadPosition(0);
            c.reconfigurarDisco(10, 0, true);
            var pc = c.createComputer("PC", 32, SchedulingPolicyType.FCFS);
            int libres = c.getDisk().getFreeDataBlocks();
            if (libres != 9) throw new AssertionError("La raíz debe ocupar un bloque");
            rechazar(c, "/grande", 10);
            if (c.getAllProcesses().size() != 0 || c.getDisk().getFreeDataBlocks() != libres
                    || pc.getMemoryManager().getUsedMemory() != 0)
                throw new AssertionError("El rechazo creó un proceso o alteró recursos");
            var permitido = c.requestCreation("/lleno", FsNodeType.FILE, libres, "admin", FilePermission.PUBLIC);
            for (int i = 0; i < 100 && !permitido.isTerminated(); i++) c.stepSimulation();
            if (!permitido.isCreationCompleted() || c.getDisk().getFreeDataBlocks() != 0)
                throw new AssertionError("Se debe permitir usar exactamente el espacio libre");
            rechazar(c, "/sin-espacio", 1);
            try {
                c.requestCreation("/directorio", FsNodeType.DIRECTORY, 1, "admin", FilePermission.PUBLIC);
                throw new AssertionError("El directorio necesita un bloque libre");
            } catch (IllegalArgumentException esperado) {
                if (!esperado.getMessage().contains("Espacio insuficiente")) throw esperado;
            }
            c.reset();
            c.getDisk().setHeadPosition(0);
            c.reconfigurarDisco(10, 0, true);
            c.createComputer("PC", 32, SchedulingPolicyType.FCFS);
            var primero = c.requestCreation("/uno", FsNodeType.FILE, 6, "admin", FilePermission.PUBLIC);
            var segundo = c.requestCreation("/dos", FsNodeType.FILE, 6, "admin", FilePermission.PUBLIC);
            for (int i = 0; i < 200 && (!primero.isTerminated() || !segundo.isTerminated()); i++) c.stepSimulation();
            if (!primero.isCreationCompleted() || !segundo.isTerminated() || segundo.isCreationCompleted()
                    || c.getFileSystemManager().findNode("/dos") != null || c.getDisk().getFreeDataBlocks() != 3)
                throw new AssertionError("La segunda reserva debe fallar sin alterar el primer archivo");
            System.out.println("PASS: rechaza sin proceso, admite límite exacto y protege raíz y directorios");
        } finally { c.reset(); }
    }
    private static void rechazar(SimulationController c, String path, int blocks) {
        try {
            c.requestCreation(path, FsNodeType.FILE, blocks, "admin", FilePermission.PUBLIC);
            throw new AssertionError("Se aceptó un archivo que no cabe");
        } catch (IllegalArgumentException esperado) {
            if (!esperado.getMessage().contains("Espacio insuficiente")) throw esperado;
        }
    }
}
