package com.avilaos.simulation;

import com.avilaos.core.model.SchedulingPolicyType;
import com.avilaos.core.model.UserMode;
import com.avilaos.filesystem.FilePermission;
import com.avilaos.filesystem.FsNodeType;

public class DeleteFlowTest {
    private static void done(SimulationController c, com.avilaos.core.model.ProcessControlBlock p) {
        for (int i = 0; i < 300 && !p.isTerminated(); i++) c.stepSimulation();
        if (!p.isTerminated()) throw new AssertionError("proceso no terminó");
    }
    public static void main(String[] args) {
        var c = SimulationController.getInstance(); c.reset();
        var pc = c.createComputer("PC", 16, SchedulingPolicyType.FCFS);
        int inicial = c.getDisk().getFreeDataBlocks();
        done(c, c.requestCreation("/usuario", FsNodeType.DIRECTORY, 1, "cesar", FilePermission.PUBLIC));
        done(c, c.requestCreation("/usuario/archivo", FsNodeType.FILE, 3, "cesar", FilePermission.PRIVATE));
        c.setUserSession(UserMode.USER, "otro");
        try { c.requestDelete("/usuario/archivo"); throw new AssertionError("borró archivo ajeno"); } catch (IllegalArgumentException expected) { }
        c.setUserSession(UserMode.USER, "cesar");
        try { c.requestDelete("/usuario"); throw new AssertionError("usuario borró directorio"); } catch (IllegalArgumentException expected) { }
        var borrar = c.requestDelete("/usuario/archivo");
        if (c.getFileSystemManager().findNode("/usuario/archivo") == null) throw new AssertionError("se borró antes del disco");
        done(c, borrar);
        if (c.getFileSystemManager().findNode("/usuario/archivo") != null || c.getDisk().getFreeDataBlocks() != inicial - 1)
            throw new AssertionError("no liberó archivo propio");
        c.setUserSession(UserMode.ADMIN, "admin");
        done(c, c.requestCreation("/usuario/sub", FsNodeType.DIRECTORY, 1, "cesar", FilePermission.PUBLIC));
        done(c, c.requestCreation("/usuario/sub/archivo", FsNodeType.FILE, 2, "otro", FilePermission.PUBLIC));
        done(c, c.requestDelete("/usuario"));
        if (c.getFileSystemManager().findNode("/usuario") != null || c.getDisk().getFreeDataBlocks() != inicial
                || pc.getMemoryManager().getUsedMemory() != 0) throw new AssertionError("eliminación recursiva o liberación incorrecta");
        try { c.requestDelete("/"); throw new AssertionError("permitió eliminar raíz"); } catch (IllegalArgumentException expected) { }
        System.out.println("PASS DeleteFlowTest");
    }
}
