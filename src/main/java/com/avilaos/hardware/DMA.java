/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.hardware;

import com.avilaos.core.kernel.IClockTickable;

/**
 * Controlador de Acceso Directo a Memoria (DMA).
 * Implementa IClockTickable para reaccionar a los ciclos de reloj del sistema.
 * Requisito: ARC-22.
 *
 * @author rafr
 */
public class DMA implements IClockTickable {

    private boolean active;
    private int cyclesRemaining;

    public DMA() {
        this.active = false;
        this.cyclesRemaining = 0;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getCyclesRemaining() {
        return cyclesRemaining;
    }

    public void setCyclesRemaining(int cyclesRemaining) {
        this.cyclesRemaining = cyclesRemaining;
    }

    @Override
    public void tick(int currentCycle) {
        if (active && cyclesRemaining > 0) {
            cyclesRemaining--;
            if (cyclesRemaining == 0) {
                active = false;
            }
        }
    }
}
