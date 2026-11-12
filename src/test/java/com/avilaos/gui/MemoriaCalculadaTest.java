package com.avilaos.gui;

import com.avilaos.core.model.BlockReason;
import com.avilaos.core.model.ProcessState;
import com.avilaos.core.model.ProcessType;
import com.avilaos.core.model.SchedulingPolicyType;
import com.avilaos.simulation.SimulationController;
import java.nio.file.Files;

/** Seguimiento manual del caso 10 CPU, intervalo 3, duración 2: 13 instrucciones y 3 solicitudes. */
public class MemoriaCalculadaTest {
    private static void check(boolean ok, String mensaje) { if (!ok) throw new AssertionError(mensaje); }
    public static void main(String[] args) throws Exception {
        var c = SimulationController.getInstance();
        c.reset();
        var pc = c.createComputer("Prueba", 128, SchedulingPolicyType.FCFS);
        var p = c.createConfiguredProcess("E/S", pc.getId(), ProcessType.IO_BOUND, 10, 1, 100, 3, 2, null, 1, 1);
        check(p.getTotalInstructions() == 13 && p.getRequiredMemory() == 13, "10 + 3 solicitudes = 13");
        check(pc.getMemoryManager().getUsedMemory() == 13, "Reserva real de 13 RAM");
        for (int i = 0; i < 3; i++) c.stepSimulation();
        check(p.getProgramCounter() == 3 && p.getBlockReason() == BlockReason.NONE, "Tres instrucciones de trabajo");
        c.stepSimulation();
        check(p.getProgramCounter() == 4 && p.getBlockReason() == BlockReason.WAITING_IO, "Solicitud E/S ocupa una instrucción");
        var file = Files.createTempFile("avila-memoria", ".json");
        try {
            var configuracion = new ConfiguracionSimulacion(c);
            check(configuracion.guardarEstado(file.toFile()), "Guardar configuración");
            check(configuracion.cargarEstado(file.toFile()), "Cargar configuración");
            p = c.getAllProcesses().get(0);
            pc = c.getComputers().get(0);
            check(p.isIoRequestInstruction() && p.getRequiredMemory() == 13, "Conserva regla tras cargar");
            int solicitudes = 0;
            for (int i = 0; i < 40 && p.getState() != ProcessState.SALIENTE; i++) {
                boolean antes = p.getBlockReason() == BlockReason.WAITING_IO;
                c.stepSimulation();
                if (!antes && p.getBlockReason() == BlockReason.WAITING_IO) solicitudes++;
            }
            check(solicitudes == 3 && p.getProgramCounter() == 13, "Ejecuta 10 CPU y 3 solicitudes");
            check(pc.getCiclosActivos() == 13 && pc.getMemoryManager().getUsedMemory() == 0, "CPU y liberación de RAM");
        } finally { Files.deleteIfExists(file); c.reset(); }
        System.out.println("PASS MemoriaCalculadaTest");
        System.exit(0);
    }
}
