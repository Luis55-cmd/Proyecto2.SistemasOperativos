package com.avilaos.gui;

import com.avilaos.gui.PanelMetricasCopia;
import com.avilaos.simulation.SimulationController;
import com.avilaos.core.model.*;
import com.avilaos.filesystem.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.lang.reflect.Field;
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
   if(table.getRowCount()!=7 || ((Number)table.getValueAt(0,1)).intValue()!=1) throw new AssertionError("Tabla FIFO: "+table.getValueAt(0,1));
   var select=(JComboBox<?>)get(panel,"selectorMetrica");
   for(int i=0;i<select.getItemCount();i++){select.setSelectedIndex(i); panel.actualizarMetricas();}
   System.out.println("PASS: tabla de disco, una solicitud FIFO y "+select.getItemCount()+" métricas seleccionables sin pantalla");
  }catch(Exception ex){throw new RuntimeException(ex);}});
  c.reset();
 }
}
