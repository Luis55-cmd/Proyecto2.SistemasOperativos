package com.avilaos.simulation;

import com.avilaos.core.model.BlockReason;
import com.avilaos.core.model.ProcessControlBlock;
import com.avilaos.core.model.ProcessState;
import com.avilaos.core.model.ProcessType;
import com.avilaos.core.model.SchedulingPolicyType;
import com.avilaos.hardware.Computer;
import com.avilaos.sync.Buffer;

/**
 * Pruebas unitarias y de integración para SimulationController (Capa 3).
 */
public class SimulationControllerTest {

    public static void main(String[] args) {
        testClusterCreationAndProcess();
        testSimulationTickExecution();
        testBufferCreation();
        testNetworkDelayAdvancement();
        testBufferBlockedProcessesWakeup();
        testProducerSingleTerminationAndIdempotency();
        testRoundRobinPreemptionAndDispatch();
        testDeadlinePermitLeakFix();
        System.out.println(">>> TODAS LAS PRUEBAS DE SimulationControllerTest PASARON EXITOSAMENTE. <<<");
    }

    private static void testClusterCreationAndProcess() {
        SimulationController controller = SimulationController.getInstance();
        controller.reset();

        Computer comp1 = controller.createComputer(1024, SchedulingPolicyType.FCFS);
        Computer comp2 = controller.createComputer(512, SchedulingPolicyType.ROUND_ROBIN);

        if (controller.getComputers().size() != 2) throw new AssertionError("Deben haber 2 computadores");
        if (comp1.getId() != 1) throw new AssertionError("comp1 debe tener id 1");
        if (comp2.getId() != 2) throw new AssertionError("comp2 debe tener id 2");

        ProcessControlBlock p1 = controller.createProcess("TestProc", 1, ProcessType.CPU_BOUND, 5, 200, 1, 50);
        if (p1 == null) throw new AssertionError("El proceso debe crearse");
        if (p1.getState() != ProcessState.LISTO) throw new AssertionError("p1 debe estar en estado LISTO");
    }

    private static void testSimulationTickExecution() {
        SimulationController controller = SimulationController.getInstance();
        controller.reset();

        Computer comp = controller.createComputer(1024, SchedulingPolicyType.FCFS);
        ProcessControlBlock p = controller.createProcess("CpuProc", comp.getId(), ProcessType.CPU_BOUND, 3, 100, 1, 50);

        // Ciclo 1: Despacha a CPU y ejecuta 1 instrucción
        controller.stepSimulation();
        if (comp.getCpu().getPCB() == null) throw new AssertionError("Debe haber proceso en CPU");
        if (comp.getCpu().getPCB().getId() != p.getId()) throw new AssertionError("El proceso en CPU debe ser p");
        if (p.getRemainingTime() != 2) throw new AssertionError("Debe restar 1 instruccion (quedan 2)");

        // Ciclo 2
        controller.stepSimulation();
        if (p.getRemainingTime() != 1) throw new AssertionError("Queda 1 instruccion");

        // Ciclo 3: Termina el proceso
        controller.stepSimulation();
        if (p.getState() != ProcessState.SALIENTE) throw new AssertionError("El proceso debe pasar a SALIENTE");
        if (comp.getKernel().getEndedQueue().size() != 1) throw new AssertionError("endedQueue debe tener al proceso terminado");
    }

    private static void testBufferCreation() {
        SimulationController controller = SimulationController.getInstance();
        controller.reset();

        // Caso 1: Creación normal
        Computer comp = controller.createComputer(1024, SchedulingPolicyType.FCFS);
        Buffer buf = controller.createBuffer(comp.getId(), 10);

        if (buf == null) throw new AssertionError("El buffer debe crearse");
        if (controller.getBuffers().size() != 1) throw new AssertionError("Debe haber 1 buffer registrado");
        if (buf.getIdComp() != comp.getId()) throw new AssertionError("El buffer anfitrion debe coincidir");

        // Caso 2: Reproducción - RAM insuficiente (RAM 32, buffer 64)
        controller.reset();
        Computer compSmall = controller.createComputer(32, SchedulingPolicyType.FCFS);
        Buffer bufOver = controller.createBuffer(compSmall.getId(), 64);
        if (bufOver != null) throw new AssertionError("Buffer con capacidad mayor a RAM debe fallar retornando null");
        if (controller.getBuffers().size() != 0) throw new AssertionError("No se debe registrar el buffer fallido");
        if (compSmall.getMemoryManager().getUsedMemory() != 0) throw new AssertionError("La RAM usada debe ser 0 tras fallo");
        if (compSmall.getRam().getUsedSize() != 0) throw new AssertionError("El usedSize de RAM debe ser 0 tras fallo");

        // Caso 3: Validaciones de entrada (capacidad <= 0 y host inexistente)
        if (controller.createBuffer(compSmall.getId(), 0) != null) throw new AssertionError("Capacidad 0 debe retornar null");
        if (controller.createBuffer(compSmall.getId(), -5) != null) throw new AssertionError("Capacidad negativa debe retornar null");
        if (controller.createBuffer(999, 10) != null) throw new AssertionError("Host inexistente debe retornar null");

        // Caso 4: Ajuste exacto (RAM 32, buffer 32)
        controller.reset();
        Computer compExact = controller.createComputer(32, SchedulingPolicyType.FCFS);
        Buffer bufExact = controller.createBuffer(compExact.getId(), 32);
        if (bufExact == null) throw new AssertionError("Ajuste exacto de buffer debe ser exitoso");
        if (compExact.getMemoryManager().getUsedMemory() != 32) throw new AssertionError("La RAM usada debe ser 32");
        if (controller.getBuffers().size() != 1) throw new AssertionError("Debe haber 1 buffer registrado");

        // Caso 5: Múltiples buffers respetan capacidad y contigüidad
        controller.reset();
        Computer compMulti = controller.createComputer(64, SchedulingPolicyType.FCFS);
        Buffer b1 = controller.createBuffer(compMulti.getId(), 32);
        Buffer b2 = controller.createBuffer(compMulti.getId(), 32);
        if (b1 == null || b2 == null) throw new AssertionError("Dos buffers de 32 deben caber en RAM 64");
        if (compMulti.getMemoryManager().getUsedMemory() != 64) throw new AssertionError("RAM usada debe ser 64");
        if (controller.getBuffers().size() != 2) throw new AssertionError("Deben haber 2 buffers registrados");

        // Tercer buffer no debe caber y no debe alterar estado
        Buffer b3 = controller.createBuffer(compMulti.getId(), 1);
        if (b3 != null) throw new AssertionError("Tercer buffer debe fallar por falta de RAM");
        if (compMulti.getMemoryManager().getUsedMemory() != 64) throw new AssertionError("RAM usada debe mantenerse en 64");
        if (controller.getBuffers().size() != 2) throw new AssertionError("Deben mantenerse 2 buffers registrados");
    }

    private static void testNetworkDelayAdvancement() {
        SimulationController controller = SimulationController.getInstance();

        // 1. Caso de reproducción exacto: 2 computadores FCFS, buffer en comp1, productor en comp2, latencia 2
        controller.reset();
        controller.setNetworkLatency(2);

        Computer c1 = controller.createComputer(128, SchedulingPolicyType.FCFS);
        Computer c2 = controller.createComputer(128, SchedulingPolicyType.FCFS);

        Buffer buf = controller.createBuffer(c1.getId(), 5);
        ProcessControlBlock prod = controller.createProcess(
                "Prod", c2.getId(), ProcessType.PRODUCER, 10, 10, 1, 100,
                String.valueOf(buf.getId()), 1, 1
        );

        // Precondición: terminó su instrucción de trabajo local.
        prod.setSyncPhase(ProcessControlBlock.SyncPhase.WAIT_SLOT);
        prod.setProgramCounter(1);
        prod.setMemoryAddressRegister(1);
        // Tick 1: Despacha en c2, detecta acceso remoto y se bloquea con demora restante 2
        controller.stepSimulation();
        if (prod.getState() != ProcessState.BLOQUEADO) throw new AssertionError("Debe estar BLOQUEADO en ciclo 1");
        if (prod.getBlockReason() != BlockReason.NETWORK_LATENCY) throw new AssertionError("Razón debe ser NETWORK_LATENCY");
        if (prod.getNetworkDelayRemaining() != 2) throw new AssertionError("Demora restante debe ser 2");
        if (c2.getCpu().getPCB() != null) throw new AssertionError("CPU de c2 no debe estar ocupada por proceso bloqueado");

        // Tick 2: Demora avanza sin ocupar CPU (restante pasa de 2 a 1)
        controller.stepSimulation();
        if (prod.getState() != ProcessState.BLOQUEADO) throw new AssertionError("Debe seguir BLOQUEADO en ciclo 2");
        if (prod.getNetworkDelayRemaining() != 1) throw new AssertionError("Demora restante debe ser 1 en ciclo 2");
        if (c2.getCpu().getPCB() != null) throw new AssertionError("CPU de c2 no debe estar ocupada en ciclo 2");

        // Tick 3: Demora llega a 0, vuelve a LISTO y se despacha a CPU en el mismo ciclo
        controller.stepSimulation();
        if (prod.getNetworkDelayRemaining() != 0) throw new AssertionError("Demora restante debe ser 0 en ciclo 3");
        if (c2.getCpu().getPCB() != prod) throw new AssertionError("Debe despacharse a CPU tras completar latencia");
        if (prod.getState() != ProcessState.EJECUTANDO) throw new AssertionError("Debe estar EJECUTANDO en ciclo 3");

        // Avanzar hasta completar los 10 ticks: debe terminar exitosamente sin congelarse
        for (int i = 4; i <= 10; i++) {
            controller.stepSimulation();
        }
        if (prod.getState() != ProcessState.SALIENTE) throw new AssertionError("Productor debe terminar exitosamente tras 10 ticks");
        if (prod.getProcessedElements() != 1) throw new AssertionError("Debe haber producido 1 elemento");

        // 2. Latencia 0: No debe bloquearse por demora de red
        controller.reset();
        controller.setNetworkLatency(0);
        Computer c1_0 = controller.createComputer(128, SchedulingPolicyType.FCFS);
        Computer c2_0 = controller.createComputer(128, SchedulingPolicyType.FCFS);
        Buffer buf0 = controller.createBuffer(c1_0.getId(), 5);
        ProcessControlBlock prod0 = controller.createProcess(
                "Prod0", c2_0.getId(), ProcessType.PRODUCER, 10, 10, 1, 100,
                String.valueOf(buf0.getId()), 1, 1
        );
        // Precondición: terminó su instrucción de trabajo local.
        prod0.setSyncPhase(ProcessControlBlock.SyncPhase.WAIT_SLOT);
        prod0.setProgramCounter(1);
        prod0.setMemoryAddressRegister(1);
        controller.stepSimulation();
        if (prod0.getState() == ProcessState.BLOQUEADO && prod0.getBlockReason() == BlockReason.NETWORK_LATENCY) {
            throw new AssertionError("Con latencia 0 no debe bloquearse por NETWORK_LATENCY");
        }

        // 3. Latencia 1: Se bloquea 1 ciclo y al siguiente vuelve a LISTO/CPU
        controller.reset();
        controller.setNetworkLatency(1);
        Computer c1_1 = controller.createComputer(128, SchedulingPolicyType.FCFS);
        Computer c2_1 = controller.createComputer(128, SchedulingPolicyType.FCFS);
        Buffer buf1 = controller.createBuffer(c1_1.getId(), 5);
        ProcessControlBlock prod1 = controller.createProcess(
                "Prod1", c2_1.getId(), ProcessType.PRODUCER, 10, 10, 1, 100,
                String.valueOf(buf1.getId()), 1, 1
        );
        // Precondición: terminó su instrucción de trabajo local.
        prod1.setSyncPhase(ProcessControlBlock.SyncPhase.WAIT_SLOT);
        prod1.setProgramCounter(1);
        prod1.setMemoryAddressRegister(1);
        controller.stepSimulation();
        if (prod1.getState() != ProcessState.BLOQUEADO || prod1.getNetworkDelayRemaining() != 1) {
            throw new AssertionError("Con latencia 1 debe bloquearse con demora 1");
        }
        controller.stepSimulation();
        if (prod1.getState() != ProcessState.EJECUTANDO || prod1.getNetworkDelayRemaining() != 0) {
            throw new AssertionError("Al ciclo 2 con latencia 1 debe estar EJECUTANDO");
        }

        // 4. Cancelación por deadline sin reactivar terminados
        controller.reset();
        controller.setNetworkLatency(5);
        Computer c1_d = controller.createComputer(128, SchedulingPolicyType.FCFS);
        Computer c2_d = controller.createComputer(128, SchedulingPolicyType.FCFS);
        Buffer bufd = controller.createBuffer(c1_d.getId(), 5);
        ProcessControlBlock prodD = controller.createProcess(
                "ProdDeadline", c2_d.getId(), ProcessType.PRODUCER, 10, 10, 1, 2,
                String.valueOf(bufd.getId()), 1, 1
        );
        // Precondición: terminó su instrucción de trabajo local.
        prodD.setSyncPhase(ProcessControlBlock.SyncPhase.WAIT_SLOT);
        prodD.setProgramCounter(1);
        prodD.setMemoryAddressRegister(1);
        controller.stepSimulation(); // Ciclo 1: Bloqueado por red, deadline 2 -> 1
        if (prodD.getState() != ProcessState.BLOQUEADO) throw new AssertionError("Debe estar bloqueado");
        controller.stepSimulation(); // Ciclo 2: Deadline vence -> SALIENTE
        if (prodD.getState() != ProcessState.SALIENTE) throw new AssertionError("Debe vencer por deadline");

        // Avanzar más ticks donde la latencia de red llegaría a 0: NO debe reactivarse
        for (int i = 0; i < 6; i++) {
            controller.stepSimulation();
        }
        if (prodD.getState() != ProcessState.SALIENTE) throw new AssertionError("Proceso terminado no debe reactivarse");
        if (c2_d.getKernel().getReadyQueue().removeElement(prodD)) {
            throw new AssertionError("Proceso terminado por deadline no debe volver a readyQueue");
        }

        // 5. Múltiples solicitudes
        controller.reset();
        controller.setNetworkLatency(2);
        Computer c1_m = controller.createComputer(128, SchedulingPolicyType.FCFS);
        Computer c2_m = controller.createComputer(128, SchedulingPolicyType.FCFS);
        Buffer bufm = controller.createBuffer(c1_m.getId(), 5);
        ProcessControlBlock p1 = controller.createProcess("P1", c2_m.getId(), ProcessType.PRODUCER, 10, 10, 1, 100, String.valueOf(bufm.getId()), 1, 1);
        ProcessControlBlock p2 = controller.createProcess("P2", c2_m.getId(), ProcessType.PRODUCER, 10, 10, 1, 100, String.valueOf(bufm.getId()), 1, 1);

        for (ProcessControlBlock p : new ProcessControlBlock[]{p1, p2}) {
            p.setSyncPhase(ProcessControlBlock.SyncPhase.WAIT_SLOT);
            p.setProgramCounter(1); p.setMemoryAddressRegister(1);
        }
        controller.stepSimulation(); // P1 se bloquea por red (demora 2)
        controller.stepSimulation(); // P1 avanza a demora 1; P2 corre y se bloquea (demora 2)
        if (p1.getNetworkDelayRemaining() != 1) throw new AssertionError("P1 demora restante debe ser 1");
        if (p2.getNetworkDelayRemaining() != 2) throw new AssertionError("P2 demora restante debe ser 2");
        controller.stepSimulation(); // P1 llega a 0 y corre; P2 avanza a demora 1
        if (p1.getState() != ProcessState.EJECUTANDO) throw new AssertionError("P1 debe estar ejecutando");
        if (p2.getNetworkDelayRemaining() != 1) throw new AssertionError("P2 demora restante debe ser 1");
    }

    private static void testBufferBlockedProcessesWakeup() {
        SimulationController controller = SimulationController.getInstance();

        // 1. Caso de reproducción exacto:
        // FCFS RAM 128, buffer 5; crear consumidor primero y productor despues.
        // Ambos con 10 ticks, RAM 10, deadline 100, requiredElements 1 y targetBufferId 1. Avanzar 15 ticks.
        controller.reset();
        controller.setNetworkLatency(0);

        Computer comp = controller.createComputer(128, SchedulingPolicyType.FCFS);
        Buffer buf = controller.createBuffer(comp.getId(), 5);

        ProcessControlBlock cons = controller.createProcess(
                "Consumidor", comp.getId(), ProcessType.CONSUMER, 10, 10, 1, 100,
                String.valueOf(buf.getId()), 1, 1
        );
        ProcessControlBlock prod = controller.createProcess(
                "Productor", comp.getId(), ProcessType.PRODUCER, 10, 10, 1, 100,
                String.valueOf(buf.getId()), 1, 1
        );

        // Avanzar 15 ticks
        for (int i = 0; i < 15; i++) {
            controller.stepSimulation();
        }

        if (buf.getProduced() != 1) throw new AssertionError("Debe haber producido 1 elemento");
        if (buf.getComsumed() != 1) throw new AssertionError("Consumidor debe haber despertado y consumido 1 elemento");
        if (cons.getState() != ProcessState.SALIENTE) throw new AssertionError("Consumidor debe terminar en SALIENTE");
        if (prod.getState() != ProcessState.SALIENTE) throw new AssertionError("Productor debe terminar en SALIENTE");

        // 2. Probar lleno (WAITING_FULL_BUFFER)
        controller.reset();
        controller.setNetworkLatency(0);
        Computer compFull = controller.createComputer(128, SchedulingPolicyType.FCFS);
        Buffer buf1 = controller.createBuffer(compFull.getId(), 1); // Capacidad 1

        ProcessControlBlock p1 = controller.createProcess("P1", compFull.getId(), ProcessType.PRODUCER, 10, 10, 1, 100, String.valueOf(buf1.getId()), 1, 1);
        ProcessControlBlock p2 = controller.createProcess("P2", compFull.getId(), ProcessType.PRODUCER, 10, 10, 1, 100, String.valueOf(buf1.getId()), 1, 1);

        // P1 produce el primer elemento y llena el buffer
        for (int i = 0; i < 6; i++) {
            controller.stepSimulation();
        }
        if (buf1.getProduced() != 1) throw new AssertionError("P1 debe haber producido");

        controller.stepSimulation(); // P2 ejecuta su trabajo de usuario
        // P2 intenta producir y se bloquea por WAITING_FULL_BUFFER
        controller.stepSimulation();
        if (p2.getState() != ProcessState.BLOQUEADO || p2.getBlockReason() != BlockReason.WAITING_FULL_BUFFER) {
            throw new AssertionError("P2 debe estar BLOQUEADO por buffer lleno");
        }

        // Crear consumidor para vaciar el buffer
        ProcessControlBlock cFull = controller.createProcess("CFull", compFull.getId(), ProcessType.CONSUMER, 10, 10, 1, 100, String.valueOf(buf1.getId()), 1, 1);
        for (int i = 0; i < 15; i++) {
            controller.stepSimulation();
        }
        // P2 debió despertar y producir su elemento
        if (buf1.getProduced() != 2) throw new AssertionError("P2 debió despertar y producir, total producidos = 2");
        if (p2.getState() != ProcessState.SALIENTE) throw new AssertionError("P2 debe haber terminado");

        // 3. Probar mutex (WAITING_MUTEX)
        controller.reset();
        controller.setNetworkLatency(0);
        Computer compMutex = controller.createComputer(128, SchedulingPolicyType.ROUND_ROBIN);
        Buffer bufMutex = controller.createBuffer(compMutex.getId(), 5);

        ProcessControlBlock pm1 = controller.createProcess("PM1", compMutex.getId(), ProcessType.PRODUCER, 10, 10, 1, 100, String.valueOf(bufMutex.getId()), 1, 1);
        ProcessControlBlock pm2 = controller.createProcess("PM2", compMutex.getId(), ProcessType.PRODUCER, 10, 10, 1, 100, String.valueOf(bufMutex.getId()), 1, 1);

        for (int i = 0; i < 20; i++) {
            controller.stepSimulation();
        }
        if (bufMutex.getProduced() != 2) throw new AssertionError("Ambos procesos deben haber producido bajo exclusión mutua");
        if (pm1.getState() != ProcessState.SALIENTE || pm2.getState() != ProcessState.SALIENTE) {
            throw new AssertionError("Ambos procesos deben terminar tras liberar mutex");
        }
    }

    private static void testProducerSingleTerminationAndIdempotency() {
        SimulationController controller = SimulationController.getInstance();

        // 1. Caso de reproducción exacto:
        // FCFS RAM 128; buffer 5; productor de 10 ticks, RAM 20, requiredElements 1, targetBufferId 1;
        // otro CPU_BOUND de 30 ticks y RAM 30; deadlines 100. Avanzar 5 ticks.
        controller.reset();
        controller.setNetworkLatency(0);

        Computer comp = controller.createComputer(128, SchedulingPolicyType.FCFS);
        Buffer buf = controller.createBuffer(comp.getId(), 5);

        ProcessControlBlock prod = controller.createProcess(
                "Prod", comp.getId(), ProcessType.PRODUCER, 10, 20, 1, 100,
                String.valueOf(buf.getId()), 1, 1
        );
        ProcessControlBlock cpuBound = controller.createProcess(
                "CpuBound", comp.getId(), ProcessType.CPU_BOUND, 30, 30, 1, 100
        );

        // RAM usada inicial = buffer (5) + prod (20) + cpuBound (30) = 55
        if (comp.getMemoryManager().getUsedMemory() != 55) {
            throw new AssertionError("RAM usada inicial debe ser 55, actual: " + comp.getMemoryManager().getUsedMemory());
        }

        // Un tick de trabajo + cinco instrucciones del protocolo
        for (int i = 0; i < 6; i++) {
            controller.stepSimulation();
        }

        if (prod.getState() != ProcessState.SALIENTE) {
            throw new AssertionError("Productor debe estar en SALIENTE tras 6 ticks");
        }
        if (comp.getKernel().getEndedQueue().size() != 1) {
            throw new AssertionError("endedQueue debe tener exactamente 1 entrada, actual: " + comp.getKernel().getEndedQueue().size());
        }
        if (comp.getMemoryManager().getUsedMemory() != 35) {
            throw new AssertionError("RAM usada esperada es 35 (buffer 5 + cpuBound 30), actual: " + comp.getMemoryManager().getUsedMemory());
        }

        // 2. Probar idempotencia: repetir terminateProcess no altera RAM ni colas
        comp.getKernel().terminateProcess(comp, prod);
        comp.getKernel().terminateProcess(comp, prod);

        if (comp.getKernel().getEndedQueue().size() != 1) {
            throw new AssertionError("Repetir terminateProcess no debe duplicar en endedQueue");
        }
        if (comp.getMemoryManager().getUsedMemory() != 35) {
            throw new AssertionError("Repetir terminateProcess no debe alterar la RAM usada");
        }
    }

    private static void testRoundRobinPreemptionAndDispatch() {
        SimulationController controller = SimulationController.getInstance();

        // 1. Caso de reproducción exacto:
        // Computador RAM 128, ROUND_ROBIN; dos procesos CPU_BOUND de 20 ticks, RAM 10, prioridad 1 y deadline 100. Avanzar 8 ticks.
        controller.reset();
        Computer compRR = controller.createComputer(128, SchedulingPolicyType.ROUND_ROBIN);
        ProcessControlBlock pA = controller.createProcess("A", compRR.getId(), ProcessType.CPU_BOUND, 20, 10, 1, 100);
        ProcessControlBlock pB = controller.createProcess("B", compRR.getId(), ProcessType.CPU_BOUND, 20, 10, 1, 100);

        for (int i = 0; i < 8; i++) {
            controller.stepSimulation();
        }

        // Con quantum 3:
        // Ticks 1-3: A corre (3 ticks) -> restantes 17
        // Ticks 4-6: B corre (3 ticks) -> restantes 17
        // Ticks 7-8: A corre (2 ticks) -> restantes 15
        if (pA.getRemainingTime() != 15) {
            throw new AssertionError("A debe tener 15 ticks restantes con quantum 3, actual: " + pA.getRemainingTime());
        }
        if (pB.getRemainingTime() != 17) {
            throw new AssertionError("B debe tener 17 ticks restantes con quantum 3, actual: " + pB.getRemainingTime());
        }

        // 2. Un proceso solo continúa en Round Robin sin que se borre la CPU al agotarse el quantum
        controller.reset();
        Computer compSingle = controller.createComputer(128, SchedulingPolicyType.ROUND_ROBIN);
        ProcessControlBlock pSingle = controller.createProcess("Single", compSingle.getId(), ProcessType.CPU_BOUND, 20, 10, 1, 100);

        for (int i = 0; i < 8; i++) {
            controller.stepSimulation();
        }
        if (pSingle.getRemainingTime() != 12) {
            throw new AssertionError("Un proceso solo en RR debe continuar ininterrumpidamente, restantes: " + pSingle.getRemainingTime());
        }
        if (compSingle.getCpu().getPCB() != pSingle) {
            throw new AssertionError("La CPU no debe ser vaciada si hay un proceso corriendo");
        }

        // 3. FCFS no es desalojado
        controller.reset();
        Computer compFcfs = controller.createComputer(128, SchedulingPolicyType.FCFS);
        ProcessControlBlock pF1 = controller.createProcess("F1", compFcfs.getId(), ProcessType.CPU_BOUND, 20, 10, 1, 100);
        ProcessControlBlock pF2 = controller.createProcess("F2", compFcfs.getId(), ProcessType.CPU_BOUND, 20, 10, 1, 100);

        for (int i = 0; i < 8; i++) {
            controller.stepSimulation();
        }
        if (pF1.getRemainingTime() != 12) {
            throw new AssertionError("F1 debe correr los 8 ticks en FCFS, restantes: " + pF1.getRemainingTime());
        }
        if (pF2.getRemainingTime() != 20) {
            throw new AssertionError("F2 no debe ser ejecutado en FCFS mientras F1 no termine, restantes: " + pF2.getRemainingTime());
        }
    }

    /**
     * Escenario: Fuga de permisos de vacios/llenos cuando un proceso muere por deadline
     * mientras retiene un permiso adquirido en la etapa 0 (syncStage >= 1).
     *
     * Verifica:
     *   1. Tras cancelar un Productor en etapa 1 por deadline, vacios recupera su permiso.
     *   2. Un nuevo Productor con deadline holgado puede producir exitosamente.
     */
    private static void testDeadlinePermitLeakFix() {
        SimulationController controller = SimulationController.getInstance();
        controller.reset();

        Computer comp = controller.createComputer(128, SchedulingPolicyType.FCFS);
        controller.setNetworkLatency(0);
        Buffer buf = controller.createBuffer(comp.getId(), 1); // búfer de capacidad 1
        if (buf == null) throw new AssertionError("El búfer debe crearse en RAM suficiente");

        int vaciosBefore = buf.getVacios().availablePermits();
        if (vaciosBefore != 1) throw new AssertionError("vacios debe iniciar en 1, got: " + vaciosBefore);

        // Productor A con deadline muy corto (2 ticks): muere antes de terminar la producción
        String bufId = String.valueOf(buf.getId());
        ProcessControlBlock prodA = controller.createProcess(
                "ProdA", comp.getId(), ProcessType.PRODUCER, 10, 10, 1, 3, bufId, 1, 1);
        if (prodA == null) throw new AssertionError("ProdA debe crearse");

        controller.stepSimulation(); // Trabajo de usuario: aún no tiene permiso
        // Tick 2: ProdA ejecuta wait(vacios)
        controller.stepSimulation();
        // Tick 3: deadline vence → cancela y devuelve vacios
        controller.stepSimulation();

        // El permiso de vacios debe haberse recuperado
        int vaciosAfter = buf.getVacios().availablePermits();
        if (vaciosAfter != 1) {
            throw new AssertionError(
                "Tras cancelar ProdA en etapa 1 por deadline, vacios debe recuperarse a 1, got: " + vaciosAfter);
        }
        if (prodA.getState() != ProcessState.SALIENTE) {
            throw new AssertionError("ProdA debe estar SALIENTE tras vencer el deadline");
        }

        // Segundo escenario: nuevo productor con deadline holgado debe producir sin bloqueo
        controller.reset();
        comp = controller.createComputer(128, SchedulingPolicyType.FCFS);
        controller.setNetworkLatency(0);
        buf = controller.createBuffer(comp.getId(), 1);
        bufId = String.valueOf(buf.getId());

        ProcessControlBlock prodB = controller.createProcess(
                "ProdB", comp.getId(), ProcessType.PRODUCER, 10, 10, 1, 50, bufId, 1, 1);
        if (prodB == null) throw new AssertionError("ProdB debe crearse");

        for (int i = 0; i < 5; i++) {
            controller.stepSimulation();
        }

        if (buf.getProduced() < 1) {
            throw new AssertionError("ProdB debe haber producido al menos 1 elemento, produced=" + buf.getProduced());
        }

        System.out.println("[PASS] testDeadlinePermitLeakFix");
    }
}
