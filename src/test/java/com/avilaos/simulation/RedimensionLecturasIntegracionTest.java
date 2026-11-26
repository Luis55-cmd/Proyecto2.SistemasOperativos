package com.avilaos.simulation;

import com.avilaos.simulation.SimulationController;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
public class RedimensionLecturasIntegracionTest {
 public static void main(String[] args) throws Exception {
  var c=SimulationController.getInstance(); c.reset();
  try {
   c.loadFileSystemJson(Files.readString(Path.of("escenarios/carga-json/01-sistema-enunciado.json")));
   var json=JsonParser.parseString(Files.readString(Path.of("escenarios/carga-json/05-procesos-enunciado.json"))).getAsJsonObject();
   var ps=json.getAsJsonArray("processes"); while(ps.size()>2) ps.remove(2);
   c.loadProcessesConfigJson(json.toString());
   for(int i=0;i<4;i++) c.stepSimulation();
   if(c.getDisk().getActiveRequest()==null || c.getDisk().getQueueSize()==0) throw new AssertionError("Faltan lecturas en curso");
   c.reconfigurarDisco(220,100,true);
   for(int i=0;i<200 && (!c.getAllProcesses().get(0).isTerminated() || !c.getAllProcesses().get(1).isTerminated());i++) c.stepSimulation();
   if(!c.getAllProcesses().get(0).isTerminated() || !c.getAllProcesses().get(1).isTerminated()) throw new AssertionError("Procesos no finalizaron");
   System.out.println("PASS: procesos de lectura finalizan tras redimensionar con E/S activa y en cola");
  } finally {c.reset();}
 }
}
