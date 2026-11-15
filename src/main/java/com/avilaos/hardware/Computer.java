/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.hardware;

import com.avilaos.core.kernel.ComputerKernel;
import com.avilaos.core.model.SchedulingPolicyType;
import com.avilaos.core.model.ProcessControlBlock; // Asegúrate de que esta importación coincida con tu estructura
import com.avilaos.memory.MemoryManager;

/**
 *
 * @author rafr
 */
public class Computer {
    private int id;
    private String name;
    private CPU cpu;
    private RAM ram;
    private DMA dma;
    private ComputerKernel kernel;
    private SchedulingPolicyType politica;
    private int quantum = 3;
    private MemoryManager memoryManager;

    // --- Variables para Métricas de Rendimiento ---
    private int ciclosActivos = 0;
    private int ciclosObservados = 0;
    private int procesosFinalizados = 0;
    private int procesosCompletados = 0;
    private int totalTiempoRespuesta = 0;
    private int deadlinesCumplidos = 0;
    private int totalTiempoEspera = 0;

    public Computer(int id, CPU cpu, RAM ram, DMA dma, ComputerKernel kernel, SchedulingPolicyType politica) {
        this(id, cpu, ram, dma, kernel, politica, 3);
    }

    public Computer(int id, CPU cpu, RAM ram, DMA dma, ComputerKernel kernel, SchedulingPolicyType politica, int quantum) {
        this.id = id;
        this.name = "Computador " + id;
        this.cpu = cpu;
        this.ram = ram;
        this.dma = dma;
        this.kernel = kernel;
        this.politica = politica;
        this.quantum = Math.max(1, quantum);
        this.memoryManager = (ram != null) ? new com.avilaos.memory.MemoryManager(ram) : null;
        if (this.kernel != null) {
            this.kernel.onPolicyChanged(this);
        }
    }

    // --- Métodos para Métricas de Rendimiento ---
    public void incrementarCicloActivo() { 
        this.ciclosActivos++; 
    }
    
    /** Un tick observado por este computador, incluso si no tiene un proceso ejecutando. */
    public void registrarCicloObservado() { ciclosObservados++; }
    public int getCiclosObservados() { return ciclosObservados; }
    public int getProcesosFinalizados() { return procesosFinalizados; }

    /** Invocado una sola vez por terminateProcess; abortos no cuentan como trabajo completado. */
    public void registrarTerminacion(ProcessControlBlock pcb, int cicloActual) {
        procesosFinalizados++;
        boolean completo = pcb.getRemainingTime() <= 0
                || (pcb.getRequiredElements() > 0 && pcb.getProcessedElements() >= pcb.getRequiredElements());
        if (!completo) return;
        procesosCompletados++;
        totalTiempoRespuesta += Math.max(0, pcb.getStartCycle() - pcb.getArrivalCycle());
        totalTiempoEspera += pcb.getWaitingTime();
        if (pcb.getRemainingDeadline() > 0) deadlinesCumplidos++;
    }

    public int getCiclosActivos() { return ciclosActivos; }
    public int getProcesosCompletados() { return procesosCompletados; }
    public int getTotalTiempoRespuesta() { return totalTiempoRespuesta; }
    public int getTotalTiempoEspera() { return totalTiempoEspera; }
    public int getDeadlinesCumplidos() { return deadlinesCumplidos; }

    // --- Métodos Originales ---
    /** Nombre visible; el ID sigue siendo la identidad. No cambia hardware ni reloj. */
    public String getName() { return name; }

    /** Entrada nombre no vacío; elimina espacios externos. Error sin mutación. Sin ciclos. */
    public void setName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("El computador necesita un nombre.");
        }
        this.name = name.trim();
    }

    public SchedulingPolicyType getPolitica() {
        return politica;
    }

    public void setPolitica(SchedulingPolicyType politica) {
        this.politica = politica;
        if (this.kernel != null) {
            this.kernel.onPolicyChanged(this);
        }
    }

    public int getQuantum() {
        return quantum;
    }

    public void setQuantum(int quantum) {
        this.quantum = Math.max(1, quantum);
        if (this.kernel != null) {
            this.kernel.updateQuantum(this.quantum);
        }
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public CPU getCpu() {
        return cpu;
    }

    public void setCpu(CPU cpu) {
        this.cpu = cpu;
    }

    public RAM getRam() {
        return ram;
    }

    public void setRam(RAM ram) {
        this.ram = ram;
    }

    public DMA getDma() {
        return dma;
    }

    public void setDma(DMA dma) {
        this.dma = dma;
    }

    public ComputerKernel getKernel() {
        return kernel;
    }

    public void setKernel(ComputerKernel kernel) {
        this.kernel = kernel;
    }

    public com.avilaos.memory.MemoryManager getMemoryManager() {
        if (this.memoryManager == null && this.ram != null) {
            this.memoryManager = new com.avilaos.memory.MemoryManager(this.ram);
        }
        return this.memoryManager;
    }

    public void setMemoryManager(com.avilaos.memory.MemoryManager memoryManager) {
        this.memoryManager = memoryManager;
        if (memoryManager != null && memoryManager.getRam() != null) {
            this.ram = memoryManager.getRam();
        }
    }

    public void setCiclosActivos(int ciclosActivos) {
        this.ciclosActivos = ciclosActivos;
    }

    public void setProcesosCompletados(int procesosCompletados) {
        this.procesosCompletados = procesosCompletados;
    }

    public void setTotalTiempoRespuesta(int totalTiempoRespuesta) {
        this.totalTiempoRespuesta = totalTiempoRespuesta;
    }

    public void setDeadlinesCumplidos(int deadlinesCumplidos) {
        this.deadlinesCumplidos = deadlinesCumplidos;
    }
}