package com.avilaos.simulation;

import com.avilaos.simulation.SimulationController;
import java.io.File;

public class JsonCargaEscenariosTest {
    static final String DIR_BASE = "escenarios/carga-json/";
    static final String DIR_FS = "escenarios/carga-json/sistema-archivos/";
    static final String DIR_PROC = "escenarios/carga-json/procesos/";

    static File resolveFsFile(String name) {
        File f = new File(DIR_FS + name);
        if (f.exists()) return f;
        return new File(DIR_BASE + name);
    }

    static File resolveProcFile(String name) {
        File f = new File(DIR_PROC + name);
        if (f.exists()) return f;
        return new File(DIR_BASE + name);
    }

    static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }

    public static void main(String[] args) throws Exception {
        var c = SimulationController.getInstance();

        // 1. Sistemas de archivos válidos
        for (String name : new String[]{"01-sistema-enunciado.json", "02-disco-vacio.json",
                "10-disco-ocupacion-agrupada.json", "11-disco-lleno.json", "12-disco-disperso.json", "13-arbol-complejo.json"}) {
            c.reset();
            c.loadFileSystemJson(resolveFsFile(name));
            check(c.isFileSystemJsonLoaded(), name);
            if (name.startsWith("11-")) check(c.getDisk().getFreeDataBlocks() == 0, "disco lleno");
            if (name.startsWith("12-")) check(c.getDisk().getFreeDataBlocks() == 178, "disco disperso: 22 bloques ocupados");
            System.out.println("PASS FS válido " + name);
        }

        // 2. Sistemas de archivos inválidos (rechazo seguro)
        for (String name : new String[]{"03-cabezal-invalido.json", "04-sin-sistema-archivos.json"}) {
            c.reset();
            c.loadFileSystemJson(resolveFsFile("13-arbol-complejo.json"));
            var disk = c.getDisk();
            var fs = c.getFileSystemManager();
            boolean rejected = false;
            try {
                c.loadFileSystemJson(resolveFsFile(name));
            } catch (Exception e) {
                rejected = true;
            }
            check(rejected && c.getDisk() == disk && c.getFileSystemManager() == fs, "rechazo y preservación " + name);
            System.out.println("PASS FS inválido sin cambios " + name);
        }

        // 3. Procesos y configuración válidos (incluyendo 15-sstf-caso-p2.json)
        for (String name : new String[]{"05-procesos-enunciado.json", "06-procesos-vacios.json", "09-cambio-fcfs-prioridades.json", "15-sstf-caso-p2.json"}) {
            c.reset();
            c.loadFileSystemJson(resolveFsFile("01-sistema-enunciado.json"));
            c.loadProcessesConfigJson(resolveProcFile(name));
            check(c.isProcessesJsonLoaded(), name);
            System.out.println("PASS procesos válido " + name);
        }

        // 4. Procesos inválidos (rechazo seguro)
        for (String name : new String[]{"07-sin-computador.json", "08-json-malformado.json"}) {
            c.reset();
            c.loadFileSystemJson(resolveFsFile("01-sistema-enunciado.json"));
            c.loadProcessesConfigJson(resolveProcFile("05-procesos-enunciado.json"));
            var disk = c.getDisk();
            var pc = c.getComputers().get(0);
            int count = c.getAllProcesses().size();
            boolean rejected = false;
            try {
                c.loadProcessesConfigJson(resolveProcFile(name));
            } catch (Exception e) {
                rejected = true;
            }
            check(rejected && c.getDisk() == disk && c.getComputers().get(0) == pc && c.getAllProcesses().size() == count,
                    "rechazo y preservación " + name);
            System.out.println("PASS procesos inválido sin cambios " + name);
        }

        c.reset();
        System.out.println("TODOS LOS ESCENARIOS PROBADOS Y APROBADOS.");
    }
}
