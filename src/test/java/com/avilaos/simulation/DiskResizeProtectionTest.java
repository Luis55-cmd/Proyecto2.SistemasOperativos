package com.avilaos.simulation;

import com.avilaos.core.model.UserMode;
import com.avilaos.filesystem.FilePermission;
import com.avilaos.core.model.IOOperationType;
import com.avilaos.disk.DiskSchedulingPolicyType;

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

        // Mantener el total físico y ampliar swap reduce el área de datos.
        var antes = controller.getDisk();
        antes.allocateChainedBlocksFrom(125, "/lejano", 1, 1);
        try {
            controller.reconfigurarDisco(120, 50, true);
            throw new AssertionError("Ampliar swap no debe desplazar un bloque de datos ocupado");
        } catch (IllegalStateException expected) {
            if (!expected.getMessage().contains("125")) throw expected;
        }
        if (controller.getDisk() != antes || !antes.getBlock(125).isOccupied())
            throw new AssertionError("El rechazo debe conservar el bloque ocupado");

        int inicioSwap = antes.allocateSwapBlocks(999, "suspendido", 1);
        if (inicioSwap < 0) throw new AssertionError("No se reservó swap");
        try {
            controller.reconfigurarDisco(150, 10, true);
            throw new AssertionError("No debe reducirse swap con procesos suspendidos");
        } catch (IllegalStateException expected) { }
        if (controller.getDisk() != antes || antes.getFreeSwapBlocks() != 19)
            throw new AssertionError("El rechazo debe conservar swap ocupado");
        antes.freeSwapBlocks(inicioSwap);

        antes.enqueueRequest(null, IOOperationType.READ, "/conservar.txt", 30, 1, 0);
        for (int ciclo = 1; ciclo <= 20 && antes.getCompletedRequestsCount() == 0; ciclo++) antes.tick(ciclo);
        int movimiento = antes.getTotalHeadMovement();
        int solicitudes = antes.getCompletedRequestsCount();
        int espera = antes.getTotalWaitTime(DiskSchedulingPolicyType.FIFO);
        if (solicitudes != 1 || movimiento == 0) throw new AssertionError("No se generaron métricas de prueba");
        controller.startSimulation();
        controller.pauseSimulation();

        controller.reconfigurarDisco(160, 20, true);
        var despues = controller.getDisk();
        if (despues.getCompletedRequestsCount() != solicitudes || despues.getTotalHeadMovement() != movimiento
                || despues.getTotalWaitTime(DiskSchedulingPolicyType.FIFO) != espera
                || despues.getServicedByPolicy(DiskSchedulingPolicyType.FIFO) != 1)
            throw new AssertionError("El cambio seguro debe conservar las métricas");
        controller.startSimulation();
        try {
            controller.reconfigurarDisco(170, 20, true);
            throw new AssertionError("No debe redimensionar con el reloj avanzando");
        } catch (IllegalStateException expected) {
            if (!expected.getMessage().contains("Pausa")) throw expected;
        }
        controller.pauseSimulation();

        // Una lectura activa y otra en cola sobreviven al cambio y terminan.
        var discoConLecturas = controller.getDisk();
        var activa = discoConLecturas.enqueueRequest(null, IOOperationType.READ, "/a", 60, 1, 40);
        var enCola = discoConLecturas.enqueueRequest(null, IOOperationType.READ, "/b", 70, 1, 40);
        discoConLecturas.tick(41);
        if (discoConLecturas.getActiveRequest() != activa) throw new AssertionError("Falta lectura activa");
        controller.reconfigurarDisco(170, 20, true);
        var trasladado = controller.getDisk();
        if (trasladado.getActiveRequest() != activa || trasladado.getQueueSize() != 1
                || trasladado.getQueueSnapshot().get(0) != enCola)
            throw new AssertionError("Se perdió una lectura durante el cambio");
        for (int ciclo = 42; ciclo < 100 && trasladado.getCompletedRequestsCount() < 3; ciclo++) trasladado.tick(ciclo);
        if (activa.getCompletionCycle() < 0 || enCola.getCompletionCycle() < 0)
            throw new AssertionError("Las lecturas trasladadas no terminaron");

        var fuera = trasladado.enqueueRequest(null, IOOperationType.READ, "/fuera", 165, 1, 100);
        try {
            controller.reconfigurarDisco(150, 20, true);
            throw new AssertionError("Se aceptó una lectura fuera del área de datos nueva");
        } catch (IllegalStateException expected) { }
        if (controller.getDisk() != trasladado || trasladado.getQueueSnapshot().get(0) != fuera)
            throw new AssertionError("El rechazo alteró la lectura pendiente");

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
