package com.avilaos.simulation;

import com.avilaos.core.model.*;
import com.avilaos.core.structures.MyQueue;
import com.avilaos.hardware.Computer;
import com.avilaos.sync.Buffer;

/** Escenarios deterministas: cada nombre puede ejecutarse por separado para mutación. */
public class RecorridosIntegracionTest {
    private static final SimulationController c = SimulationController.getInstance();

    private static void check(boolean ok, String mensaje) {
        if (!ok) throw new AssertionError(mensaje);
    }
    private static void ticks(int cantidad) {
        for (int i = 0; i < cantidad; i++) c.stepSimulation();
    }
    private static Computer equipo(String nombre) {
        return c.createComputer(nombre, 128, SchedulingPolicyType.FCFS);
    }
    private static ProcessControlBlock cpu(Computer pc, String nombre, int tiempo, int memoria, int prioridad) {
        return c.createProcess(nombre, pc.getId(), ProcessType.CPU_BOUND, tiempo, memoria, prioridad, 200);
    }
    private static ProcessControlBlock sync(Computer pc, Buffer b, String nombre, ProcessType tipo) {
        return c.createProcess(nombre, pc.getId(), tipo, 20, 10, 1, 200,
                String.valueOf(b.getId()), 1, 1);
    }
    // Rota exactamente el tamaño original para inspeccionar identidad y conservar el orden.
    private static int ocurrencias(MyQueue<ProcessControlBlock> cola, ProcessControlBlock pcb) {
        int n = cola.size(), encontrados = 0;
        for (int i = 0; i < n; i++) {
            ProcessControlBlock actual = cola.dequeue();
            if (actual == pcb) encontrados++;
            cola.enqueue(actual);
        }
        return encontrados;
    }
    private static void terminadoUnaVez(Computer pc, ProcessControlBlock p) {
        check(p.getState() == ProcessState.SALIENTE, "Proceso no termino: " + p.getName());
        check(ocurrencias(pc.getKernel().getEndedQueue(), p) == 1, "Terminado ausente o duplicado");
        check(ocurrencias(pc.getKernel().getReadyQueue(), p) == 0, "Terminado sigue listo");
        check(ocurrencias(pc.getKernel().getBlockedQueue(), p) == 0, "Terminado sigue bloqueado");
        check(pc.getCpu().getPCB() != p, "Terminado sigue en CPU");
    }
    static void admision() {
        Computer pc = equipo("Memoria");
        ProcessControlBlock a = cpu(pc, "A", 2, 80, 1);
        ProcessControlBlock b = cpu(pc, "B", 3, 64, 1);
        check(a.getState() == ProcessState.LISTO, "A debe ser admitido");
        check(b.getState() == ProcessState.NUEVO, "ADMISION: B no cabe y debe esperar");
        check(pc.getMemoryManager().getUsedMemory() == 80 && pc.getRam().getUsedSize() == 80,
                "ADMISION: esperar no debe reservar RAM");
        check(ocurrencias(pc.getKernel().getNewQueue(), b) == 1, "B debe estar una vez en nuevos");
        check(ocurrencias(pc.getKernel().getReadyQueue(), b) == 0, "B no debe estar listo");
    }
    static void readmision() {
        Computer pc = equipo("Memoria");
        ProcessControlBlock a = cpu(pc, "A", 2, 80, 1);
        ProcessControlBlock b = cpu(pc, "B", 3, 64, 1);
        ticks(2);
        terminadoUnaVez(pc, a);
        check(b.getState() == ProcessState.LISTO, "READMISION: B debe admitirse al terminar A");
        check(pc.getKernel().getNewQueue().isEmpty(), "Nuevo no retirado");
        check(pc.getMemoryManager().getUsedMemory() == 64, "READMISION: liberar A y reservar B");
        pc.getKernel().terminateProcess(pc, a);
        check(pc.getMemoryManager().getUsedMemory() == 64, "Doble liberacion");
        ticks(3);
        terminadoUnaVez(pc, b);
        check(pc.getMemoryManager().getUsedMemory() == 0 && pc.getRam().getUsedSize() == 0, "Fuga RAM final");
    }
    static void consumidor() {
        Computer pc = equipo("Local");
        Buffer b = c.createBuffer(pc.getId(), 1, 1);
        ProcessControlBlock consumidor = sync(pc, b, "C", ProcessType.CONSUMER);
        ticks(1);
        check(consumidor.getBlockReason() == BlockReason.WAITING_EMPTY_BUFFER, "Consumidor debe esperar datos");
        check(pc.getCpu().getPCB() == null && pc.getKernel().getBlockedQueue().size() == 1, "Bloqueo debe liberar CPU");
        ProcessControlBlock productor = sync(pc, b, "P", ProcessType.PRODUCER);
        ticks(12);
        check(b.getComsumed() == 1, "CONSUMIDOR: debe despertar y consumir exactamente uno");
        check(b.getProduced() == 1 && b.getDatos().size() == 0, "Balance local");
        terminadoUnaVez(pc, consumidor); terminadoUnaVez(pc, productor);
        check(b.getMutex().availablePermits() == 1 && b.getVacios().availablePermits() == 1
                && b.getLlenos().availablePermits() == 0, "Permisos finales vacio");
    }
    static void productor() {
        Computer pc = equipo("Local");
        Buffer b = c.createBuffer(pc.getId(), 1, 1);
        ProcessControlBlock primero = sync(pc, b, "P1", ProcessType.PRODUCER);
        ticks(6);
        ProcessControlBlock segundo = sync(pc, b, "P2", ProcessType.PRODUCER);
        ticks(2); // Trabajo de usuario y wait(vacios)
        check(segundo.getBlockReason() == BlockReason.WAITING_FULL_BUFFER, "Productor debe esperar espacio");
        check(pc.getCpu().getPCB() == null && b.getDatos().size() == 1, "Lleno no debe sobreescribirse");
        ProcessControlBlock consumidor = sync(pc, b, "C", ProcessType.CONSUMER);
        ticks(12);
        check(b.getProduced() == 2, "PRODUCTOR: debe despertar y producir exactamente el segundo elemento");
        check(b.getComsumed() == 1 && b.getDatos().size() == 1, "Balance lleno");
        terminadoUnaVez(pc, primero); terminadoUnaVez(pc, segundo); terminadoUnaVez(pc, consumidor);
        check(b.getMutex().availablePermits() == 1 && b.getVacios().availablePermits() == 0
                && b.getLlenos().availablePermits() == 1, "Permisos finales lleno");
    }
    static void remoto() {
        Computer host = equipo("Host"), remoto = equipo("Remoto");
        c.setNetworkLatency(2);
        Buffer b = c.createBuffer(host.getId(), 2, 2);
        ProcessControlBlock p = sync(remoto, b, "P remoto", ProcessType.PRODUCER);
        ticks(2); // Trabajo de usuario antes de solicitar acceso remoto
        check(p.getState() == ProcessState.BLOQUEADO && p.getBlockReason() == BlockReason.NETWORK_LATENCY
                && p.getNetworkDelayRemaining() == 2, "RED: debe bloquearse dos ciclos");
        ticks(1);
        check(p.getNetworkDelayRemaining() == 1, "RED: debe descontar un ciclo de latencia");
        check(b.getProduced() == 0 && remoto.getCpu().getPCB() == null, "No operar durante latencia");
        ticks(1);
        check(p.getNetworkDelayRemaining() == 0 && remoto.getCpu().getPCB() == p, "RED: debe volver al CPU");
        ticks(12);
        check(b.getProduced() == 1 && b.getDatos().size() == 1 && p.getProcessedElements() == 1, "Operacion remota duplicada o perdida");
        terminadoUnaVez(remoto, p);
        check(p.getComputerId() == remoto.getId(), "Proceso no debe migrar");
        check(host.getMemoryManager().getUsedMemory() == 2 && remoto.getMemoryManager().getUsedMemory() == 0, "RAM por anfitrion");
    }
    static void politica() {
        Computer pc = equipo("Politica");
        ProcessControlBlock a = cpu(pc, "A", 6, 10, 1), b = cpu(pc, "B", 3, 10, 10);
        ticks(1);
        check(pc.getCpu().getPCB() == a, "FCFS debe elegir A");
        pc.setPolitica(SchedulingPolicyType.PRIORITY_PREEMPTIVE);
        ticks(1);
        check(pc.getCpu().getPCB() == b, "POLITICA: cambio debe despachar B de mayor prioridad");
        check(a.getRemainingTime() == 5 && b.getRemainingTime() == 2, "Solo una ejecucion por tick");
        check(ocurrencias(pc.getKernel().getReadyQueue(), a) == 1, "A perdido o duplicado tras desalojo");
        pc.setPolitica(SchedulingPolicyType.FCFS);
        ticks(1);
        check(pc.getCpu().getPCB() == b && b.getRemainingTime() == 1, "FCFS debe conservar proceso activo");
        ticks(6);
        terminadoUnaVez(pc, a); terminadoUnaVez(pc, b);
        check(a.getProgramCounter() == 6 && b.getProgramCounter() == 3, "Contexto perdido o ejecucion duplicada");
        check(pc.getMemoryManager().getUsedMemory() == 0, "RAM final tras cambios");
    }
    public static void main(String[] args) {
        String[] casos = args.length == 0 ? new String[]{"admision", "readmision", "consumidor", "productor", "remoto", "politica"} : args;
        try {
            for (String caso : casos) {
                c.reset(); c.setNetworkLatency(0);
                switch (caso) {
                    case "admision" -> admision();
                    case "readmision" -> readmision();
                    case "consumidor" -> consumidor();
                    case "productor" -> productor();
                    case "remoto" -> remoto();
                    case "politica" -> politica();
                    default -> throw new IllegalArgumentException("Caso desconocido: " + caso);
                }
                System.out.println("PASS " + caso);
            }
        } finally { c.reset(); }
    }
}
