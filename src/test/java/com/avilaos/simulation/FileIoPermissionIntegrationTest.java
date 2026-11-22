package com.avilaos.simulation;

import com.avilaos.core.model.ExecutionMode;
import com.avilaos.core.model.IOOperationType;
import com.avilaos.core.model.SchedulingPolicyType;
import com.avilaos.core.model.UserMode;
import com.avilaos.filesystem.FilePermission;
import com.avilaos.core.structures.MyLinkedList;
import javax.swing.SwingUtilities;

/** Comprueba identidad capturada y cambios del destino mientras espera el disco. */
public class FileIoPermissionIntegrationTest {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        var c = SimulationController.getInstance();
        c.reset();
        var pc = c.createComputer("PC", 32, SchedulingPolicyType.FCFS);
        var fs = c.getFileSystemManager();
        fs.createDirectory("/mio", "user1", FilePermission.PRIVATE, "test");
        fs.createFile("/mio/nota", 1, "user1", FilePermission.PRIVATE, "test", 1);
        c.setMode(UserMode.USER);
        c.getDisk().setHeadPosition(100);
        var read = c.createConfigFileIOProcess("leer", pc.getId(), IOOperationType.READ, "/mio/nota", 0,
                "user1", ExecutionMode.USER, null, 1, 1000);
        check(read.getTotalInstructions() == 2 && read.getAssignedMemory() == 2, "lectura reserva dos instrucciones y dos unidades de RAM");
        c.setMode(UserMode.ADMIN);
        c.stepSimulation();
        check(read.getProgramCounter() == 1 && !read.isTerminated(), "primera instrucción solicita la lectura");
        for (int i = 0; i < 100 && !read.isTerminated(); i++) c.stepSimulation();
        check(read.getProgramCounter() == 2, "segunda instrucción termina tras volver del disco");
        check(read.isTerminated() && c.getDisk().getCompletedRequestsCount() == 1, "lectura termina por disco");

        c.setMode(UserMode.USER);
        c.getDisk().setHeadPosition(100);
        MyLinkedList<String> logs = new MyLinkedList<>();
        c.addLogListener(s -> { synchronized (logs) { logs.append(s); } });
        var stale = c.createConfigFileIOProcess("leer-cambio", pc.getId(), IOOperationType.READ, "/mio/nota", 0,
                "user1", ExecutionMode.USER, null, 1, 1000);
        c.stepSimulation();
        check(stale.isDiskRequestIssued() && !stale.isTerminated(), "lectura encolada y pendiente");
        fs.findNode("/mio/nota").setOwner("otro");
        c.setMode(UserMode.ADMIN);
        for (int i = 0; i < 100 && !stale.isTerminated(); i++) c.stepSimulation();
        boolean rechazado = false;
        for (int intento = 0; intento < 10 && !rechazado; intento++) {
            SwingUtilities.invokeAndWait(() -> { });
            synchronized (logs) {
                for (int i = 0; i < logs.size(); i++) if (logs.get(i).contains("Operación fallida") && logs.get(i).contains("privado")) rechazado = true;
            }
        }
        check(stale.isTerminated() && rechazado, "revalida usando identidad original, aunque cambie la sesión");
        c.reset();
        System.out.println("PASS FileIoPermissionIntegrationTest");
    }
}
