package com.avilaos.simulation;

import com.avilaos.core.model.IOOperationType;
import java.nio.file.Files;
import java.nio.file.Path;

/** Impide abandonar E/S y swap durante la sustitución de la sesión. */
public class CargaSeguraDiscoTest {
    public static void main(String[] args) throws Exception {
        var c = SimulationController.getInstance();
        var rutaFs = Path.of("escenarios/carga-json/01-sistema-enunciado.json");
        var rutaProcesos = Path.of("escenarios/carga-json/05-procesos-enunciado.json");
        String fs = Files.readString(rutaFs);
        String procesos = Files.readString(rutaProcesos);
        c.reset();
        try {
            c.loadFileSystemJson(fs);
            c.loadProcessesConfigJson(procesos);
            var disco = c.getDisk();
            var arbol = c.getFileSystemManager();
            var computador = c.getComputers().get(0);
            int libres = disco.getFreeSwapBlocks();
            int inicio = disco.allocateSwapBlocks(999, "suspendido", 2);
            if (inicio < 0) throw new AssertionError("No se reservó swap");
            rechazar(() -> c.loadFileSystemJson(fs));
            rechazar(() -> { try { c.loadFileSystemJson(rutaFs.toFile()); } catch (java.io.IOException e) { throw new RuntimeException(e); } });
            rechazar(() -> c.loadProcessesConfigJson(procesos));
            if (c.getDisk() != disco || c.getFileSystemManager() != arbol || c.getComputers().get(0) != computador
                    || disco.getFreeSwapBlocks() != libres - 2)
                throw new AssertionError("La carga fallida alteró swap o sesión");
            disco.freeSwapBlocks(inicio);
            disco.enqueueRequest(null, IOOperationType.READ, "/pendiente", 4, 1, 0);
            rechazar(() -> c.loadFileSystemJson(fs));
            if (c.getDisk() != disco || disco.getQueueSize() != 1)
                throw new AssertionError("La carga fallida descartó la solicitud");
            disco.tick(1);
            rechazar(() -> c.loadFileSystemJson(fs));
            if (c.getDisk() != disco || disco.getActiveRequest() == null)
                throw new AssertionError("La carga fallida descartó la solicitud activa");
            c.reset();
            c.loadFileSystemJson(fs);
            c.loadProcessesConfigJson(procesos);
            var discoEnPausa = c.getDisk();
            c.startSimulation();
            c.pauseSimulation();
            try {
                c.loadFileSystemJson(fs);
                throw new AssertionError("Se aceptó una carga con el reloj iniciado y pausado");
            } catch (IllegalStateException esperado) {
                if (!esperado.getMessage().contains("Detén")) throw esperado;
            }
            if (c.getDisk() != discoEnPausa || !c.isPaused())
                throw new AssertionError("La carga alteró la simulación pausada");
            System.out.println("PASS: carga rechazada con swap ocupado, E/S en cola o E/S activa");
        } finally { c.reset(); }
    }
    private static void rechazar(Runnable accion) {
        try { accion.run(); throw new AssertionError("Se aceptó una carga insegura"); }
        catch (IllegalStateException esperado) {
            if (!esperado.getMessage().contains("swap ocupado")) throw esperado;
        }
    }
}
