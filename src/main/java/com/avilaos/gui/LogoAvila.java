package com.avilaos.gui;

import java.awt.*;
import java.awt.geom.Path2D;
import javax.swing.JPanel;

/** Marca vectorial inspirada en el perfil del Ávila; no es un perfil topográfico exacto. */
final class LogoAvila extends JPanel {
    LogoAvila() {
        setOpaque(false);
        setPreferredSize(new Dimension(176, 118));
        setMaximumSize(new Dimension(176, 118));
        setMinimumSize(new Dimension(176, 118));
    }

    /**
     * Responsabilidad: pintar montaña y nombre. Entrada: contexto gráfico de Swing.
     * Salida: dibujo; sin estado de dominio ni efectos externos. Swing invoca al repintar.
     * Trabaja sobre una copia del contexto y la libera al terminar. Sin ciclos ni ramas.
     * Caso normal: marca de 176 × 118; un contenedor menor recorta el dibujo.
     */
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(194, 178, 132));
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D perfil = new Path2D.Double();
        perfil.moveTo(1, 39); perfil.lineTo(18, 29); perfil.lineTo(28, 34);
        perfil.lineTo(43, 24); perfil.lineTo(57, 27); perfil.lineTo(76, 16);
        perfil.lineTo(89, 13); perfil.lineTo(100, 20); perfil.lineTo(113, 33);
        perfil.lineTo(133, 29); perfil.lineTo(146, 24); perfil.lineTo(159, 29);
        perfil.lineTo(170, 35); perfil.lineTo(175, 33);
        // Pliegues interiores: conservan el trazo ligero de la referencia.
        perfil.moveTo(12, 48); perfil.lineTo(43, 24);
        perfil.moveTo(35, 47); perfil.lineTo(76, 16);
        perfil.moveTo(58, 44); perfil.lineTo(68, 31); perfil.lineTo(89, 13);
        perfil.moveTo(76, 47); perfil.lineTo(100, 20);
        perfil.moveTo(97, 48); perfil.lineTo(113, 33); perfil.lineTo(126, 47);
        perfil.moveTo(132, 46); perfil.lineTo(146, 24); perfil.lineTo(164, 46);
        g.draw(perfil);
        g.setColor(new Color(248, 244, 228));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
        g.drawString("AvilaOS", 23, 83);
        g.setColor(new Color(192, 181, 148));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
        g.drawString("LABORATORIO DE SISTEMAS", 18, 105);
        g.dispose();
    }
}
