/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.hardware;
import com.avilaos.core.kernel.IClockTickable;
import com.avilaos.core.model.ExecutionMode;
import com.avilaos.core.model.ProcessControlBlock;

/**
 * Representación del procesador (CPU) y sus registros de hardware.
 * Implementa IClockTickable para reaccionar al ciclo de reloj actualizando sus registros.
 * Requisito: ARC-22.
 *
 * @author rafr
 */
public class CPU implements IClockTickable {
    private int PC;
    private int MAR;
    private int IR;
    private ProcessControlBlock PCB;
    private ExecutionMode mode;

    public CPU(int PC, int MAR, int IR,ProcessControlBlock PCB ,ExecutionMode mode) {
        this.PC = PC;
        this.MAR = MAR;
        this.IR = IR;
        this.PCB = null;
        this.mode = ExecutionMode.KERNEL;
        
    }

    @Override
    public void tick(int currentCycle) {
        if (this.PCB != null) {
            this.PC = this.PCB.getProgramCounter();
            this.MAR = this.PCB.getMemoryAddressRegister();
            this.mode = this.PCB.getMode();
        } else {
            this.mode = ExecutionMode.KERNEL;
        }
    }

    public ExecutionMode getMode() {
        return mode;
    }

    public void setMode(ExecutionMode mode) {
        this.mode = mode;
    }

    public int getPC() {
        return PC;
    }

    public void setPC(int PC) {
        this.PC = PC;
    }

    public int getMAR() {
        return MAR;
    }

    public void setMAR(int MAR) {
        this.MAR = MAR;
    }

    public int getIR() {
        return IR;
    }

    public void setIR(int IR) {
        this.IR = IR;
    }

    public ProcessControlBlock getPCB() {
        return PCB;
    }

    public void setPCB(ProcessControlBlock PCB) {
        this.PCB = PCB;
        if (PCB == null) {
            this.mode = ExecutionMode.KERNEL;
        }
    }

    
    
    
}
