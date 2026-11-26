package com.avilaos.gui;

import com.avilaos.gui.PanelMetricasCopia;
import com.avilaos.simulation.SimulationController;
import com.avilaos.core.model.*;
import com.avilaos.filesystem.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.lang.reflect.Field;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.renderer.xy.XYStepRenderer;
import org.jfree.data.xy.XYSeriesCollection;
public class MetricasDiscoGuiTest {
 static Object get(Object obj,String name) throws Exception {Field f=obj.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(obj);}
 public static void main(String[] args) throws Exception {
  var c=SimulationController.getInstance(); c.reset();
  var pc=c.createComputer("Prueba",32,SchedulingPolicyType.FCFS);
  c.getFileSystemManager().createFile("/nota",2,"admin",FilePermission.PUBLIC,"test",1);
  var p=c.createConfigFileIOProcess("lector",pc.getId(),IOOperationType.READ,"/nota",0,"admin",ExecutionMode.USER,null,1,1000);
  for(int i=0;i<100 && !p.isTerminated();i++) c.stepSimulation();
  if(!p.isTerminated()) throw new AssertionError("No terminó lectura");
  SwingUtilities.invokeAndWait(()->{try {
   var panel=new PanelMetricasCopia(c);panel.actualizarMetricas();
   var table=(DefaultTableModel)get(panel,"tablaDisco");
   if(table.getRowCount()!=5 || ((Number)table.getValueAt(0,1)).intValue()!=1) throw new AssertionError("Tabla FIFO: "+table.getValueAt(0,1));
   for (int i=0; i<table.getRowCount(); i++) if (table.getValueAt(i,0).toString().contains("LOOK")) throw new AssertionError("Política no visible en disco");
   var select=(JComboBox<?>)get(panel,"selectorMetrica");
   for(int i=0;i<select.getItemCount();i++){select.setSelectedIndex(i); panel.actualizarMetricas();}
   var chart=(JFreeChart)get(panel,"chart");
   select.setSelectedIndex(4);
   if (!(chart.getXYPlot().getRenderer() instanceof XYStepRenderer)) throw new AssertionError("Suspensiones sin escalones");
   select.setSelectedIndex(5);
   if (!(chart.getXYPlot().getRenderer() instanceof XYStepRenderer)) throw new AssertionError("Reactivaciones sin escalones");
   select.setSelectedIndex(8);
   var series=(XYSeriesCollection)get(panel,"dataset");
   if(series.getSeries("SSTF (espera)").getY(series.getSeries("SSTF (espera)").getItemCount()-1)!=null)
     throw new AssertionError("SSTF sin solicitudes muestra espera cero");
   System.out.println("PASS: tabla de disco, una solicitud FIFO y "+select.getItemCount()+" métricas seleccionables sin pantalla");
  }catch(Exception ex){throw new RuntimeException(ex);}});
  c.reset();
 }
}
