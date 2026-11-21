package com.avilaos.fs;

import com.avilaos.disk.SimulatedDisk;
import com.avilaos.filesystem.FilePermission;
import com.avilaos.filesystem.FileSystemManager;

public class FileSystemSnapshotTest {
    public static void main(String[] args) {
        var fs = new FileSystemManager(new SimulatedDisk(20, 0, 0));
        fs.createDirectory("/docs", "ana", FilePermission.PUBLIC, "SYSTEM");
        fs.createFile("/docs/nota", 2, "ana", FilePermission.PRIVATE, "SYSTEM", 1);
        var raiz = fs.snapshotTree();
        var docs = raiz.findChild("docs");
        var nota = docs.findChild("nota");
        if (nota == null || !"/docs/nota".equals(nota.getPath()) || nota.getSizeBlocks() != 2
                || nota.getPermission() != FilePermission.PRIVATE) throw new AssertionError("Datos de la copia");
        fs.renameNode("/docs/nota", "nueva");
        if (!"nota".equals(nota.getName()) || docs.findChild("nueva") != null)
            throw new AssertionError("La copia no debe cambiar con el árbol vivo");
        System.out.println("PASS FileSystemSnapshotTest");
    }
}
