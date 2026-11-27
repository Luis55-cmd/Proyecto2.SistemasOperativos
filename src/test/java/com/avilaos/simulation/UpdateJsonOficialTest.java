package com.avilaos.simulation;

import com.avilaos.filesystem.FilePermission;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;

/** UPDATE del ejemplo oficial, sin nombre nuevo, y protección frente a colisiones. */
public class UpdateJsonOficialTest {
    public static void main(String[] args) throws Exception {
        var c = SimulationController.getInstance();
        c.reset();
        try {
            c.loadFileSystemJson(Files.readString(Path.of("escenarios/carga-json/sistema-archivos/01-sistema-enunciado.json")));
            var fs = c.getFileSystemManager();
            fs.createFile("/usuarios/notas_actualizado.txt", 1, "admin", FilePermission.PUBLIC, "test", 1);
            int bloque = fs.findNode("/usuarios/notas.txt").getFirstBlock();
            var cfg = JsonParser.parseString(Files.readString(Path.of("escenarios/carga-json/procesos/05-procesos-enunciado.json"))).getAsJsonObject();
            var procesos = cfg.getAsJsonArray("processes");
            for (int i = procesos.size() - 1; i >= 0; i--) {
                if (!"UPDATE".equals(procesos.get(i).getAsJsonObject().get("operation").getAsString())) procesos.remove(i);
            }
            c.loadProcessesConfigJson(cfg.toString());
            for (int i = 0; i < 500 && (!c.getAllProcesses().get(0).isTerminated() || !c.getAllProcesses().get(1).isTerminated()); i++) c.stepSimulation();
            if (!c.getAllProcesses().get(0).isFileOperationCompleted() || !c.getAllProcesses().get(1).isFileOperationCompleted())
                throw new AssertionError("Los UPDATE oficiales no se completaron");
            var renombrado = fs.findNode("/usuarios/notas_actualizado_2.txt");
            if (renombrado == null || renombrado.getFirstBlock() != bloque || fs.findNode("/usuarios/notas_actualizado.txt") == null
                    || fs.findNode("/usuarios/data_actualizado.csv") == null
                    || !"/usuarios/notas_actualizado_2.txt".equals(c.getDisk().getBlock(bloque).getOccupiedBy()))
                throw new AssertionError("Nombre automático, colisión o referencia de disco incorrectos");
            System.out.println("PASS: UPDATE oficial genera nombres únicos y conserva bloques");
        } finally { c.reset(); }
    }
}
