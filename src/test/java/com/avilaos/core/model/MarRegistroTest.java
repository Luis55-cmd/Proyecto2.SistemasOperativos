package com.avilaos.core.model;

import com.avilaos.simulation.SimulationController;

/** El MAR recuerda la instrucción ejecutada y sobrevive a cambios de contexto. */
public class MarRegistroTest {
    private static void check(boolean ok, String mensaje) { if (!ok) throw new AssertionError(mensaje); }
    public static void main(String[] args) {
        var controller = SimulationController.getInstance();
        controller.reset();
        var pc = controller.createComputer("RR", 128, SchedulingPolicyType.ROUND_ROBIN, 1);
        var a = controller.createProcess("A", pc.getId(), ProcessType.CPU_BOUND, 3, 3, 1, 100);
        var b = controller.createProcess("B", pc.getId(), ProcessType.CPU_BOUND, 3, 3, 1, 100);
        controller.stepSimulation();
        check(a.getProgramCounter() == 1 && a.getMemoryAddressRegister() == 0, "A ejecuta dirección 0");
        controller.stepSimulation();
        check(b.getProgramCounter() == 1 && b.getMemoryAddressRegister() == 0, "B ejecuta dirección 0");
        check(a.getProgramCounter() == 1 && a.getMemoryAddressRegister() == 0, "A conserva contexto desalojado");
        controller.stepSimulation();
        check(a.getProgramCounter() == 2 && a.getMemoryAddressRegister() == 1, "A continúa en dirección 1");
        controller.reset();
        pc = controller.createComputer("IO", 128, SchedulingPolicyType.FCFS);
        var io = controller.createConfiguredProcess("IO", pc.getId(), ProcessType.IO_BOUND, 4, 1, 100, 2, 2, null, 1, 1);
        controller.stepSimulation(); controller.stepSimulation(); controller.stepSimulation();
        check(io.getState() == ProcessState.BLOQUEADO && io.getBlockReason() == BlockReason.WAITING_IO,
                "Solicitud de E/S bloquea");
        check(io.getProgramCounter() == 3 && io.getMemoryAddressRegister() == 2,
                "Solicitud ejecutada en dirección 2");
        controller.reset();
        System.out.println("PASS MarRegistroTest");
    }
}
