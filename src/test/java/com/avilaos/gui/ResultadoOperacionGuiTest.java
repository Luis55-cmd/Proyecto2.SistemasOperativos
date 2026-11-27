package com.avilaos.gui;

import com.avilaos.core.model.SchedulingPolicyType;
import com.avilaos.filesystem.FilePermission;
import com.avilaos.filesystem.FsNodeType;
import com.avilaos.simulation.SimulationController;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/** Una lectura que pierde su ruta no debe anunciarse como completada. */
public class ResultadoOperacionGuiTest {
    public static void main(String[] args) throws Exception {
        var c = SimulationController.getInstance();
        c.reset();
        try {
            c.createComputer("PC", 16, SchedulingPolicyType.FCFS);
            var creado = c.requestCreation("/original", FsNodeType.FILE, 2, "admin", FilePermission.PUBLIC);
            for (int i = 0; i < 200 && !creado.isTerminated(); i++) c.stepSimulation();
            var ok = c.requestRead("/original");
            for (int i = 0; i < 200 && !ok.isTerminated(); i++) c.stepSimulation();
            if (!ok.isFileOperationCompleted()) throw new AssertionError("Lectura exitosa no registrada");
            var renombrar = c.requestRename("/original", "nuevo");
            var leer = c.requestRead("/original");
            for (int i = 0; i < 200 && !leer.isTerminated(); i++) c.stepSimulation();
            if (!renombrar.isFileOperationCompleted() || !leer.isTerminated() || leer.isFileOperationCompleted())
                throw new AssertionError("No se distinguió la lectura fallida");
            PanelPrincipal[] panel = new PanelPrincipal[1];
            SwingUtilities.invokeAndWait(() -> {
                try {
                    panel[0] = new PanelPrincipal();
                    var solicitud = PanelPrincipal.class.getDeclaredField("solicitudArchivo");
                    solicitud.setAccessible(true); solicitud.set(panel[0], leer);
                    var actualizar = PanelPrincipal.class.getDeclaredMethod("actualizarVistaArchivos");
                    actualizar.setAccessible(true); actualizar.invoke(panel[0]);
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            });
            GuiTestDrain.await();
            SwingUtilities.invokeAndWait(() -> {
                try {
                    var campo = PanelPrincipal.class.getDeclaredField("estadoSolicitudArchivo");
                    campo.setAccessible(true);
                    String mensaje = ((JTextArea) campo.get(panel[0])).getText();
                    if (!mensaje.contains("no completada")) throw new AssertionError("Mensaje incorrecto: " + mensaje);
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            });
            System.out.println("PASS: lectura fallida no se informa como completada");
        } finally { c.reset(); }
    }
}
