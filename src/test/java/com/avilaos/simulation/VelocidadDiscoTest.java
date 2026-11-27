package com.avilaos.simulation;

import com.avilaos.core.model.IOOperationType;
import com.avilaos.core.model.UserMode;

public class VelocidadDiscoTest {
    public static void main(String[] args) {
        var c = SimulationController.getInstance();
        c.reset();
        try {
            var disco = c.getDisk();
            disco.setHeadPosition(0);
            c.setDiskHeadSpeed(3);
            disco.enqueueRequest(null, IOOperationType.READ, "/prueba", 20, 1, 0);
            disco.tick(1);
            if (disco.getHeadPosition() != 3) throw new AssertionError("Velocidad inicial no aplicada");
            c.setDiskHeadSpeed(7);
            disco.tick(2);
            if (disco.getHeadPosition() != 10) throw new AssertionError("El siguiente ciclo no usó la nueva velocidad");
            for (int valor : new int[]{0, 501}) {
                try { c.setDiskHeadSpeed(valor); throw new AssertionError("Rango inválido aceptado"); }
                catch (IllegalArgumentException esperado) { }
            }
            c.setUserSession(UserMode.USER, "ana");
            try { c.setDiskHeadSpeed(1); throw new AssertionError("USER cambió la velocidad"); }
            catch (IllegalStateException esperado) { }
            if (disco.getHeadSpeedPerTick() != 7) throw new AssertionError("La velocidad cambió tras rechazo");
            System.out.println("PASS: cambio de velocidad por ciclo, rango y permisos");
        } finally { c.reset(); }
    }
}
