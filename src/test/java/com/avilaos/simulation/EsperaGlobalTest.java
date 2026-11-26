package com.avilaos.simulation;

import com.avilaos.core.model.ProcessType;
import com.avilaos.core.model.SchedulingPolicyType;

/** La espera global incluye NUEVO sin cambiar la espera usada para elegir víctimas de swap. */
public class EsperaGlobalTest {
    public static void main(String[] args) {
        var c = SimulationController.getInstance();
        c.reset();
        try {
            var pc = c.createComputer("PC", 1, SchedulingPolicyType.FCFS);
            var primero = c.createProcess("Primero", pc.getId(), ProcessType.CPU_BOUND, 4, 1, 1, 100);
            var segundo = c.createProcess("Segundo", pc.getId(), ProcessType.CPU_BOUND, 1, 1, 1, 100);
            for (int i = 0; i < 20 && !segundo.isTerminated(); i++) c.stepSimulation();
            if (!primero.isTerminated() || !segundo.isTerminated() || segundo.getWaitingNewTicks() <= 0)
                throw new AssertionError("El segundo proceso no esperó en Nuevos o no terminó");
            int esperado = primero.getWaitingTime() + primero.getWaitingNewTicks()
                    + segundo.getWaitingTime() + segundo.getWaitingNewTicks();
            if (pc.getTotalTiempoEspera() != esperado || esperado < segundo.getWaitingNewTicks())
                throw new AssertionError("La espera promedio omitió ciclos de Nuevos");
            System.out.println("PASS: espera en Nuevos incluida en espera global de procesos completados");
        } finally { c.reset(); }
    }
}
