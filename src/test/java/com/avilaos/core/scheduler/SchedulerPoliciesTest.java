package com.avilaos.core.scheduler;

import com.avilaos.core.model.ProcessControlBlock;
import com.avilaos.core.model.ProcessState;
import com.avilaos.core.model.ProcessType;
import com.avilaos.core.model.SchedulingPolicyType;

/**
 * Pruebas unitarias para las 4 políticas de planificación:
 * - FcfsScheduler
 * - EdfScheduler
 * - RoundRobinScheduler
 * - PreemptivePriorityScheduler
 */
public class SchedulerPoliciesTest {

    public static void main(String[] args) {
        testFcfs();
        testEdf();
        testRoundRobin();
        testPreemptivePriority();
        System.out.println("TODAS LAS PRUEBAS DE SchedulerPoliciesTest PASARON EXITOSAMENTE.");
    }

    private static void testFcfs() {
        FcfsScheduler scheduler = new FcfsScheduler();
        if (scheduler.getType() != SchedulingPolicyType.FCFS) throw new AssertionError("Tipo incorrecto");

        ProcessControlBlock p1 = new ProcessControlBlock("P1", 1, ProcessType.CPU_BOUND, 5, 100, 1, 20);
        ProcessControlBlock p2 = new ProcessControlBlock("P2", 1, ProcessType.CPU_BOUND, 5, 100, 1, 20);

        scheduler.addProcess(p1);
        scheduler.addProcess(p2);

        // Primer despacho: toma P1
        ProcessControlBlock running = scheduler.scheduleNext(null, null);
        if (running != p1) throw new AssertionError("FCFS debio elegir P1");
        if (running.getState() != ProcessState.EJECUTANDO) throw new AssertionError("P1 debe estar RUNNING");

        // FCFS no desaloja mientras P1 siga RUNNING
        ProcessControlBlock same = scheduler.scheduleNext(null, running);
        if (same != p1) throw new AssertionError("FCFS no debe desalojar a P1");

        // P1 termina
        running.setState(ProcessState.SALIENTE);
        ProcessControlBlock next = scheduler.scheduleNext(null, running);
        if (next != p2) throw new AssertionError("FCFS debio despachar P2 tras terminar P1");

        // P2 termina
        next.setState(ProcessState.SALIENTE);
        ProcessControlBlock none = scheduler.scheduleNext(null, next);
        if (none != null) throw new AssertionError("Cola vacia debe retornar null");
    }

    private static void testEdf() {
        EdfScheduler scheduler = new EdfScheduler();
        if (scheduler.getType() != SchedulingPolicyType.EDF) throw new AssertionError("Tipo incorrecto");

        ProcessControlBlock pFar = new ProcessControlBlock("Far", 1, ProcessType.CPU_BOUND, 10, 100, 1, 100);
        ProcessControlBlock pUrgent = new ProcessControlBlock("Urgent", 1, ProcessType.CPU_BOUND, 10, 100, 1, 20);

        scheduler.addProcess(pFar);
        scheduler.addProcess(pUrgent);

        // Debe seleccionar Urgent (deadline 20 < 100)
        ProcessControlBlock running = scheduler.scheduleNext(null, null);
        if (running != pUrgent) throw new AssertionError("EDF debio elegir el de menor deadline");

        // Ahora llega uno SUPER urgente (deadline 5)
        ProcessControlBlock pCritical = new ProcessControlBlock("Critical", 1, ProcessType.CPU_BOUND, 10, 100, 1, 5);
        scheduler.addProcess(pCritical);

        // Desalojo apropiativo de EDF
        ProcessControlBlock preempted = scheduler.scheduleNext(null, running);
        if (preempted != pCritical) throw new AssertionError("EDF debio desalojar por proceso con deadline mas cercano");
        if (running.getState() != ProcessState.LISTO) throw new AssertionError("Proceso desalojado debe volver a READY");
    }

    private static void testRoundRobin() {
        RoundRobinScheduler scheduler = new RoundRobinScheduler(2); // Quantum de 2
        if (scheduler.getType() != SchedulingPolicyType.ROUND_ROBIN) throw new AssertionError("Tipo incorrecto");
        if (scheduler.getQuantum() != 2) throw new AssertionError("Quantum debe ser 2");

        ProcessControlBlock p1 = new ProcessControlBlock("P1", 1, ProcessType.CPU_BOUND, 10, 100, 1, 50);
        ProcessControlBlock p2 = new ProcessControlBlock("P2", 1, ProcessType.CPU_BOUND, 10, 100, 1, 50);

        scheduler.addProcess(p1);
        scheduler.addProcess(p2);

        // Inicia P1
        ProcessControlBlock running = scheduler.scheduleNext(null, null);
        if (running != p1) throw new AssertionError("RR debio despachar P1");

        // Ciclo 1
        scheduler.onClockTick();
        if (scheduler.getCurrentQuantumRemaining() != 1) throw new AssertionError("Quantum restante debe ser 1");
        running = scheduler.scheduleNext(null, running);
        if (running != p1) throw new AssertionError("P1 debe retener la CPU en su primer ciclo de quantum");

        // Ciclo 2 (se agota quantum)
        scheduler.onClockTick();
        if (scheduler.getCurrentQuantumRemaining() != 0) throw new AssertionError("Quantum restante debe ser 0");

        // Desalojo por quantum
        running = scheduler.scheduleNext(null, running);
        if (running != p2) throw new AssertionError("P1 debio ser desalojado y P2 despachado");
        if (p1.getState() != ProcessState.LISTO) throw new AssertionError("P1 debe volver a READY");
        if (scheduler.getCurrentQuantumRemaining() != 2) throw new AssertionError("Quantum de P2 debe reiniciar a 2");
    }

    private static void testPreemptivePriority() {
        PreemptivePriorityScheduler scheduler = new PreemptivePriorityScheduler(true); // mayor numero = mayor prioridad
        if (scheduler.getType() != SchedulingPolicyType.PRIORITY_PREEMPTIVE) throw new AssertionError("Tipo incorrecto");

        ProcessControlBlock pLow = new ProcessControlBlock("Low", 1, ProcessType.CPU_BOUND, 10, 100, 1, 50);
        ProcessControlBlock pHigh = new ProcessControlBlock("High", 1, ProcessType.CPU_BOUND, 10, 100, 10, 50);

        scheduler.addProcess(pLow);
        ProcessControlBlock running = scheduler.scheduleNext(null, null);
        if (running != pLow) throw new AssertionError("Debio despachar pLow que estaba solo");

        // Llega pHigh con prioridad 10 (> 1)
        scheduler.addProcess(pHigh);

        // Desalojo inmediato
        ProcessControlBlock next = scheduler.scheduleNext(null, running);
        if (next != pHigh) throw new AssertionError("Debio desalojar pLow por pHigh de mayor prioridad");
        if (pLow.getState() != ProcessState.LISTO) throw new AssertionError("pLow debe estar de vuelta en READY");

        // Llega pMedium (prioridad 5) mientras corre pHigh (10) -> NO debe desalojar a pHigh
        ProcessControlBlock pMedium = new ProcessControlBlock("Medium", 1, ProcessType.CPU_BOUND, 10, 100, 5, 50);
        scheduler.addProcess(pMedium);

        ProcessControlBlock stillHigh = scheduler.scheduleNext(null, next);
        if (stillHigh != pHigh) throw new AssertionError("pHigh no debe ser desalojado por prioridad menor");

        // pHigh termina -> debe seleccionar pMedium antes que pLow
        pHigh.setState(ProcessState.SALIENTE);
        ProcessControlBlock afterHigh = scheduler.scheduleNext(null, pHigh);
        if (afterHigh != pMedium) throw new AssertionError("Tras pHigh debio seleccionar pMedium (prioridad 5)");
    }
}
