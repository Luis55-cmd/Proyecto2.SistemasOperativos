package com.avilaos.gui;
import com.avilaos.simulation.SimulationController;
import javax.swing.SwingUtilities;
import java.io.File;

/** Comprueba eventos de decisiones con el escenario de integración. */
public class EventosLogTest {
 public static void main(String[] args) throws Exception {
  var c=SimulationController.getInstance(); var m=new MonitorSimulacion(c,()->{});
  StringBuilder log=new StringBuilder(); c.addLogListener(s -> log.append(s).append('\n'));
  if(!m.cargarEstado(new File(args[0]))) throw new AssertionError("Carga fallida");
  for(int i=0;i<80;i++) { c.stepSimulation(); SwingUtilities.invokeAndWait(() -> {}); }
  String text=log.toString();
  String[] expected={"selecciona", "desaloja", "se bloqueó: latencia de red", "sin elementos (semáforo llenos)", "despertó y pasó a listos", "se bloqueó: espera de E/S", "terminó por vencimiento de deadline", "terminó normalmente", "admitido desde nuevos tras liberarse RAM", "CPU al finalizar ciclo"};
  for(String e:expected) if(!text.contains(e)) { System.err.println(text); throw new AssertionError("Falta evento: "+e); }
  String terminado = "Todos los procesos terminaron; las CPUs están inactivas. El reloj continúa.";
  if (!text.contains("Todas las CPUs están inactivas; puede haber procesos pendientes.")
          || text.indexOf(terminado) < 0 || text.indexOf(terminado) != text.lastIndexOf(terminado))
      throw new AssertionError("La inactividad debe distinguir pendientes y anunciar terminación una vez");
  c.reset(); SwingUtilities.invokeAndWait(() -> {}); log.setLength(0);
  c.createComputer("Reloj", 64, com.avilaos.core.model.SchedulingPolicyType.FCFS, 1);
  c.setCycleDurationMs(1000); SwingUtilities.invokeAndWait(() -> {}); log.setLength(0);
  c.startSimulation();
  long limite=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
  while(c.getCurrentCycle()<1 && System.nanoTime()<limite) Thread.sleep(5);
  c.pauseSimulation(); SwingUtilities.invokeAndWait(() -> {});
  String inicio=log.toString();
  int start=inicio.indexOf("Simulación iniciada"), tick=inicio.indexOf("[Ciclo 1]");
  if(start<0 || tick<0 || start>tick) throw new AssertionError("El inicio debe registrarse antes del primer tick: " + inicio);
  System.out.println("PASS: eventos de proceso y orden de inicio del reloj");
 }
}
