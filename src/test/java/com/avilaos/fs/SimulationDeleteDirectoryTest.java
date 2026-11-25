package com.avilaos.fs;

import com.avilaos.core.model.*;
import com.avilaos.filesystem.FilePermission;
import com.avilaos.filesystem.FsNodeType;
import com.avilaos.simulation.SimulationController;

public class SimulationDeleteDirectoryTest {
    public static void main(String[] args) throws Exception {
        System.out.println("Iniciando SimulationDeleteDirectoryTest...");
        SimulationController controller = SimulationController.getInstance();
        controller.reset();
        controller.createComputer("PC1", 1024, SchedulingPolicyType.FCFS, 1);
        
        var fs = controller.getFileSystemManager();
        var dir = fs.createDirectory("/usuarios", "admin", FilePermission.PUBLIC, "SYSTEM");
        var file = fs.createFile("/usuarios/video.mp4", 5, "admin", FilePermission.PUBLIC, "SYSTEM", 1);
        
        System.out.println("Directorio y archivo creados:");
        System.out.println("findNode(/usuarios): " + fs.findNode("/usuarios"));
        System.out.println("findNode(/usuarios/video.mp4): " + fs.findNode("/usuarios/video.mp4"));
        
        // Request delete
        ProcessControlBlock p = controller.requestDelete("/usuarios");
        System.out.println("Proceso creado: " + p.getId() + ", estado: " + p.getState());
        
        // Run steps until process completes
        int maxCycles = 50;
        int cycle = 0;
        while (cycle < maxCycles && !p.isTerminated()) {
            controller.stepSimulation();
            cycle++;
            System.out.println("Ciclo " + cycle + " | Estado proc: " + p.getState() 
                    + " | PC: " + p.getProgramCounter() 
                    + " | Instr rem: " + p.getRemainingTime() 
                    + " | Disk head: " + controller.getDisk().getHeadPosition()
                    + " | Disk active: " + controller.getDisk().getActiveRequest());
        }
        
        System.out.println("Finalizado en ciclo " + cycle + ", p terminado: " + p.isTerminated());
        System.out.println("findNode(/usuarios): " + fs.findNode("/usuarios"));
        System.out.println("findNode(/usuarios/video.mp4): " + fs.findNode("/usuarios/video.mp4"));
        
        if (fs.findNode("/usuarios") != null || fs.findNode("/usuarios/video.mp4") != null) {
            throw new AssertionError("FALLO: El directorio o sus archivos no fueron eliminados!");
        }
        System.out.println("PASS: SimulationDeleteDirectoryTest completado con éxito!");
    }
}
