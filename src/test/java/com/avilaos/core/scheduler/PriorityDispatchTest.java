package com.avilaos.core.scheduler;

import com.avilaos.core.model.*;
import com.avilaos.core.structures.MyQueue;

public class PriorityDispatchTest {
    private static ProcessControlBlock p(String name, int priority) {
        var p = new ProcessControlBlock(name, 1, ProcessType.CPU_BOUND, 6, 8, priority, 100);
        p.setState(ProcessState.LISTO);
        return p;
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        var scheduler = new PreemptivePriorityScheduler();
        var queue = new MyQueue<ProcessControlBlock>();
        var low = p("Baja",1); var high = p("Alta",10); var middle = p("Media",5);
        queue.enqueue(low); queue.enqueue(high); queue.enqueue(middle);
        check(scheduler.scheduleNext(queue,null) == high, "INICIAL: seleccionar Alta antes que Baja");
        check(queue.size()==2 && queue.dequeue()==middle && queue.dequeue()==low,
                "COLA: remanente ordenado sin duplicados");

        middle.setState(ProcessState.EJECUTANDO);
        middle.setProgramCounter(2); middle.setMemoryAddressRegister(1);
        high.setState(ProcessState.LISTO);
        queue.enqueue(low); queue.enqueue(high);
        check(scheduler.scheduleNext(queue,middle)==high, "APROPIACION: candidato oculto tras Baja");
        check(queue.size()==2 && queue.dequeue()==middle && queue.dequeue()==low
                && middle.getProgramCounter()==2 && middle.getMemoryAddressRegister()==1,
                "CONTEXTO: conserva registros y devuelve una sola vez");

        var equal = p("Igual",10); queue.enqueue(equal);
        check(scheduler.scheduleNext(queue,high)==high && queue.size()==1,
                "EMPATE: no desalojar por prioridad igual");
        queue.dequeue();
        check(scheduler.scheduleNext(queue,high)==high, "VACIA: mantener actual");
        check(scheduler.scheduleNext(queue,null)==null, "VACIA: CPU libre");

        var inverse = new PreemptivePriorityScheduler(false);
        high.setState(ProcessState.LISTO); low.setState(ProcessState.LISTO);
        queue.enqueue(high); queue.enqueue(low);
        check(inverse.scheduleNext(queue,null)==low, "INVERSA: respetar convenio configurado");
        System.out.println("PASS PriorityDispatchTest");
    }
}
