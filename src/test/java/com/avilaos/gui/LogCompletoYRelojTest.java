package com.avilaos.gui;

import com.avilaos.simulation.SimulationController;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/** Evita pérdida de eventos por saturación o recorte y verifica el estado del reloj. */
public class LogCompletoYRelojTest {
    private static Object campo(Object o, String nombre) {
        try { Field f=o.getClass().getDeclaredField(nombre); f.setAccessible(true); return f.get(o); }
        catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }
    private static void check(boolean valor, String mensaje) { if (!valor) throw new AssertionError(mensaje); }
    public static void main(String[] args) throws Exception {
        SimulationController c=SimulationController.getInstance();
        MonitorSimulacion[] monitor=new MonitorSimulacion[1];
        VistaSimulacionAlternativa[] vista=new VistaSimulacionAlternativa[1];
        CountDownLatch recibido=new CountDownLatch(1);
        SwingUtilities.invokeAndWait(() -> {
            c.reset();
            monitor[0]=new MonitorSimulacion(c,()->{});
            vista[0]=new VistaSimulacionAlternativa(c);
            c.addLogListener(s -> { if (s.contains("FIN-PRUEBA-LOG")) recibido.countDown(); });
        });
        // El EDT no puede procesar la cola hasta que termine este bloque.
        SwingUtilities.invokeAndWait(() -> {
            for(int i=0;i<2600;i++) c.logEvent("PRUEBA-"+i+" "+"x".repeat(50));
            c.logEvent("FIN-PRUEBA-LOG");
        });
        check(recibido.await(30,TimeUnit.SECONDS),"No se drenó la cola de eventos");
        SwingUtilities.invokeAndWait(() -> {
            String anterior=((JTextArea)campo(monitor[0],"areaLog")).getText();
            String nuevo=((JTextArea)campo(vista[0],"eventos")).getText();
            for(int i=0;i<2600;i++) {
                String marca="PRUEBA-"+i+" ";
                check(anterior.contains(marca),"Falta en log anterior: "+i);
                check(nuevo.contains(marca),"Falta en log nuevo: "+i);
            }
            check(nuevo.length()>100000,"La prueba no superó el límite anterior");
            check(!nuevo.contains("eventos omitidos"),"Se omitieron eventos");
            c.startSimulation();
        });
        SwingUtilities.invokeAndWait(() -> {});
        SwingUtilities.invokeAndWait(() -> {
            var reloj=(RelojAnimado)campo(vista[0],"relojAnimado");
            check(reloj.estaActivo(),"El reloj no refleja simulación activa");
            c.pauseSimulation();
        });
        SwingUtilities.invokeAndWait(() -> {});
        SwingUtilities.invokeAndWait(() -> {
            var reloj=(RelojAnimado)campo(vista[0],"relojAnimado");
            check(!reloj.estaActivo(),"El reloj no se pausó");
            c.reset();
            c.logEvent("PRIMER-EVENTO-DE-SESION");
        });
        SwingUtilities.invokeAndWait(() -> {});
        SwingUtilities.invokeAndWait(() -> {
            String nuevo=((JTextArea)campo(vista[0],"eventos")).getText();
            check(nuevo.contains("PRIMER-EVENTO-DE-SESION"), "La nueva sesión perdió su primer evento");
            check(!nuevo.contains("PRUEBA-0 "), "El log conserva la sesión anterior");
        });
        System.out.println("PASS: 2600 eventos completos, cambio de sesión y reloj activo/pausado");
        System.exit(0);
    }
}
