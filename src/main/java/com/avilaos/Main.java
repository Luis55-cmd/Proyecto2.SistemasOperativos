package com.avilaos;

import com.avilaos.gui.PanelPrincipal;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/** Punto de entrada de la interfaz Swing; ejecutable desde NetBeans/Maven. */
public class Main {

    /**
     * Entrada: args (no utilizados). Salida: void.
     * Efecto: programa la creación de una única ventana en el EDT de Swing.
     * Conexiones: JVM → main → invokeLater → JFrame + PanelPrincipal.
     * Sin condiciones ni ciclos propios. Cerrar la ventana termina la aplicación.
     * Requiere un entorno gráfico; no ejecuta el motor del simulador.
     */
    public static void main(String[] args) {
        // Swing exige crear y actualizar la GUI en su hilo de eventos, no en el hilo main.
        SwingUtilities.invokeLater(() -> {
            JFrame ventana = new JFrame("AvilaOS");
            ventana.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            ventana.setContentPane(new PanelPrincipal());
            ventana.setMinimumSize(new java.awt.Dimension(1060, 760));
            ventana.setSize(1180, 820);
            ventana.setLocationRelativeTo(null);
            ventana.setVisible(true);
        });
    }
}
