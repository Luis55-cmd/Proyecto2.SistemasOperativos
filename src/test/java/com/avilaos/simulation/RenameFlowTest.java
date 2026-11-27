package com.avilaos.simulation;

import com.avilaos.core.model.SchedulingPolicyType;
import com.avilaos.core.model.UserMode;
import com.avilaos.filesystem.FilePermission;
import com.avilaos.filesystem.FsNodeType;

public class RenameFlowTest {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        var c = SimulationController.getInstance();
        c.reset(); c.createComputer("PC", 16, SchedulingPolicyType.FCFS);
        var dir = c.requestCreation("/fotos", FsNodeType.DIRECTORY, 1, "user1", FilePermission.PUBLIC);
        for (int i = 0; i < 200 && !dir.isTerminated(); i++) c.stepSimulation();
        var file = c.requestCreation("/fotos/playa", FsNodeType.FILE, 3, "user1", FilePermission.PRIVATE);
        for (int i = 0; i < 200 && !file.isTerminated(); i++) c.stepSimulation();
        var original = c.getFileSystemManager().findNode("/fotos/playa");
        check(original != null, "archivo inicial");
        int bloque = original.getFirstBlock(), libres = c.getDisk().getFreeDataBlocks();
        var rename = c.requestRename("/fotos", "viaje");
        check(c.getFileSystemManager().findNode("/fotos/playa") != null, "no renombrar antes de E/S");
        c.setUserSession(UserMode.USER, "user1");
        try { c.requestRename("/fotos/playa", "otro"); throw new AssertionError("USER pudo renombrar"); }
        catch (IllegalArgumentException expected) { }
        for (int i = 0; i < 200 && !rename.isTerminated(); i++) c.stepSimulation();
        var cambiado = c.getFileSystemManager().findNode("/viaje/playa");
        check(cambiado != null && c.getFileSystemManager().findNode("/fotos") == null, "rutas del directorio");
        check(cambiado.getFirstBlock() == bloque && cambiado.getSizeBlocks() == 3
                && cambiado.getPermission() == FilePermission.PRIVATE && cambiado.getOwner().equals("user1"), "conservar archivo");
        check(c.getDisk().getBlock(bloque).getOccupiedBy().equals("/viaje/playa"), "actualizar ruta en disco");
        check(c.getDisk().getFreeDataBlocks() == libres, "no cambia ocupación");
        c.setUserSession(UserMode.ADMIN, "admin");
        var renameFile = c.requestRename("/viaje/playa", "mar");
        for (int i = 0; i < 200 && !renameFile.isTerminated(); i++) c.stepSimulation();
        check(c.getFileSystemManager().findNode("/viaje/mar") != null, "renombrar archivo");
        try { c.requestRename("/viaje/mar", "../invalido"); throw new AssertionError("nombre inválido aceptado"); }
        catch (IllegalArgumentException expected) { }
        var otro = c.requestCreation("/viaje/otro", FsNodeType.FILE, 1, "user1", FilePermission.PUBLIC);
        for (int i = 0; i < 200 && !otro.isTerminated(); i++) c.stepSimulation();
        try { c.requestRename("/viaje/mar", "otro"); throw new AssertionError("duplicado aceptado"); }
        catch (IllegalArgumentException expected) { }
        check(c.getFileSystemManager().findNode("/viaje/mar") != null, "conservar original tras rechazo");
        try { c.requestRename("/", "raiz"); throw new AssertionError("renombró raíz"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("PASS RenameFlowTest");
    }
}
