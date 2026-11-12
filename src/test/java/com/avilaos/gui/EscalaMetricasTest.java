package com.avilaos.gui;
import javax.swing.*;
import org.jfree.chart.ChartPanel;
import org.jfree.data.xy.XYSeriesCollection;
import com.avilaos.simulation.SimulationController;
import com.avilaos.core.model.SchedulingPolicyType;

public class EscalaMetricasTest {
 public static void main(String[] args) throws Exception {
  SwingUtilities.invokeAndWait(() -> {
   var c=SimulationController.getInstance();c.reset();
   c.createComputer("A",128,SchedulingPolicyType.FCFS);
   c.createComputer("B",128,SchedulingPolicyType.ROUND_ROBIN);
   var p=new PanelMetricas(c);p.actualizarMetricas();
   var chart=(ChartPanel)CreacionComputadoresGuiTest.buscar(p,ChartPanel.class,null);
   var plot=chart.getChart().getXYPlot();
   var data=(XYSeriesCollection)plot.getDataset();
   for(int i=0;i<2;i++){data.getSeries(i).clear();data.getSeries(i).add(1,20);data.getSeries(i).add(2,30);}
   if(plot.getRangeAxis().getLowerBound()<=0 || plot.getRangeAxis().getUpperBound()>=100)
       throw new AssertionError("ESCALA: ajusta al rango de los datos");
   data.getSeries(0).add(3,100);
   if(plot.getRangeAxis().getUpperBound()<=100)throw new AssertionError("MARGEN: 100 no queda recortado");
   var a=(java.awt.BasicStroke)plot.getRenderer().getSeriesStroke(0);
   var b=(java.awt.BasicStroke)plot.getRenderer().getSeriesStroke(1);
   if(a.getDashArray()!=null || b.getDashArray()==null)throw new AssertionError("TRAZOS: distinguir series coincidentes");
   if(plot.getSeriesRenderingOrder()!=org.jfree.chart.plot.SeriesRenderingOrder.FORWARD)
       throw new AssertionError("ORDEN: discontinua encima de continua");
   System.out.println("PASS EscalaMetricasTest");
  });
 }
}
