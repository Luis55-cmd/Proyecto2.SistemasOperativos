package com.avilaos.gui;

import com.avilaos.simulation.SimulationController;
import com.avilaos.core.model.SchedulingPolicyType;
import java.io.File;

/** La detección de nombres duplicados no debe reemplazar la sesión activa. */
public class CargaDuplicadosTest {
    public static void main(String[] args) {
        SimulationController c = SimulationController.getInstance();
        c.reset();
        var anterior = c.createComputer("Anterior", 32, SchedulingPolicyType.FCFS, 2);
        c.setCurrentCycle(7);
        MonitorSimulacion monitor = new MonitorSimulacion(c, () -> {});
        if (monitor.cargarEstado(new File(args[0]))) throw new AssertionError("Carga inválida aceptada");
        if (c.getComputers().size() != 1 || c.getComputers().get(0) != anterior || c.getCurrentCycle() != 7)
            throw new AssertionError("La carga fallida cambió la sesión");
        System.out.println("PASS: duplicados rechazan antes de reiniciar");
    }
}
