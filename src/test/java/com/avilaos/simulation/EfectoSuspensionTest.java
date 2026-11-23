package com.avilaos.simulation;

import com.avilaos.core.model.ProcessType;
import com.avilaos.filesystem.FilePermission;
import com.avilaos.filesystem.FsNodeType;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;

/** Contrasta dos configuraciones idénticas salvo el espacio de swap. */
public class EfectoSuspensionTest {
    private record Resultado(int finalNuevo, int finalCreador, int movimientos, int suspensiones, int reactivaciones) { }
    public static void main(String[] args) throws Exception {
        var sinSwap = ejecutar(0);
        var conSwap = ejecutar(100);
        if (!sinSwap.equals(new Resultado(17, 15, 53, 0, 0))) throw new AssertionError("Sin swap: " + sinSwap);
        if (!conSwap.equals(new Resultado(38, 41, 251, 1, 1))) throw new AssertionError("Con swap: " + conSwap);
        System.out.println("Sin swap: " + sinSwap);
        System.out.println("Con swap: " + conSwap);
    }
    private static Resultado ejecutar(int swap) throws Exception {
        var c = SimulationController.getInstance();
        c.reset();
        try {
            var cfg = JsonParser.parseString(Files.readString(Path.of("escenarios/carga-json/05-procesos-enunciado.json"))).getAsJsonObject();
            cfg.getAsJsonObject("globalConfig").addProperty("swapSizeBlocks", swap);
            cfg.getAsJsonObject("computer").addProperty("ramCapacity", 2);
            cfg.add("processes", new JsonArray());
            c.loadProcessesConfigJson(cfg.toString());
            var pc = c.getComputers().get(0);
            var creador = c.requestCreation("/prueba", FsNodeType.FILE, 5, "admin", FilePermission.PUBLIC);
            c.stepSimulation();
            var nuevo = c.createProcess("nuevo", pc.getId(), ProcessType.CPU_BOUND, 2, 2, 5, 1000);
            int finalCreador = -1, finalNuevo = -1;
            for (int i = 0; i < 500 && (!creador.isTerminated() || !nuevo.isTerminated()); i++) {
                c.stepSimulation();
                if (creador.isTerminated() && finalCreador < 0) finalCreador = c.getCurrentCycle();
                if (nuevo.isTerminated() && finalNuevo < 0) finalNuevo = c.getCurrentCycle();
            }
            if (finalCreador < 0 || finalNuevo < 0 || c.getFileSystemManager().findNode("/prueba") == null
                    || pc.getMemoryManager().getUsedMemory() != 0)
                throw new AssertionError("La ejecución no finalizó o perdió estado");
            return new Resultado(finalNuevo, finalCreador, c.getDisk().getTotalHeadMovement(),
                    pc.getSuspensionCount(), pc.getReactivationCount());
        } finally { c.reset(); }
    }
}
