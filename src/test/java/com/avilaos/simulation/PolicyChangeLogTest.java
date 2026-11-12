package com.avilaos.simulation;

import com.avilaos.core.model.SchedulingPolicyType;
import javax.swing.SwingUtilities;

public class PolicyChangeLogTest {
    public static void main(String[] args) throws Exception {
        SimulationController c = SimulationController.getInstance();
        c.reset();
        c.createComputer("Cambio", 128, SchedulingPolicyType.FCFS, 1);
        c.createComputer("Referencia", 128, SchedulingPolicyType.FCFS, 1);
        SwingUtilities.invokeAndWait(() -> {});
        int[] count = {0};
        String[] event = {null};
        c.addLogListener(message -> {
            if (message.contains("Política de")) { count[0]++; event[0] = message; }
        });
        SwingUtilities.invokeAndWait(() -> {
            c.setCurrentCycle(2);
            c.setComputerPolicy(1, SchedulingPolicyType.ROUND_ROBIN);
            c.setComputerPolicy(1, SchedulingPolicyType.ROUND_ROBIN);
            c.setCurrentCycle(3);
        });
        SwingUtilities.invokeAndWait(() -> {});
        if (count[0] != 1 || !"[Ciclo 2] Política de Cambio (ID 1) cambiada de FCFS a ROUND_ROBIN".equals(event[0]))
            throw new AssertionError("Evento incorrecto o duplicado: " + event[0] + " count=" + count[0]);
        if (c.getComputerById(2).getPolitica() != SchedulingPolicyType.FCFS)
            throw new AssertionError("Se alteró la política del computador de referencia");
        System.out.println("PASS: cambio de política registrado una vez en el ciclo correcto");
    }
}
