package com.avilaos.simulation;

import com.avilaos.core.model.UserMode;
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
            throw new AssertionError("El cambio sin flag de preservación debe rechazarse");
        } catch (IllegalStateException expected) { }
        if (controller.getDisk() != disk || controller.getFileSystemManager() != fs
                || fs.findNode("/conservar.txt") == null || disk.getFreeDataBlocks() != free)
            throw new AssertionError("El rechazo debe conservar el disco y el archivo");

        // 2. Redimensionamiento seguro con preservación de archivos (como desde el botón Aplicar de la GUI)
        controller.reconfigurarDisco(150, 20, true);
        if (controller.getDisk().getTotalBlocks() != 150 || controller.getDisk().getSwapSizeBlocks() != 20)
            throw new AssertionError("Debió actualizar capacidad a 150 bloques y 20 de swap");
        if (controller.getFileSystemManager().findNode("/conservar.txt") == null
                || !controller.getDisk().getBlock(1).isOccupied()
                || !controller.getDisk().getBlock(2).isOccupied())
            throw new AssertionError("Debió conservar /conservar.txt y sus bloques encadenados");

        // 3. Rechazar reducción por debajo del último bloque ocupado o de la posición del cabezal
        controller.getDisk().setHeadPosition(120);
        try {
            controller.reconfigurarDisco(80, 10, true);
            throw new AssertionError("Debió rechazar si el cabezal queda fuera del nuevo rango");
        } catch (IllegalStateException expected) { }
        controller.getDisk().setHeadPosition(10);

        // 4. Rechazar cambio en modo USER
        controller.setUserSession(UserMode.USER, "user1");
        try {
            controller.reconfigurarDisco(200, 50, true);
            throw new AssertionError("Debió rechazar cambio de capacidad en modo USER");
        } catch (IllegalStateException expected) { }

        controller.reset();
        System.out.println("PASS DiskResizeProtectionTest");
    }
}
