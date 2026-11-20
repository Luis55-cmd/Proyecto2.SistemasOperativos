package com.avilaos.simulation;

import com.avilaos.filesystem.FilePermission;

public class DiskResizeProtectionTest {
    public static void main(String[] args) {
        var controller = SimulationController.getInstance();
        controller.reset();
        var disk = controller.getDisk();
        var fs = controller.getFileSystemManager();
        fs.createFile("/conservar.txt", 2, "admin", FilePermission.PUBLIC, "test", 1);
        int free = disk.getFreeDataBlocks();
        try {
            controller.reconfigurarDisco(150, 20);
            throw new AssertionError("El cambio debe rechazarse");
        } catch (IllegalStateException expected) { }
        if (controller.getDisk() != disk || controller.getFileSystemManager() != fs
                || fs.findNode("/conservar.txt") == null || disk.getFreeDataBlocks() != free)
            throw new AssertionError("El rechazo debe conservar el disco y el archivo");
        controller.reset();
        System.out.println("PASS DiskResizeProtectionTest");
    }
}
