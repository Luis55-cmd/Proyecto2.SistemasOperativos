package com.avilaos.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.HierarchyEvent;
import javax.swing.JComponent;
import javax.swing.Timer;

/** Reloj decorativo: la manecilla gira únicamente durante la simulación activa. */
final class RelojAnimado extends JComponent {
    private final Timer animacion;
    private boolean activo;
    private double angulo;
    private long ultimoInstante;

    RelojAnimado() {
        setName("relojAnimado");
        setPreferredSize(new Dimension(90, 90));
        setMinimumSize(new Dimension(70, 70));
        setToolTipText("Reloj de la simulación");
        animacion = new Timer(40, e -> avanzar());
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) actualizarAnimacion();
        });
    }

    void setActivo(boolean activo) {
        if (this.activo == activo) return;
        this.activo = activo;
        actualizarAnimacion();
        repaint();
    }

    boolean estaActivo() { return activo; }

    private void actualizarAnimacion() {
        if (activo && isShowing()) {
            if (!animacion.isRunning()) {
                ultimoInstante = System.nanoTime();
                animacion.start();
            }
        } else {
            animacion.stop();
        }
    }

    private void avanzar() {
        long ahora = System.nanoTime();
        angulo = (angulo + (ahora - ultimoInstante) / 1_000_000_000.0 * Math.PI / 2) % (2 * Math.PI);
        ultimoInstante = ahora;
        repaint();
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int lado = Math.min(getWidth(), getHeight()) - 10;
            int x = (getWidth() - lado) / 2, y = (getHeight() - lado) / 2;
            int cx = x + lado / 2, cy = y + lado / 2;
            g.setColor(PanelPrincipal.PAPEL); g.fillOval(x, y, lado, lado);
            g.setColor(PanelPrincipal.ACENTO); g.setStroke(new BasicStroke(3f)); g.drawOval(x, y, lado, lado);
            g.setStroke(new BasicStroke(2f));
            for (int i = 0; i < 12; i++) {
                double a = i * Math.PI / 6;
                int r1 = lado * 38 / 100, r2 = lado * 43 / 100;
                g.drawLine(cx + (int)(Math.sin(a)*r1), cy - (int)(Math.cos(a)*r1),
                           cx + (int)(Math.sin(a)*r2), cy - (int)(Math.cos(a)*r2));
            }
            g.setColor(PanelPrincipal.TINTA); g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(cx, cy, cx + (int)(Math.sin(angulo / 12)*lado*0.22), cy - (int)(Math.cos(angulo / 12)*lado*0.22));
            g.setColor(PanelPrincipal.ACENTO); g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(cx, cy, cx + (int)(Math.sin(angulo)*lado*0.34), cy - (int)(Math.cos(angulo)*lado*0.34));
            g.fillOval(cx-3, cy-3, 6, 6);
        } finally { g.dispose(); }
    }
}
