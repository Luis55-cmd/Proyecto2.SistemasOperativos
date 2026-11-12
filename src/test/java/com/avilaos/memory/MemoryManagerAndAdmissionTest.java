package com.avilaos.memory;

import com.avilaos.hardware.Computer;
import com.avilaos.hardware.RAM;
import com.avilaos.core.kernel.ComputerKernel;
import com.avilaos.core.model.ExecutionMode;
import com.avilaos.sync.NetworkSimulator;
import com.avilaos.core.model.ProcessControlBlock;
import com.avilaos.core.model.ProcessState;
import com.avilaos.core.model.ProcessType;
import com.avilaos.sync.ProducerConsumerDriver;
import com.avilaos.core.model.SchedulingPolicyType;
import com.avilaos.core.structures.MyLinkedList;
import com.avilaos.core.structures.MyQueue;

/**
 * Pruebas de verificación para MemoryManager, NetworkSimulator,
 * Admisión a Largo Plazo y cambio a modo SYSTEM.
 */
public class MemoryManagerAndAdmissionTest {

    public static void main(String[] args) {
        testMemoryManagerBasic();
        testLongTermAdmissionAndQueue();
        testProcessTerminationAndMemoryRelease();
        testNetworkSimulatorAndModeChange();
        System.out.println(">>> TODAS LAS PRUEBAS DE MemoryManagerAndAdmissionTest PASARON EXITOSAMENTE. <<<");
    }

    private static void testMemoryManagerBasic() {
        MemoryManager mm = new MemoryManager(1000);
        if (mm.getTotalMemory() != 1000) throw new AssertionError("Total debe ser 1000");
        if (mm.getFreeMemory() != 1000) throw new AssertionError("Libre inicial debe ser 1000");
        if (mm.getUsedMemory() != 0) throw new AssertionError("Usada inicial debe ser 0");

        boolean allocated = mm.allocateMemory(400);
        if (!allocated) throw new AssertionError("Debe permitir alojar 400");
        if (mm.getUsedMemory() != 400) throw new AssertionError("Usada debe ser 400");
        if (mm.getFreeMemory() != 600) throw new AssertionError("Libre debe ser 600");

        boolean overAllocate = mm.allocateMemory(700);
        if (overAllocate) throw new AssertionError("No debe permitir alojar más de la disponible");

        mm.freeMemory(300);
        if (mm.getUsedMemory() != 100) throw new AssertionError("Usada debe ser 100");
        if (mm.getFreeMemory() != 900) throw new AssertionError("Libre debe ser 900");
    }

    private static void testLongTermAdmissionAndQueue() {
        RAM ram = new RAM(500, 0, new int[500]);
        ComputerKernel kernel = new ComputerKernel(
                new MyLinkedList<>(), new MyQueue<>(), new MyQueue<>(),
                new MyQueue<>(), new MyQueue<>(), null
        );
        Computer comp = new Computer(1, null, ram, null, kernel, SchedulingPolicyType.FCFS);

        // Proceso 1 pide 300 MB -> cabe en RAM
        ProcessControlBlock p1 = new ProcessControlBlock("P1", 1, ProcessType.CPU_BOUND, 10, 300, 1, 100);
        boolean p1Admitted = kernel.admitProcess(comp, p1);
        if (!p1Admitted) throw new AssertionError("P1 debe ser admitido");
        if (p1.getState() != ProcessState.LISTO) throw new AssertionError("P1 debe estar LISTO");
        if (kernel.getReadyQueue().size() != 1) throw new AssertionError("readyQueue debe tener 1 proceso");
        if (comp.getMemoryManager().getUsedMemory() != 300) throw new AssertionError("RAM usada debe ser 300");

        // Proceso 2 pide 300 MB -> no cabe (quedan 200) -> va a newQueue
        ProcessControlBlock p2 = new ProcessControlBlock("P2", 1, ProcessType.CPU_BOUND, 10, 300, 1, 100);
        boolean p2Admitted = kernel.admitProcess(comp, p2);
        if (p2Admitted) throw new AssertionError("P2 no debe ser admitido de inmediato");
        if (p2.getState() != ProcessState.NUEVO) throw new AssertionError("P2 debe estar en estado NUEVO");
        if (kernel.getNewQueue().size() != 1) throw new AssertionError("newQueue debe tener 1 proceso esperando");
    }

    private static void testProcessTerminationAndMemoryRelease() {
        RAM ram = new RAM(500, 0, new int[500]);
        ComputerKernel kernel = new ComputerKernel(
                new MyLinkedList<>(), new MyQueue<>(), new MyQueue<>(),
                new MyQueue<>(), new MyQueue<>(), null
        );
        Computer comp = new Computer(1, null, ram, null, kernel, SchedulingPolicyType.FCFS);

        ProcessControlBlock p1 = new ProcessControlBlock("P1", 1, ProcessType.CPU_BOUND, 10, 300, 1, 100);
        ProcessControlBlock p2 = new ProcessControlBlock("P2", 1, ProcessType.CPU_BOUND, 10, 250, 1, 100);

        kernel.admitProcess(comp, p1); // Ocupa 300 MB
        kernel.admitProcess(comp, p2); // Pide 250 MB -> queda en newQueue

        if (kernel.getNewQueue().size() != 1) throw new AssertionError("P2 debe estar esperando en newQueue");

        // Al terminar P1, se debe liberar sus 300 MB y automáticamente admitir a P2
        kernel.terminateProcess(comp, p1);

        if (p1.getAssignedMemory() != 0 || p1.getRequiredMemory() != 300)
            throw new AssertionError("La memoria requerida debe sobrevivir a la liberación");
        if (p1.getState() != ProcessState.SALIENTE) throw new AssertionError("P1 debe estar SALIENTE");
        if (kernel.getEndedQueue().size() != 1) throw new AssertionError("endedQueue debe tener a P1");

        // Verificamos que P2 fue admitido automáticamente por evaluateNewQueueAdmission
        if (kernel.getNewQueue().size() != 0) throw new AssertionError("newQueue debe quedar vacía");
        if (p2.getState() != ProcessState.LISTO) throw new AssertionError("P2 debe haber pasado a LISTO");
        if (kernel.getReadyQueue().size() != 1) throw new AssertionError("readyQueue debe contener a P2");
        if (comp.getMemoryManager().getUsedMemory() != 250) throw new AssertionError("RAM usada debe ser 250");
    }

    private static void testNetworkSimulatorAndModeChange() {
        NetworkSimulator net = new NetworkSimulator(5);
        if (net.getLatencyCycles() != 5) throw new AssertionError("Latencia debe ser 5");

        ProcessControlBlock pcb = new ProcessControlBlock("Prod", 1, ProcessType.PRODUCER, 20, 50, 1, 100);
        if (pcb.getMode() != ExecutionMode.USER) throw new AssertionError("Modo inicial debe ser USER");

        pcb.setMode(ExecutionMode.KERNEL);
        if (pcb.getMode() != ExecutionMode.KERNEL) throw new AssertionError("Debe permitir cambiar a modo KERNEL");
    }
}
