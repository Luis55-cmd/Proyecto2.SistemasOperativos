package com.avilaos.memory;

import com.avilaos.hardware.RAM;

/**
 * Gestor de Memoria RAM para ÁvilaOS (Capa 2: Memoria).
 * Administra la capacidad total, memoria usada y libre de forma contable y sincronizada,
 * manteniendo consistencia con el componente físico simulado (RAM).
 * 
 * Requisitos: D-08, D-12, MEM-01 a MEM-07, Issue #20.
 */
public class MemoryManager {

    private final RAM ram;
    private final int totalMemory;
    private int usedMemory;
    private int freeMemory;

    public MemoryManager(RAM ram) {
        this.ram = ram;
        this.totalMemory = (ram != null) ? ram.getTotalSize() : 0;
        this.usedMemory = (ram != null) ? ram.getUsedSize() : 0;
        this.freeMemory = Math.max(0, this.totalMemory - this.usedMemory);
    }

    public MemoryManager(int totalMemory) {
        this.totalMemory = Math.max(0, totalMemory);
        this.usedMemory = 0;
        this.freeMemory = this.totalMemory;
        this.ram = new RAM(this.totalMemory, 0, new int[this.totalMemory]);
    }

    public synchronized boolean hasAvailableMemory(int required) {
        if (required <= 0) {
            return true;
        }
        return this.freeMemory >= required;
    }

    public synchronized boolean allocateMemory(int amount) {
        if (amount < 0) {
            return false;
        }
        if (hasAvailableMemory(amount)) {
            this.usedMemory += amount;
            this.freeMemory -= amount;
            if (this.ram != null) {
                this.ram.setUsedSize(this.usedMemory);
            }
            return true;
        }
        return false;
    }

    public synchronized void freeMemory(int amount) {
        if (amount <= 0) {
            return;
        }
        this.usedMemory = Math.max(0, this.usedMemory - amount);
        this.freeMemory = this.totalMemory - this.usedMemory;
        if (this.ram != null) {
            this.ram.setUsedSize(this.usedMemory);
        }
    }

    public synchronized int getTotalMemory() {
        return totalMemory;
    }

    public synchronized int getUsedMemory() {
        return usedMemory;
    }

    public synchronized int getFreeMemory() {
        return freeMemory;
    }

    public RAM getRam() {
        return ram;
    }

    public synchronized void reset() {
        this.usedMemory = 0;
        this.freeMemory = this.totalMemory;
        if (this.ram != null) {
            this.ram.setUsedSize(0);
            if (this.ram.getSpace() != null) {
                int[] space = this.ram.getSpace();
                for (int i = 0; i < space.length; i++) {
                    space[i] = 0;
                }
            }
        }
    }
}
