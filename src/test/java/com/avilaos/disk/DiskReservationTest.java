package com.avilaos.disk;

/** Comprobación ejecutable de reservas antes de conectar el ciclo de E/S. */
public class DiskReservationTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        SimulatedDisk disk = new SimulatedDisk(5, 0, 0, 1);
        int first = disk.reserveChainedBlocks("/uno", 3, 10);
        check(first >= 0 && disk.getFreeDataBlocks() == 2, "la reserva reduce el espacio libre");
        check(disk.getBlock(first).isReserved() && !disk.getBlock(first).isFree(), "reserva identificada");
        check(disk.allocateChainedBlocks("/dos", 3, 11) == -1, "otros archivos no usan bloques reservados");
        check(!disk.finishReservation(first, 11, false), "otro proceso no cancela la reserva");
        check(disk.freeChainedBlocks(first) == 0, "liberar archivo no libera una reserva");
        check(disk.finishReservation(first, 10, false), "se cancela la reserva propia");
        check(disk.getFreeDataBlocks() == 5, "cancelar devuelve todo el espacio");
        first = disk.reserveChainedBlocks("/tres", 2, 12);
        int second = disk.getBlock(first).getNextBlock();
        check(disk.finishReservation(first, 12, true), "se confirma la reserva");
        check(!disk.getBlock(first).isReserved() && !disk.getBlock(second).isReserved(), "cadena confirmada");
        check(disk.getFreeDataBlocks() == 3 && disk.freeChainedBlocks(first) == 2, "archivo confirmado puede liberarse");
        check(disk.getFreeDataBlocks() == 5, "espacio recuperado");
        check(!disk.finishReservation(-1, 12, true), "cadena vacía rechazada");
        System.out.println("PASS DiskReservationTest");
    }
}
