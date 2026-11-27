package com.avilaos.simulation;

import com.avilaos.core.model.SchedulingPolicyType;
import com.avilaos.core.structures.MyLinkedList;
import com.avilaos.filesystem.FilePermission;
import com.avilaos.filesystem.FsNodeType;
import javax.swing.SwingUtilities;

public class FileIoFailureTest {
    public static void main(String[] args) throws Exception {
        var c = SimulationController.getInstance(); c.reset();
        var pc = c.createComputer("PC", 8, SchedulingPolicyType.FCFS);
        var file = c.requestCreation("/original", FsNodeType.FILE, 2, "admin", FilePermission.PUBLIC);
        for (int i = 0; i < 200 && !file.isTerminated(); i++) c.stepSimulation();
        MyLinkedList<String> logs = new MyLinkedList<>();
        c.addLogListener(s -> { synchronized (logs) { logs.append(s); } });
        var rename = c.requestRename("/original", "nuevo");
        var read = c.requestRead("/original");
        for (int i = 0; i < 200 && !read.isTerminated(); i++) c.stepSimulation();
        SwingUtilities.invokeAndWait(() -> {});
        boolean failed = false, normal = false;
        synchronized (logs) {
            for (int i = 0; i < logs.size(); i++) {
                String line = logs.get(i);
                if (line.contains("Leer /original") && line.contains("terminó por fallo") && line.contains("La ruta ya no existe")) failed = true;
                if (line.contains("Leer /original") && line.contains("terminó normalmente")) normal = true;
            }
        }
        if (!rename.isTerminated() || !read.isTerminated() || !failed || normal || pc.getMemoryManager().getUsedMemory() != 0)
            throw new AssertionError("La lectura fallida debe informar motivo y liberar RAM sin declararse normal");
        System.out.println("PASS FileIoFailureTest");
    }
}
