package com.avilaos.core.scheduler;

import com.avilaos.core.model.ProcessControlBlock;
import com.avilaos.core.model.ProcessType;
import com.avilaos.core.model.SchedulingPolicyType;
import com.avilaos.hardware.Computer;
import com.avilaos.simulation.SimulationController;

/**
 * Pruebas unitarias para la configuración y cambio en caliente del quantum
 * de Round Robin por computador (Requisitos PLA-03, PLA-04, CFG-02, D-10).
 */
public class ComputerQuantumTest {

    public static void main(String[] args) {
        testDefaultQuantum();
        testCustomQuantumPerComputer();
        testDynamicQuantumChangeInFlight();
        System.out.println(">>> TODAS LAS PRUEBAS DE ComputerQuantumTest PASARON EXITOSAMENTE. <<<");
    }

    private static void testDefaultQuantum() {
        SimulationController controller = SimulationController.getInstance();
        controller.reset();

        Computer comp = controller.createComputer(128, SchedulingPolicyType.ROUND_ROBIN);
        if (comp.getQuantum() != 3) {
            throw new AssertionError("El quantum por defecto debe ser 3, obtenido: " + comp.getQuantum());
        }

        comp.setQuantum(5);
        if (comp.getQuantum() != 5) {
            throw new AssertionError("El quantum modificado debe ser 5, obtenido: " + comp.getQuantum());
        }
        System.out.println("[PASS] testDefaultQuantum");
    }

    private static void testCustomQuantumPerComputer() {
        SimulationController controller = SimulationController.getInstance();
        controller.reset();

        // Computador 1 con quantum 2
        Computer c1 = controller.createComputer("Comp1", 128, SchedulingPolicyType.ROUND_ROBIN, 2);
        // Computador 2 con quantum 4
        Computer c2 = controller.createComputer("Comp2", 128, SchedulingPolicyType.ROUND_ROBIN, 4);

        if (c1.getQuantum() != 2) throw new AssertionError("c1 debe tener quantum 2");
        if (c2.getQuantum() != 4) throw new AssertionError("c2 debe tener quantum 4");

        // Cargar procesos en c1
        ProcessControlBlock p1A = controller.createProcess("P1A", c1.getId(), ProcessType.CPU_BOUND, 10, 10, 1, 100);
        ProcessControlBlock p1B = controller.createProcess("P1B", c1.getId(), ProcessType.CPU_BOUND, 10, 10, 1, 100);

        // Cargar procesos en c2
        ProcessControlBlock p2A = controller.createProcess("P2A", c2.getId(), ProcessType.CPU_BOUND, 10, 10, 1, 100);
        ProcessControlBlock p2B = controller.createProcess("P2B", c2.getId(), ProcessType.CPU_BOUND, 10, 10, 1, 100);

        // Ejecutar 2 ticks
        controller.stepSimulation();
        controller.stepSimulation();

        // En c1 (quantum 2): P1A corrió 2 ticks -> restantes 8
        if (p1A.getRemainingTime() != 8) throw new AssertionError("P1A debe tener 8 restantes, actual: " + p1A.getRemainingTime());

        // En c2 (quantum 4): P2A corrió 2 ticks -> restantes 8
        if (p2A.getRemainingTime() != 8) throw new AssertionError("P2A debe tener 8 restantes, actual: " + p2A.getRemainingTime());

        // Tick 3: En c1, quantum expiró -> debe desalojar y correr P1B
        // En c2, quantum es 4 -> P2A sigue corriendo en su 3er tick
        controller.stepSimulation();

        if (p1B.getRemainingTime() != 9) throw new AssertionError("P1B en c1 debió correr en tick 3, restantes: " + p1B.getRemainingTime());
        if (p2A.getRemainingTime() != 7) throw new AssertionError("P2A en c2 debió continuar corriendo en tick 3, restantes: " + p2A.getRemainingTime());

        System.out.println("[PASS] testCustomQuantumPerComputer");
    }

    private static void testDynamicQuantumChangeInFlight() {
        SimulationController controller = SimulationController.getInstance();
        controller.reset();

        // Inicia con quantum 5
        Computer comp = controller.createComputer("CompDyn", 128, SchedulingPolicyType.ROUND_ROBIN, 5);
        ProcessControlBlock p1 = controller.createProcess("Proc1", comp.getId(), ProcessType.CPU_BOUND, 20, 10, 1, 100);
        ProcessControlBlock p2 = controller.createProcess("Proc2", comp.getId(), ProcessType.CPU_BOUND, 20, 10, 1, 100);

        // Tick 1: P1 corre (restantes 19)
        controller.stepSimulation();
        if (p1.getRemainingTime() != 19) throw new AssertionError("P1 debe tener 19 restantes");

        // Cambio en caliente de quantum de 5 a 2:
        controller.setComputerQuantum(comp.getId(), 2);
        if (comp.getQuantum() != 2) throw new AssertionError("Quantum debe ser 2 tras setComputerQuantum");

        // Tick 2: P1 corre su segundo tick (restantes 18), consumiendo el nuevo quantum de 2
        controller.stepSimulation();
        if (p1.getRemainingTime() != 18) throw new AssertionError("P1 debe tener 18 restantes tras tick 2");

        // Tick 3: Con quantum 2 ya agotado, se desaloja P1 y corre P2
        controller.stepSimulation();
        if (p2.getRemainingTime() != 19) throw new AssertionError("P2 debió ser despachado en tick 3 tras agotarse el nuevo quantum");

        System.out.println("[PASS] testDynamicQuantumChangeInFlight");
    }
}
