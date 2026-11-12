package com.avilaos.sync;

import com.avilaos.core.model.ProcessControlBlock;
import com.avilaos.core.model.ProcessState;
import com.avilaos.core.model.ProcessType;
import com.avilaos.core.structures.MyQueue;

/**
 * Valida que los semáforos del Buffer adopten valores negativos
 * según la convención clásica de Stallings cuando encolan procesos bloqueados.
 */
public class SemaforosValoresNegativosTest {

    private static void check(boolean condition, String msg) {
        if (!condition) throw new AssertionError(msg);
    }

    public static void main(String[] args) {
        // Buffer de capacidad 2
        Buffer buffer = new Buffer(null, null, null, 1, 2, 1, new MyQueue<>(), 2);

        // Estado inicial: Mutex=1, Llenos=0, Vacios=2
        check(buffer.getTheoreticalValue(Buffer.SemaphoreKind.MUTEX) == 1, "Inicial Mutex debe ser 1");
        check(buffer.getTheoreticalValue(Buffer.SemaphoreKind.LLENOS) == 0, "Inicial Llenos debe ser 0");
        check(buffer.getTheoreticalValue(Buffer.SemaphoreKind.VACIOS) == 2, "Inicial Vacios debe ser 2");

        // 1. Proceso P1 toma el Mutex
        ProcessControlBlock p1 = new ProcessControlBlock("P1", 1, ProcessType.PRODUCER, 10, 10, 1, 100);
        p1.setState(ProcessState.EJECUTANDO);
        boolean p1Adquirio = buffer.semWait(p1, Buffer.SemaphoreKind.MUTEX);
        check(p1Adquirio, "P1 debe adquirir el mutex");
        check(buffer.getTheoreticalValue(Buffer.SemaphoreKind.MUTEX) == 0, "Mutex ocupado sin cola debe ser 0");

        // 2. Proceso P2 intenta tomar el Mutex y se encola
        ProcessControlBlock p2 = new ProcessControlBlock("P2", 1, ProcessType.PRODUCER, 10, 10, 1, 100);
        p2.setState(ProcessState.EJECUTANDO);
        boolean p2Adquirio = buffer.semWait(p2, Buffer.SemaphoreKind.MUTEX);
        check(!p2Adquirio, "P2 debe bloquearse");
        check(buffer.getTheoreticalValue(Buffer.SemaphoreKind.MUTEX) == -1, "Mutex con 1 esperando debe ser -1");

        // 3. Proceso P3 intenta tomar el Mutex y también se encola
        ProcessControlBlock p3 = new ProcessControlBlock("P3", 1, ProcessType.CONSUMER, 10, 10, 1, 100);
        p3.setState(ProcessState.EJECUTANDO);
        boolean p3Adquirio = buffer.semWait(p3, Buffer.SemaphoreKind.MUTEX);
        check(!p3Adquirio, "P3 debe bloquearse");
        check(buffer.getTheoreticalValue(Buffer.SemaphoreKind.MUTEX) == -2, "Mutex con 2 esperando debe ser -2");

        // 4. Consumidor P4 intenta consumir de un buffer vacío (semWait en LLENOS)
        ProcessControlBlock p4 = new ProcessControlBlock("P4", 1, ProcessType.CONSUMER, 10, 10, 1, 100);
        p4.setState(ProcessState.EJECUTANDO);
        boolean p4Adquirio = buffer.semWait(p4, Buffer.SemaphoreKind.LLENOS);
        check(!p4Adquirio, "P4 debe bloquearse en buffer vacío");
        check(buffer.getTheoreticalValue(Buffer.SemaphoreKind.LLENOS) == -1, "Llenos con 1 encolado debe ser -1");

        System.out.println("PASS: SemaforosValoresNegativosTest exitoso. Valores teóricos negativos según Stallings validados.");
    }
}
