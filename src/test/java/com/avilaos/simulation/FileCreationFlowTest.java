package com.avilaos.simulation;

import com.avilaos.core.model.SchedulingPolicyType;
import com.avilaos.filesystem.FilePermission;
import com.avilaos.filesystem.FsNodeType;

public class FileCreationFlowTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        var c = SimulationController.getInstance();
        c.reset();
        c.createComputer("PC", 128, SchedulingPolicyType.FCFS);
        int initial = c.getDisk().getFreeDataBlocks();
        var dir = c.requestCreation("/docs", FsNodeType.DIRECTORY, 1, "admin", FilePermission.PUBLIC);
        check(c.getFileSystemManager().findNode("/docs") == null, "directorio aún no creado");
        c.stepSimulation();
        check(dir.getReservedStart() >= 0 && c.getDisk().getBlock(dir.getReservedStart()).isReserved(), "reserva durante la E/S");
        check(c.getFileSystemManager().findNode("/docs") == null, "no publicar antes de finalizar la E/S");
        for (int i = 0; i < 40 && c.getFileSystemManager().findNode("/docs") == null; i++) c.stepSimulation();
        check(c.getFileSystemManager().findNode("/docs") != null, "directorio creado al completar disco");
        check(c.getDisk().getFreeDataBlocks() == initial - 1, "directorio ocupa bloque");
        var file = c.requestCreation("/docs/a.txt", FsNodeType.FILE, 3, "ana", FilePermission.PRIVATE);
        check(c.getFileSystemManager().findNode("/docs/a.txt") == null, "archivo aún no creado");
        for (int i = 0; i < 40 && c.getFileSystemManager().findNode("/docs/a.txt") == null; i++) c.stepSimulation();
        var node = c.getFileSystemManager().findNode("/docs/a.txt");
        check(node != null && node.getSizeBlocks() == 3 && node.getOwner().equals("ana")
                && node.getPermission() == FilePermission.PRIVATE, "archivo con metadatos");
        var chain = c.getDisk().getChainBlocks(node.getFirstBlock());
        check(c.getDisk().getHeadPosition() == chain.get(chain.size() - 1), "cabezal termina en el último bloque escrito");
        check(c.getDisk().getFreeDataBlocks() == initial - 4, "bloques ocupados");
        var first = c.requestCreation("/docs/duplicado", FsNodeType.FILE, 2, "admin", FilePermission.PUBLIC);
        var second = c.requestCreation("/docs/duplicado", FsNodeType.FILE, 2, "admin", FilePermission.PUBLIC);
        for (int i = 0; i < 80 && !second.isTerminated(); i++) c.stepSimulation();
        check(first.isCreationCompleted() && second.isTerminated() && !second.isCreationCompleted(), "duplicado rechazado");
        check(c.getDisk().getFreeDataBlocks() == initial - 6, "reserva fallida liberada");
        c.getFileSystemManager().createDirectory("/mio", "user1", FilePermission.PRIVATE, "test");
        c.setMode(com.avilaos.core.model.UserMode.USER);
        var userFile = c.requestCreation("/mio/propio", FsNodeType.FILE, 1, "otro", FilePermission.PRIVATE);
        c.setMode(com.avilaos.core.model.UserMode.ADMIN);
        for (int i = 0; i < 60 && !userFile.isCreationCompleted() && !userFile.isTerminated(); i++) c.stepSimulation();
        check(userFile.isCreationCompleted() && c.getFileSystemManager().findNode("/mio/propio").getOwner().equals("user1"), "conserva identidad original");
        c.reset();
        c.createComputer("PC", 128, SchedulingPolicyType.FCFS);
        var fragmented = c.getFileSystemManager();
        fragmented.createFileAtStartBlock("/ocupado2", 2, 1, "admin", FilePermission.PUBLIC, "test");
        fragmented.createFileAtStartBlock("/ocupado4", 4, 1, "admin", FilePermission.PUBLIC, "test");
        var p = c.requestCreation("/fragmentado", FsNodeType.FILE, 3, "admin", FilePermission.PUBLIC);
        int previousRemaining = -1;
        for (int i = 0; i < 80 && !p.isCreationCompleted(); i++) {
            c.stepSimulation();
            var active = c.getDisk().getActiveRequest();
            if (active != null && active.getPcb() == p) {
                int remaining = c.getDisk().getRemainingTransferCycles();
                if (previousRemaining >= 0) check(previousRemaining - remaining <= 1, "máximo un bloque transferido por tick");
                previousRemaining = remaining;
            }
        }
        var f = fragmented.findNode("/fragmentado");
        check(f != null, "archivo fragmentado completado");
        var fragmentedChain = c.getDisk().getChainBlocks(f.getFirstBlock());
        check(fragmentedChain.size() == 3 && fragmentedChain.get(0) == 1 && fragmentedChain.get(1) == 3 && fragmentedChain.get(2) == 5, "cadena fragmentada");
        check(c.getDisk().getHeadPosition() == 5, "cabezal termina en último bloque fragmentado");
        c.reset();
        System.out.println("PASS FileCreationFlowTest");
    }
}
