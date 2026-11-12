package com.avilaos.gui;

import com.avilaos.core.model.ProcessType;
import com.avilaos.core.model.SchedulingPolicyType;
import com.avilaos.simulation.SimulationController;
import java.lang.reflect.Field;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/** Verifica que ambas vistas observan el mismo modelo y los controles alcanzan el núcleo. */
public class VistaSimulacionAlternativaTest {
    private static final SimulationController c=SimulationController.getInstance();
    private static VistaSimulacionAlternativa vista;
    private static Object campo(String nombre) {
        try { Field f=VistaSimulacionAlternativa.class.getDeclaredField(nombre); f.setAccessible(true); return f.get(vista); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void check(boolean valor,String mensaje) { if(!valor) throw new AssertionError(mensaje); }
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            c.reset();
            var a=c.createComputer("Host",128,SchedulingPolicyType.FCFS);
            c.createComputer("Remoto",128,SchedulingPolicyType.ROUND_ROBIN,2);
            c.createBuffer(a.getId(),2,3);
            c.createProcess("Consumidor",a.getId(),ProcessType.CONSUMER,6,10,1,100,"1",1,1);
            var panel=new PanelPrincipal();
            panel.mostrarPanel(PanelPrincipal.Destino.SIMULACION);
            vista=(VistaSimulacionAlternativa) CreacionComputadoresGuiTest.buscar(panel,VistaSimulacionAlternativa.class,null);
            check(vista!=null,"No existe la vista alternativa");
            var tabs=(JTabbedPane) campo("vistas");
            check(tabs.getTabCount()==3 && tabs.getTitleAt(0).equals("Resumen"),"Pestañas");
            var modelo=(DefaultTableModel) campo("modeloBuffers");
            check(modelo.getRowCount()==1 && modelo.getValueAt(0,0).equals(1) && modelo.getValueAt(0,4).equals(3),"Buffer en resumen");
            c.stepSimulation();
        });
        SwingUtilities.invokeAndWait(() -> {
            var espera=(JTextArea) campo("esperaBuffers");
            check(espera.getText().contains("Consumidor"),"No se visualiza proceso bloqueado en semáforo");
            ((JSpinner) campo("latencia")).setValue(7);
            ((JButton) campo("aplicarLatencia")).doClick();
        });
        GuiTestDrain.await();
        check(c.getBuffers().get(0).getLatency()==7,"No aplicó latencia seleccionada");
        SwingUtilities.invokeAndWait(() -> {
            var tabs=(JTabbedPane) campo("vistas");
            check(tabs.getTabCount()==3,"Cambió la topología de vistas inesperadamente");
            c.createProcess("Productor",1,ProcessType.PRODUCER,6,10,1,100,"1",1,1);
            for(int i=0;i<4;i++) c.stepSimulation();
        });
        SwingUtilities.invokeAndWait(() -> {
            var detalle=((JTextArea) campo("esperaBuffers")).getText();
            check(c.snapshot().buffers.get(0).datos().size()==1 && detalle.contains("Contenido: ["), "No muestra contenido del buffer");
            var reiniciar=(JButton) CreacionComputadoresGuiTest.buscar(vista,JButton.class,"Reiniciar todo");
            check(reiniciar!=null,"Falta botón de reinicio");
            reiniciar.doClick();
        });
        GuiTestDrain.await();
        SwingUtilities.invokeAndWait(() -> {
            check(c.getCurrentCycle()==0 && c.getComputers().isEmpty(),"El botón no reinició la simulación");
            check(((DefaultTableModel) campo("modeloBuffers")).getRowCount()==0,"Reset no limpió buffers");
            check(((JTabbedPane) campo("vistas")).getTabCount()==1,"Reset no limpió computadores");
            check(!((JButton) campo("iniciar")).isEnabled() && !((JButton) campo("paso")).isEnabled(), "Con 0 computadores no debe permitir iniciar");
            c.createComputer("Unico", 128, SchedulingPolicyType.FCFS);
            vista.revalidate();
        });
        SwingUtilities.invokeAndWait(() -> {
            check(!((JButton) campo("iniciar")).isEnabled() && !((JButton) campo("paso")).isEnabled(), "Con 1 computador no debe permitir iniciar");
            c.createComputer("Segundo", 128, SchedulingPolicyType.FCFS);
        });
        SwingUtilities.invokeAndWait(() -> {
            check(((JButton) campo("iniciar")).isEnabled() && ((JButton) campo("paso")).isEnabled(), "Con 2 computadores debe permitir iniciar");
        });
        System.out.println("PASS: vista paralela, buffers, espera, latencia y reinicio");
        System.exit(0);
    }
}
