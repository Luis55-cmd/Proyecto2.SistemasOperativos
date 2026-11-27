package com.avilaos.simulation;

import com.avilaos.core.model.ProcessState;
import com.avilaos.core.model.ProcessType;
import com.avilaos.filesystem.FilePermission;
import com.avilaos.filesystem.FsNodeType;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;

/** Sin swap libre, el proceso nuevo espera y no se pierde RAM ni una operación en curso. */
public class SwapInsuficienteTest {
    public static void main(String[] args) throws Exception {
        var c = SimulationController.getInstance();
        c.reset();
        try {
            var cfg = JsonParser.parseString(Files.readString(Path.of("escenarios/carga-json/05-procesos-enunciado.json"))).getAsJsonObject();
            cfg.getAsJsonObject("globalConfig").addProperty("swapSizeBlocks", 0);
            cfg.getAsJsonObject("computer").addProperty("ramCapacity", 2);
            cfg.add("processes", new JsonArray());
            c.loadProcessesConfigJson(cfg.toString());
            var pc = c.getComputers().get(0);
            var primero = c.requestCreation("/sin-swap", FsNodeType.FILE, 5, "admin", FilePermission.PUBLIC);
            c.stepSimulation();
            if (primero.getState() != ProcessState.BLOQUEADO) throw new AssertionError("El primer proceso no esperó E/S");
            var segundo = c.createProcess("nuevo", pc.getId(), ProcessType.CPU_BOUND, 2, 2, 5, 1000);
            if (segundo == null || segundo.getState() != ProcessState.NUEVO
                    || pc.getMemoryManager().getUsedMemory() != 2 || c.getDisk().getFreeSwapBlocks() != 0)
                throw new AssertionError("Sin swap el proceso debe esperar sin alterar la RAM");
            for (int i = 0; i < 500 && (!primero.isTerminated() || !segundo.isTerminated()); i++) c.stepSimulation();
            if (!primero.isTerminated() || !segundo.isTerminated()
                    || c.getFileSystemManager().findNode("/sin-swap") == null
                    || pc.getMemoryManager().getUsedMemory() != 0 || pc.getSuspensionCount() != 0)
                throw new AssertionError("Se perdió un proceso, archivo o memoria al esperar");
            System.out.println("PASS: sin swap, admisión diferida y recuperación de RAM");
        } finally { c.reset(); }
    }
}
