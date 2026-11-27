package com.avilaos.gui;

import com.avilaos.simulation.SimulationController;
import java.nio.file.Path;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

/** La pestaña de disco debe mostrar la solicitud activa y las que siguen en cola. */
public class ColaDiscoSimulacionTest {
    public static void main(String[] args) throws Exception {
        var c = SimulationController.getInstance();
        c.reset();
        try {
            c.loadFileSystemJson(Path.of("escenarios/carga-json/01-sistema-enunciado.json").toFile());
            c.loadProcessesConfigJson(Path.of("escenarios/carga-json/05-procesos-enunciado.json").toFile());
            VistaSimulacionAlternativa[] vista = new VistaSimulacionAlternativa[1];
            SwingUtilities.invokeAndWait(() -> vista[0] = new VistaSimulacionAlternativa(c));
            for (int i = 0; i < 3; i++) c.stepSimulation();
            GuiTestDrain.await();
            SwingUtilities.invokeAndWait(() -> {
                try {
                    var campo = VistaSimulacionAlternativa.class.getDeclaredField("modeloColaDiscoResumen");
                    campo.setAccessible(true);
                    var modelo = (DefaultTableModel) campo.get(vista[0]);
                    boolean activa = false, pendiente = false;
                    for (int i = 0; i < modelo.getRowCount(); i++) {
                        String estado = modelo.getValueAt(i, 0).toString();
                        activa |= estado.equals("En atención");
                        pendiente |= estado.startsWith("En cola");
                    }
                    if (!activa || !pendiente) throw new AssertionError("No aparecen solicitud activa y pendiente");
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            });
            System.out.println("PASS: la tabla muestra solicitud activa y pendientes");
        } finally { c.reset(); }
    }
}
