package com.avilaos.simulation;

import com.avilaos.core.model.SchedulingPolicyType;
import com.avilaos.filesystem.FilePermission;
import com.avilaos.filesystem.FsNodeType;
import java.io.File;

/** JSON de archivos más computador y proceso preparados desde interfaz. */
public class ManualPreparationStartTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        var controller = SimulationController.getInstance();
        controller.reset();
        controller.loadFileSystemJson(new File("escenarios/carga-json/13-arbol-complejo.json"));
        check(!controller.isReadyToStartClock(), "sin computador ni procesos no está listo");
        controller.createComputer("C1", 128, SchedulingPolicyType.EDF);
        check(!controller.isReadyToStartClock(), "sin procesos no está listo");
        controller.requestCreation("/compartido/prueba", FsNodeType.DIRECTORY, 1, "admin", FilePermission.PUBLIC);
        check(controller.isReadyToStartClock(), "proceso manual habilita el arranque");
        controller.startSimulation();
        controller.pauseSimulation();
        int cicloAntes = controller.getCurrentCycle();
        controller.stepSimulation();
        check(controller.getCurrentCycle() == cicloAntes + 1, "el reloj puede avanzar");
        for (int i = 0; i < 50 && controller.getFileSystemManager().findNode("/compartido/prueba") == null; i++) controller.stepSimulation();
        check(controller.getFileSystemManager().findNode("/compartido/prueba") != null, "creación completada");
        controller.reset();
        System.out.println("PASS ManualPreparationStartTest");
    }
}
