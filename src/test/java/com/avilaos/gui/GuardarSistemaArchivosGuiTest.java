package com.avilaos.gui;

import com.avilaos.filesystem.FilePermission;
import com.avilaos.simulation.SimulationController;
import java.nio.file.Files;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

/** Ejercita el guardado de Inicio sin abrir el selector de archivos. */
public class GuardarSistemaArchivosGuiTest {
    public static void main(String[] args) throws Exception {
        var c = SimulationController.getInstance();
        c.reset();
        var ruta = Files.createTempFile("avila-guardar-gui-", ".json");
        try {
            c.getFileSystemManager().createFile("/nota", 2, "admin", FilePermission.PRIVATE, "prueba", 1);
            PanelPrincipal[] panel = new PanelPrincipal[1];
            JButton[] boton = new JButton[1];
            SwingUtilities.invokeAndWait(() -> {
                panel[0] = new PanelPrincipal();
                boton[0] = (JButton) CreacionComputadoresGuiTest.buscar(panel[0], JButton.class, "Guardar JSON");
                if (boton[0] == null || !boton[0].isEnabled()) throw new AssertionError("Botón de archivos deshabilitado");
                panel[0].aplicarGuardadoSistemaArchivos(ruta.toFile(), boton[0]);
            });
            GuiTestDrain.await();
            SwingUtilities.invokeAndWait(() -> {
                if (!boton[0].isEnabled()) throw new AssertionError("Botón no reactivado");
                try {
                    var campo = PanelPrincipal.class.getDeclaredField("estadoCargaArchivos");
                    campo.setAccessible(true);
                    if (!((JLabel) campo.get(panel[0])).getText().contains("guardado correctamente"))
                        throw new AssertionError("No se informó el resultado");
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            });
            c.loadFileSystemJson(ruta.toFile());
            var archivo = c.getFileSystemManager().findNode("/nota");
            if (archivo == null || archivo.getSizeBlocks() != 2 || archivo.getPermission() != FilePermission.PRIVATE)
                throw new AssertionError("El JSON guardado no conserva el archivo");
            System.out.println("PASS: Inicio guarda y vuelve a cargar el sistema de archivos");
        } finally {
            Files.deleteIfExists(ruta);
            c.reset();
        }
    }
}
