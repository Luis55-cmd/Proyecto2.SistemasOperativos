package com.avilaos.sync;
import com.avilaos.core.model.*;
import com.avilaos.core.structures.MyQueue;

public class ProtocolCleanupTest {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        for (ProcessType type : new ProcessType[]{ProcessType.PRODUCER, ProcessType.CONSUMER}) {
            for (int stop = 1; stop <= 6; stop++) {
                var b = new Buffer(null,null,null,1,1,1,new MyQueue<>(),1);
                if (type == ProcessType.CONSUMER) {
                    b.getVacios().tryAcquire(); b.produce(99); b.getLlenos().release();
                }
                var p = new ProcessControlBlock();
                p.setComputerId(1); p.setType(type); p.setRequiredElements(2); p.setCyclesPerOperation(1);
                p.setState(ProcessState.EJECUTANDO);
                var driver = new ProducerConsumerDriver(0);
                for (int tick=0; tick<stop; tick++) driver.executeCycle(p,b);
                int pc = p.getProgramCounter();
                p.setState(ProcessState.SALIENTE);
                b.cleanupPermits(p); b.cleanupPermits(p);
                check(b.getMutex().availablePermits()==1, "MUTEX: liberación exacta");
                check(b.getLlenos().availablePermits()==b.getDatos().size(), "LLENOS: refleja operación real");
                check(b.getVacios().availablePermits()+b.getDatos().size()==1, "VACIOS: capacidad conservada");
                check(p.getPermitBufferId()==-1 && p.getProgramCounter()==pc, "PCB: limpio sin instrucciones extra");
            }
        }
        System.out.println("PASS ProtocolCleanupTest: 12 puntos de cancelación");
    }
}
