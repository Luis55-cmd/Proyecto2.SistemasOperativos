package com.avilaos.fs;

import com.avilaos.disk.SimulatedDisk;
import com.avilaos.filesystem.FilePermission;
import com.avilaos.filesystem.FileSystemManager;
import com.avilaos.io.FileSystemJsonSerializer;

public class DirectoryBlocksTest {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        var disk = new SimulatedDisk(12, 0, 0);
        var fs = new FileSystemManager(disk);
        check(disk.getFreeDataBlocks() == 11 && fs.getTree().getRoot().getFirstBlock() == 0, "raíz");
        var dir = fs.createDirectory("/a", "admin", FilePermission.PUBLIC, "test");
        var sub = fs.createDirectory("/a/b", "admin", FilePermission.PUBLIC, "test");
        fs.createFile("/a/b/x", 2, "admin", FilePermission.PUBLIC, "test", 1);
        check(dir.getSizeBlocks() == 1 && sub.getSizeBlocks() == 1 && disk.getFreeDataBlocks() == 7, "asignación");
        String json = FileSystemJsonSerializer.toJsonString(disk, fs);
        var loaded = FileSystemJsonSerializer.loadFromString(json, 0);
        check(loaded.disk().getFreeDataBlocks() == 7, "contador tras recarga");
        check(loaded.fileSystemManager().findNode("/a/b").getFirstBlock() == sub.getFirstBlock(), "bloque persistente");
        check(loaded.fileSystemManager().deleteNode("/a") == 4, "liberación recursiva");
        check(loaded.disk().getFreeDataBlocks() == 11, "se conserva solo la raíz");
        System.out.println("PASS DirectoryBlocksTest");
    }
}
