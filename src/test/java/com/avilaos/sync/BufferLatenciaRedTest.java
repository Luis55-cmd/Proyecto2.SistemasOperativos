package com.avilaos.sync;

import com.avilaos.core.model.BlockReason;
import com.avilaos.core.model.ProcessControlBlock;
import com.avilaos.core.model.ProcessState;
import com.avilaos.core.model.ProcessType;
import com.avilaos.core.model.SchedulingPolicyType;
import com.avilaos.hardware.Computer;
import com.avilaos.simulation.SimulationController;
import com.avilaos.simulation.SimulationSnapshot;

/**
 * Pruebas unitarias para verificar la latencia de red configurable por buffer,
 * diferenciación entre accesos locales y remotos, y cambio dinámico de latencia
 * en plena ejecución de la simulación.
 */
public class BufferLatenciaRedTest {

    private static void check(boolean condition, String msg) {
        if (!condition) {
            throw new AssertionError("FALLO: " + msg);
        }
    }

    public static void main(String[] args) {
        testBufferLatencyProperty();
        testLocalAccessNoDelay();
        testRemoteAccessWithBufferLatency();
        testBufferSnapshotLatency();
        testChangeBufferLatencyInSimulation();
        System.out.println(">>> TODAS LAS PRUEBAS DE BufferLatenciaRedTest PASARON EXITOSAMENTE. <<<");
    }

    private static void testBufferLatencyProperty() {
        SimulationController controller = SimulationController.getInstance();
        controller.reset();

        Computer host = controller.createComputer("HostNode", 128, SchedulingPolicyType.FCFS);
        Buffer buf = controller.createBuffer(host.getId(), 5, 4); // capacidad 5, latencia 4

        check(buf != null, "Buffer debe ser creado");
        check(buf.getStorage() == 5, "Capacidad debe ser 5");
        check(buf.getLatency() == 4, "Latencia configurada debe ser 4 ticks");
    }

    private static void testLocalAccessNoDelay() {
        SimulationController controller = SimulationController.getInstance();
        controller.reset();

        Computer node1 = controller.createComputer("Node1", 128, SchedulingPolicyType.FCFS);
        Buffer localBuf = controller.createBuffer(node1.getId(), 5, 5); // latencia 5 ticks

        // Proceso productor en el MISMO computador (Acceso Local)
        ProcessControlBlock prodLocal = controller.createProcess(
                "ProdLocal", node1.getId(), ProcessType.PRODUCER, 10, 10, 1, 100,
                String.valueOf(localBuf.getId()), 1, 1
        );
        prodLocal.setSyncPhase(ProcessControlBlock.SyncPhase.WAIT_SLOT);
        prodLocal.setProgramCounter(1);
        prodLocal.setMemoryAddressRegister(1);

        controller.stepSimulation();

        // En acceso local NO debe sufrir retardo de red
        check(prodLocal.getBlockReason() != BlockReason.NETWORK_LATENCY,
                "Acceso local en el mismo host no debe bloquearse por NETWORK_LATENCY");
    }

    private static void testRemoteAccessWithBufferLatency() {
        SimulationController controller = SimulationController.getInstance();
        controller.reset();

        Computer node1 = controller.createComputer("Node1", 128, SchedulingPolicyType.FCFS);
        Computer node2 = controller.createComputer("Node2", 128, SchedulingPolicyType.FCFS);

        final int BUFFER_LATENCY = 3;
        Buffer remoteBuf = controller.createBuffer(node1.getId(), 5, BUFFER_LATENCY);

        // Productor en Nodo 2 accediendo al Buffer en Nodo 1 (Acceso Remoto)
        ProcessControlBlock prodRemote = controller.createProcess(
                "ProdRemote", node2.getId(), ProcessType.PRODUCER, 20, 10, 1, 100,
                String.valueOf(remoteBuf.getId()), 1, 1
        );
        prodRemote.setSyncPhase(ProcessControlBlock.SyncPhase.WAIT_SLOT);
        prodRemote.setProgramCounter(1);
        prodRemote.setMemoryAddressRegister(1);

        // Ciclo 1: Despacha en c2, detecta acceso remoto y se bloquea con demora restante 3
        controller.stepSimulation();
        check(prodRemote.getState() == ProcessState.BLOQUEADO, "Proceso remoto debe bloquearse");
        check(prodRemote.getBlockReason() == BlockReason.NETWORK_LATENCY, "Motivo debe ser NETWORK_LATENCY");
        check(prodRemote.getNetworkDelayRemaining() == BUFFER_LATENCY,
                "Demora restante en ciclo 1 debe ser BUFFER_LATENCY (" + BUFFER_LATENCY + ")");

        // Ciclo 2: Demora pasa de 3 a 2
        controller.stepSimulation();
        check(prodRemote.getState() == ProcessState.BLOQUEADO, "Proceso debe seguir bloqueado en ciclo 2");
        check(prodRemote.getNetworkDelayRemaining() == BUFFER_LATENCY - 1,
                "Demora restante en ciclo 2 debe ser 2");

        // Ciclo 3: Demora pasa de 2 a 1
        controller.stepSimulation();
        check(prodRemote.getState() == ProcessState.BLOQUEADO, "Proceso debe seguir bloqueado en ciclo 3");
        check(prodRemote.getNetworkDelayRemaining() == BUFFER_LATENCY - 2,
                "Demora restante en ciclo 3 debe ser 1");

        // Ciclo 4: Demora llega a 0, vuelve a LISTO y se despacha a CPU en el mismo ciclo
        controller.stepSimulation();
        check(prodRemote.getNetworkDelayRemaining() == 0, "Demora debe ser 0 al finalizar la latencia");
        check(prodRemote.getState() == ProcessState.EJECUTANDO,
                "Proceso debe salir del bloqueo de red y despacharse a CPU");
    }

    private static void testBufferSnapshotLatency() {
        SimulationController controller = SimulationController.getInstance();
        controller.reset();

        Computer host = controller.createComputer("Host", 128, SchedulingPolicyType.FCFS);
        controller.createBuffer(host.getId(), 8, 7);

        SimulationSnapshot snapshot = controller.snapshot();
        check(snapshot.buffers.size() == 1, "Snapshot debe incluir el buffer");
        check(snapshot.buffers.get(0).getLatency() == 7, "BufferView debe exponer latencia = 7");
    }

    private static void testChangeBufferLatencyInSimulation() {
        SimulationController controller = SimulationController.getInstance();
        controller.reset();

        Computer node1 = controller.createComputer("NodeA", 128, SchedulingPolicyType.FCFS);
        Computer node2 = controller.createComputer("NodeB", 128, SchedulingPolicyType.FCFS);

        Buffer buf = controller.createBuffer(node1.getId(), 10, 6); // Inicialmente latencia 6
        check(buf.getLatency() == 6, "Latencia inicial debe ser 6");

        ProcessControlBlock prod = controller.createProcess(
                "ProdLive", node2.getId(), ProcessType.PRODUCER, 20, 10, 1, 100,
                String.valueOf(buf.getId()), 1, 1
        );
        prod.setSyncPhase(ProcessControlBlock.SyncPhase.WAIT_SLOT);
        prod.setProgramCounter(1);
        prod.setMemoryAddressRegister(1);

        // Ciclo 1: inicia acceso remoto con latencia 6
        controller.stepSimulation();
        check(prod.getState() == ProcessState.BLOQUEADO, "Debe bloquearse por red");
        check(prod.getNetworkDelayRemaining() == 6, "Demora restante inicial debe ser 6");

        // Durante la simulación, el usuario cambia la latencia del buffer a 2 ticks
        controller.setBufferLatency(buf.getId(), 2);
        check(buf.getLatency() == 2, "Nueva latencia debe ser 2 ticks");
        check(prod.getNetworkDelayRemaining() == 2, "Demora del proceso en espera debe ajustarse a la nueva latencia");

        // Ciclo 2: avanza la demora reducida (de 2 a 1)
        controller.stepSimulation();
        check(prod.getNetworkDelayRemaining() == 1, "Demora debe ser 1 en ciclo 2");

        // Ciclo 3: se completa la demora (de 1 a 0) y despacha a CPU
        controller.stepSimulation();
        check(prod.getNetworkDelayRemaining() == 0, "Demora debe llegar a 0");
        check(prod.getState() == ProcessState.EJECUTANDO, "Proceso debe estar ejecutando");
    }
}
