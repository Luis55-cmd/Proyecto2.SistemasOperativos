/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.sync;

import com.avilaos.core.model.BlockReason;
import com.avilaos.core.model.ProcessControlBlock.BufferPermit;
import com.avilaos.core.model.ExecutionMode;
import com.avilaos.core.model.ProcessControlBlock;
import com.avilaos.core.model.ProcessState;
import com.avilaos.core.structures.*;
import java.util.concurrent.Semaphore;
/**
 *
 * @author rafr
 */
public class Buffer {
    public enum SemaphoreKind { MUTEX, VACIOS, LLENOS }

    /**
     * Ejecuta una instrucción wait, sin bloquear el hilo Java.
     * Entrada: PCB ejecutando y semáforo de este buffer.
     * Salida: true si obtuvo permiso; false si quedó en la cola de espera.
     * MAR toma el PC actual y PC avanza una vez incluso si bloquea; no hay bucle de espera.
     * Un estado inválido se rechaza antes de modificar PCB, permisos o colas.
     * signal concede el permiso al PCB encolado antes de marcarlo listo.
     * Ambas operaciones usan el monitor de este buffer.
     */
    public synchronized boolean semWait(ProcessControlBlock pcb, SemaphoreKind kind) {
        if (pcb == null || kind == null) {
            throw new IllegalArgumentException("wait requiere un PCB y un semáforo");
        }
        if (pcb.getState() != ProcessState.EJECUTANDO
                || pcb.getBlockReason() != BlockReason.NONE || pcb.isTerminated()) {
            throw new IllegalStateException("Solo un proceso ejecutando sin espera pendiente puede hacer wait");
        }

        Semaphore semaphore = switch (kind) {
            case MUTEX -> mutex;
            case VACIOS -> vacios;
            case LLENOS -> llenos;
        };
        MyQueue<ProcessControlBlock> waiting = switch (kind) {
            case MUTEX -> waitingMutex;
            case VACIOS -> waitingVacios;
            case LLENOS -> waitingLlenos;
        };
        BlockReason reason = switch (kind) {
            case MUTEX -> BlockReason.WAITING_MUTEX;
            case VACIOS -> BlockReason.WAITING_FULL_BUFFER;
            case LLENOS -> BlockReason.WAITING_EMPTY_BUFFER;
        };

        pcb.validateBufferPermitAcquisition(id, BufferPermit.valueOf(kind.name()));
        pcb.setMode(ExecutionMode.KERNEL);
        pcb.advanceInstruction();

        if (semaphore.tryAcquire()) {
            pcb.grantBufferPermit(id, BufferPermit.valueOf(kind.name()));
            return true;
        }

        waiting.enqueue(pcb);
        pcb.setBlockReason(reason);
        pcb.setState(ProcessState.BLOQUEADO);
        return false;
    }

    /**
     * Ejecuta signal como una instrucción del emisor, bajo el mismo monitor que wait.
     * Entradas: emisor ejecutando y semáforo de este buffer.
     * Salida: PCB al que se transfirió el permiso, o null si quedó disponible.
     * Actualiza PC/MAR solo del emisor. El receptor queda LISTO, sin repetir wait.
     * Descarta entradas de espera obsoletas hasta hallar una válida o vaciar la cola.
     * Valida titularidad del mutex o notificación pendiente tras insertar/extraer.
     * El driver continúa en la fase siguiente sin reintentar wait.
     */
    public synchronized ProcessControlBlock semSignal(ProcessControlBlock emitter, SemaphoreKind kind) {
        if (emitter == null || kind == null) {
            throw new IllegalArgumentException("signal requiere un PCB y un semáforo");
        }
        if (emitter.getState() != ProcessState.EJECUTANDO
                || emitter.getBlockReason() != BlockReason.NONE || emitter.isTerminated()) {
            throw new IllegalStateException("Solo un proceso ejecutando sin espera pendiente puede hacer signal");
        }
        emitter.validateBufferSignal(id, BufferPermit.valueOf(kind.name()));
        emitter.recordBufferSignal(id, BufferPermit.valueOf(kind.name()));
        emitter.setMode(ExecutionMode.KERNEL);
        emitter.advanceInstruction();

        return transferOrRelease(kind);
    }

    /** Comparte la transferencia con la limpieza; no ejecuta instrucciones del PCB. */
    private ProcessControlBlock transferOrRelease(SemaphoreKind kind) {
        Semaphore semaphore = switch (kind) {
            case MUTEX -> mutex; case VACIOS -> vacios; case LLENOS -> llenos;
        };
        MyQueue<ProcessControlBlock> waiting = switch (kind) {
            case MUTEX -> waitingMutex; case VACIOS -> waitingVacios; case LLENOS -> waitingLlenos;
        };
        BlockReason reason = switch (kind) {
            case MUTEX -> BlockReason.WAITING_MUTEX;
            case VACIOS -> BlockReason.WAITING_FULL_BUFFER;
            case LLENOS -> BlockReason.WAITING_EMPTY_BUFFER;
        };
        while (!waiting.isEmpty()) {
            ProcessControlBlock receiver = waiting.dequeue();
            if (receiver.isTerminated() || receiver.getState() != ProcessState.BLOQUEADO
                    || receiver.getBlockReason() != reason) continue;
            receiver.grantBufferPermit(id, BufferPermit.valueOf(kind.name()));
            receiver.setBlockReason(BlockReason.NONE);
            receiver.setState(ProcessState.LISTO);
            return receiver;
        }
        semaphore.release();
        return null;
    }

    /** Cancelación: devuelve reserva no usada o publica la operación ya realizada.
     * Idempotente; no avanza PC/MAR. El kernel retira luego al PCB del sistema.
     */
    public synchronized void cleanupPermits(ProcessControlBlock pcb) {
        removeWaitingProcess(pcb);
        if (pcb.getPermitBufferId() != id) return;
        if (pcb.holdsBufferMutex()) transferOrRelease(SemaphoreKind.MUTEX);
        if (pcb.getReservedBufferSlot() != null)
            transferOrRelease(SemaphoreKind.valueOf(pcb.getReservedBufferSlot().name()));
        if (pcb.getPendingBufferSignal() != null)
            transferOrRelease(SemaphoreKind.valueOf(pcb.getPendingBufferSignal().name()));
        pcb.clearBufferPermits();
    }

    Semaphore mutex;
    Semaphore vacios;
    Semaphore llenos;
    int idComp;
    int storage;
    int sizeInMemory;
    int id;
    MyQueue<Object> datos;
    private int produced;
    private int comsumed;
    private int latency;

    private final MyQueue<ProcessControlBlock> waitingMutex = new MyQueue<>();
    private final MyQueue<ProcessControlBlock> waitingVacios = new MyQueue<>();
    private final MyQueue<ProcessControlBlock> waitingLlenos = new MyQueue<>();

    public Buffer(Semaphore mutex, Semaphore vacios, Semaphore llenos, int idComp, int storage, int id, MyQueue<Object> datos, int sizeInMemory) {
        this(mutex, vacios, llenos, idComp, storage, id, datos, sizeInMemory, 0);
    }

    public Buffer(Semaphore mutex, Semaphore vacios, Semaphore llenos, int idComp, int storage, int id, MyQueue<Object> datos, int sizeInMemory, int latency) {
        this.mutex = new Semaphore(1, true);
        this.vacios = new Semaphore(storage, true);
        this.llenos = new Semaphore(0, true);
        this.idComp = idComp;
        this.storage = storage;
        this.sizeInMemory = sizeInMemory;
        this.id = id;
        this.datos = datos;
        this.produced = 0;
        this.comsumed = 0;
        this.latency = Math.max(0, latency);
    }

    public synchronized Object consume() {
        if (!datos.isEmpty()) {
            comsumed++;
            return datos.dequeue();
        }
        return null;
    }
    
     public synchronized void produce(Object item) {
        if (datos.size() < storage) {
            datos.enqueue(item);
            produced++;
        }
    }

    public Semaphore getMutex() {
        return mutex;
    }

    public void setMutex(Semaphore mutex) {
        this.mutex = mutex;
    }

    public Semaphore getVacios() {
        return vacios;
    }

    public void setVacios(Semaphore vacios) {
        this.vacios = vacios;
    }

    public Semaphore getLlenos() {
        return llenos;
    }

    public void setLlenos(Semaphore llenos) {
        this.llenos = llenos;
    }

    public int getIdComp() {
        return idComp;
    }

    public void setIdComp(int idComp) {
        this.idComp = idComp;
    }

    public int getStorage() {
        return storage;
    }

    public void setStorage(int storage) {
        this.storage = storage;
    }

    public int getSizeInMemory() {
        return sizeInMemory;
    }

    public void setSizeInMemory(int sizeInMemory) {
        this.sizeInMemory = sizeInMemory;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public MyQueue<Object> getDatos() {
        return datos;
    }

    public void setDatos(MyQueue<Object> datos) {
        this.datos = datos;
    }

    public int getProduced() {
        return produced;
    }

    public void setProduced(int produced) {
        this.produced = produced;
    }

    public int getComsumed() {
        return comsumed;
    }

    public void setComsumed(int comsumed) {
        this.comsumed = comsumed;
    }

    public int getLatency() {
        return latency;
    }

    public void setLatency(int latency) {
        this.latency = Math.max(0, latency);
    }

    public synchronized void registerWaitingMutex(ProcessControlBlock pcb) {
        if (pcb == null) return;
        this.waitingMutex.removeElement(pcb);
        this.waitingMutex.enqueue(pcb);
    }

    public synchronized ProcessControlBlock pollWaitingMutex() {
        while (!this.waitingMutex.isEmpty()) {
            ProcessControlBlock p = this.waitingMutex.dequeue();
            if (p != null
                    && p.getState() == ProcessState.BLOQUEADO
                    && p.getBlockReason() == BlockReason.WAITING_MUTEX) {
                return p;
            }
        }
        return null;
    }

    public synchronized void registerWaitingVacios(ProcessControlBlock pcb) {
        if (pcb == null) return;
        this.waitingVacios.removeElement(pcb);
        this.waitingVacios.enqueue(pcb);
    }

    public synchronized ProcessControlBlock pollWaitingVacios() {
        while (!this.waitingVacios.isEmpty()) {
            ProcessControlBlock p = this.waitingVacios.dequeue();
            if (p != null
                    && p.getState() == ProcessState.BLOQUEADO
                    && p.getBlockReason() == BlockReason.WAITING_FULL_BUFFER) {
                return p;
            }
        }
        return null;
    }

    public synchronized void registerWaitingLlenos(ProcessControlBlock pcb) {
        if (pcb == null) return;
        this.waitingLlenos.removeElement(pcb);
        this.waitingLlenos.enqueue(pcb);
    }

    public synchronized ProcessControlBlock pollWaitingLlenos() {
        while (!this.waitingLlenos.isEmpty()) {
            ProcessControlBlock p = this.waitingLlenos.dequeue();
            if (p != null
                    && p.getState() == ProcessState.BLOQUEADO
                    && p.getBlockReason() == BlockReason.WAITING_EMPTY_BUFFER) {
                return p;
            }
        }
        return null;
    }

    public synchronized void removeWaitingProcess(ProcessControlBlock pcb) {
        if (pcb == null) return;
        this.waitingMutex.removeElement(pcb);
        this.waitingVacios.removeElement(pcb);
        this.waitingLlenos.removeElement(pcb);
    }

    /**
     * Retorna el valor del semáforo según la convención clásica de Stallings/Dijkstra:
     * - Si es positivo (> 0): permisos disponibles.
     * - Si es cero (== 0): ocupado sin procesos en espera.
     * - Si es negativo (< 0): su valor absoluto indica la cantidad de procesos bloqueados en la cola de espera.
     */
    public synchronized int getTheoreticalValue(SemaphoreKind kind) {
        return switch (kind) {
            case MUTEX -> mutex.availablePermits();
            case VACIOS -> vacios.availablePermits() - waitingVacios.size();
            case LLENOS -> llenos.availablePermits() - waitingLlenos.size();
        };
    }

    public MyQueue<ProcessControlBlock> getWaitingMutex() {
        return waitingMutex;
    }

    public MyQueue<ProcessControlBlock> getWaitingVacios() {
        return waitingVacios;
    }

    public MyQueue<ProcessControlBlock> getWaitingLlenos() {
        return waitingLlenos;
    }
    
    public synchronized String getBlockedProcessesAsString() {
        // Obtenemos la representación en texto de cada cola
        String m = waitingMutex.toString();
        String v = waitingVacios.toString();
        String l = waitingLlenos.toString();
        
        // Limpiamos los corchetes vacíos si las colas están vacías (dependiendo de cómo sea tu MyQueue.toString())
        if (m.equals("[]")) m = "";
        if (v.equals("[]")) v = "";
        if (l.equals("[]")) l = "";
        
        StringBuilder result = new StringBuilder();
        
        if (!m.isEmpty()) result.append("Mutex: ").append(m).append(" ");
        if (!v.isEmpty()) result.append("Vacíos: ").append(v).append(" ");
        if (!l.isEmpty()) result.append("Llenos: ").append(l);
        
        String finalResult = result.toString().trim();
        return finalResult.isEmpty() ? "-" : finalResult;
    }
}
