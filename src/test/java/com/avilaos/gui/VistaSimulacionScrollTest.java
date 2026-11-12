package com.avilaos.gui;

import com.avilaos.core.model.SchedulingPolicyType;
import com.avilaos.core.model.ProcessType;
import com.avilaos.simulation.SimulationController;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseWheelEvent;
import java.lang.reflect.Field;
import javax.swing.*;

/** La rueda sobre una tabla debe mover el scroll vertical de su vista exterior. */
public class VistaSimulacionScrollTest {
    private static Object campo(Object objeto, String nombre) {
        try { Field f=objeto.getClass().getDeclaredField(nombre); f.setAccessible(true); return f.get(objeto); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void layout(Container c) {
        c.doLayout();
        for (Component x:c.getComponents()) if (x instanceof Container) layout((Container)x);
    }
    private static void rueda(Component elemento) {
        elemento.dispatchEvent(new MouseWheelEvent(elemento, MouseWheelEvent.MOUSE_WHEEL,
                System.currentTimeMillis(), 0, 5, 5, 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 1));
    }
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var c=SimulationController.getInstance(); c.reset();
            var pc=c.createComputer("Equipo",128,SchedulingPolicyType.FCFS);
            for (int i=0;i<30;i++) c.createBuffer(pc.getId(),1,0);
            for (int i=0;i<30;i++) c.createProcess("P"+i,pc.getId(),ProcessType.CPU_BOUND,10,1,1,100);
            var principal=new PanelPrincipal(); principal.mostrarPanel(PanelPrincipal.Destino.SIMULACION);
            principal.setSize(1000,760); layout(principal);
            var vista=(VistaSimulacionAlternativa) CreacionComputadoresGuiTest.buscar(principal,VistaSimulacionAlternativa.class,null);
            var tabs=(JTabbedPane) campo(vista,"vistas");
            var exterior=(JScrollPane) tabs.getComponentAt(0);
            var interior=(JScrollPane) campo(vista,"scrollBuffers");
            var tabla=(JTable) campo(vista,"buffers");
            var barra=exterior.getVerticalScrollBar();
            if (barra.getMaximum()-barra.getVisibleAmount()<=0) throw new AssertionError("El resumen no tiene contenido desplazable");
            int antes=barra.getValue(); rueda(tabla);
            if (barra.getValue()<=antes) throw new AssertionError("La rueda sobre la tabla no desplaza el resumen");
            int despues=barra.getValue(); rueda(tabla.getTableHeader());
            if (barra.getValue()<=despues) throw new AssertionError("La rueda sobre el encabezado no desplaza el resumen");
            if (interior.isWheelScrollingEnabled()) throw new AssertionError("La tabla retiene el scroll vertical");
            tabs.setSelectedIndex(1); layout(principal);
            var vistaEquipo=(JScrollPane) tabs.getComponentAt(1);
            var detalle=((Object[])campo(vista,"detalles"))[0];
            var colas=(JScrollPane[]) campo(detalle,"tablasColas");
            var listos=(JTable) colas[1].getViewport().getView();
            var barraEquipo=vistaEquipo.getVerticalScrollBar();
            if (barraEquipo.getMaximum()-barraEquipo.getVisibleAmount()<=0) throw new AssertionError("El detalle no tiene contenido desplazable");
            int inicioEquipo=barraEquipo.getValue(); rueda(listos);
            if (barraEquipo.getValue()<=inicioEquipo) throw new AssertionError("La rueda sobre la cola no desplaza el computador");
            System.out.println("PASS: rueda sobre buffers, encabezado y colas desplaza la vista exterior");
            c.reset();
        });
        System.exit(0);
    }
}
