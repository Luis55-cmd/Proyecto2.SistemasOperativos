package com.avilaos.simulation;

import com.avilaos.core.model.BlockReason;
import com.avilaos.core.model.ProcessState;
import com.avilaos.core.model.ProcessType;
import com.avilaos.core.model.SchedulingPolicyType;
import com.avilaos.filesystem.FilePermission;
import com.avilaos.filesystem.FsNodeType;

/** Cruce real de una creación con presión de RAM, disco y swap. */
public class CreationSwapIntegrationTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    public static void main(String[] args) {
        testCompletionDuringSwapOut();
        testCancellationDuringSwapOut();
        testFailedDuplicateReleasesReservation();
        System.out.println("PASS CreationSwapIntegrationTest");
    }

    private static void testCompletionDuringSwapOut() {
        var c = SimulationController.getInstance();
        c.reset();
        var pc = c.createComputer("PC", 2, SchedulingPolicyType.FCFS);
        int datosAntes = c.getDisk().getFreeDataBlocks();
        int swapAntes = c.getDisk().getFreeSwapBlocks();
        var crear = c.requestCreation("/mezcla", FsNodeType.FILE, 8, "admin", FilePermission.PRIVATE);
        c.stepSimulation();
        check(crear.getReservedStart() >= 0 && crear.getState() == ProcessState.BLOQUEADO, "creación bloqueada con reserva");
        check(c.getFileSystemManager().findNode("/mezcla") == null, "el archivo aún no se publica");
        var otro = c.createProcess("otro", pc.getId(), ProcessType.CPU_BOUND, 2, 2, 5, 1000);
        check(otro != null && otro.getState() == ProcessState.NUEVO, "otro proceso espera RAM");
        check(crear.getBlockReason() == BlockReason.SWAPPING_OUT, "creación seleccionada para suspensión");
        check(c.getDisk().getFreeSwapBlocks() == swapAntes - 2, "se reservó swap del proceso");
        boolean completoDuranteSalida = false;
        boolean fueSuspendido = false;
        for (int i = 0; i < 500 && !crear.isTerminated(); i++) {
            c.stepSimulation();
            if (crear.isCreationCompleted() && crear.getBlockReason() == BlockReason.SWAPPING_OUT) completoDuranteSalida = true;
            if (crear.getState() == ProcessState.SUSPENDIDO_LISTO || crear.getState() == ProcessState.SUSPENDIDO_BLOQUEADO) fueSuspendido = true;
        }
        check(completoDuranteSalida, "la creación termina mientras espera swap-out");
        check(fueSuspendido, "la creación pasó a estado suspendido");
        check(crear.isTerminated() && crear.isCreationCompleted(), "creación reactivada y terminada");
        var nodo = c.getFileSystemManager().findNode("/mezcla");
        check(nodo != null && nodo.getSizeBlocks() == 8 && nodo.getPermission() == FilePermission.PRIVATE, "archivo conservado");
        check(c.getDisk().getFreeDataBlocks() == datosAntes - 8, "solo los bloques del archivo quedan ocupados");
        check(c.getDisk().getFreeSwapBlocks() == swapAntes, "swap recuperado tras reactivación");
        check(pc.getMemoryManager().getUsedMemory() == 0, "RAM recuperada tras terminación");
        c.reset();
    }

    private static void testCancellationDuringSwapOut() {
        var c = SimulationController.getInstance();
        c.reset();
        var pc = c.createComputer("PC", 2, SchedulingPolicyType.FCFS);
        int datosAntes = c.getDisk().getFreeDataBlocks();
        int swapAntes = c.getDisk().getFreeSwapBlocks();
        var crear = c.requestCreation("/cancelado", FsNodeType.FILE, 5, "admin", FilePermission.PUBLIC);
        c.stepSimulation();
        check(crear.getReservedStart() >= 0, "reserva asignada antes de cancelar");
        c.createProcess("otro", pc.getId(), ProcessType.CPU_BOUND, 2, 2, 5, 1000);
        check(crear.getBlockReason() == BlockReason.SWAPPING_OUT, "swap-out en curso");
        pc.getKernel().terminateProcess(pc, crear);
        check(c.getDisk().getFreeDataBlocks() == datosAntes, "cancelación libera reserva de datos");
        check(c.getDisk().getFreeSwapBlocks() == swapAntes, "cancelación libera reserva swap");
        for (int i = 0; i < 100; i++) c.stepSimulation();
        check(c.getFileSystemManager().findNode("/cancelado") == null, "las solicitudes antiguas no crean archivo");
        check(c.getDisk().getFreeDataBlocks() == datosAntes, "sin fugas tras solicitudes antiguas");
        check(c.getDisk().getFreeSwapBlocks() == swapAntes, "sin fugas swap tras solicitudes antiguas");
        c.reset();
    }

    private static void testFailedDuplicateReleasesReservation() {
        var c = SimulationController.getInstance();
        c.reset();
        c.createComputer("PC", 16, SchedulingPolicyType.FCFS);
        int initial = c.getDisk().getFreeDataBlocks();
        var first = c.requestCreation("/duplicado", FsNodeType.FILE, 2, "admin", FilePermission.PUBLIC);
        var second = c.requestCreation("/duplicado", FsNodeType.FILE, 2, "admin", FilePermission.PUBLIC);
        for (int i = 0; i < 200 && !second.isTerminated(); i++) c.stepSimulation();
        check(first.isCreationCompleted() && second.isTerminated() && !second.isCreationCompleted(), "duplicado rechazado");
        check(c.getDisk().getFreeDataBlocks() == initial - 2, "reserva del duplicado liberada");
        c.reset();
    }
}
