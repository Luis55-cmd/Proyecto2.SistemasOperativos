package com.avilaos.simulation;

import com.avilaos.core.model.IOOperationType;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;

/** Los errores de configuración deben detectarse antes de sustituir la sesión. */
public class ConfiguracionAtomicaTest {
    public static void main(String[] args) throws Exception {
        var c = SimulationController.getInstance();
        c.reset();
        try {
            String base = Files.readString(Path.of("escenarios/carga-json/05-procesos-enunciado.json"));
            c.loadFileSystemJson(Path.of("escenarios/carga-json/01-sistema-enunciado.json").toFile());
            c.loadProcessesConfigJson(base);
            var disco = c.getDisk();
            var computador = c.getComputers().get(0);
            int procesos = c.getAllProcesses().size();
            int ciclo = c.getCurrentCycle();

            var memoria = JsonParser.parseString(base).getAsJsonObject();
            memoria.getAsJsonArray("processes").get(0).getAsJsonObject().addProperty("memory", 999999);
            rechazar(c, memoria.toString(), "memoria");
            conservar(c, disco, computador, procesos, ciclo);

            var sinSwap = JsonParser.parseString(base).getAsJsonObject();
            sinSwap.getAsJsonObject("globalConfig").addProperty("swapSizeBlocks", 0);
            disco.setHeadPosition(disco.getTotalBlocks());
            rechazar(c, sinSwap.toString(), "cabezal");
            conservar(c, disco, computador, procesos, ciclo);
            if (disco.getHeadPosition() != disco.getTotalBlocks()) throw new AssertionError("Cabezal alterado");

            disco.setHeadPosition(0);
            disco.enqueueRequest(null, IOOperationType.READ, "/pendiente", 4, 1, 0);
            rechazar(c, sinSwap.toString(), "solicitudes");
            conservar(c, disco, computador, procesos, ciclo);
            if (disco.getQueueSize() != 1) throw new AssertionError("Solicitud descartada");
            System.out.println("PASS: RAM excesiva y cambio de swap inseguro rechazan sin sustituir la sesión");
        } finally { c.reset(); }
    }
    private static void rechazar(SimulationController c, String json, String texto) {
        try {
            c.loadProcessesConfigJson(json);
            throw new AssertionError("Configuración inválida aceptada: " + texto);
        } catch (IllegalArgumentException | IllegalStateException expected) {
            if (!expected.getMessage().contains(texto)) throw expected;
        }
    }
    private static void conservar(SimulationController c, Object disco, Object computador, int procesos, int ciclo) {
        if (c.getDisk() != disco || c.getComputers().get(0) != computador
                || c.getAllProcesses().size() != procesos || c.getCurrentCycle() != ciclo)
            throw new AssertionError("La sesión cambió después de rechazar la configuración");
    }
}
