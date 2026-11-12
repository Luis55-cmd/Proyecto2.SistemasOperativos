/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.hardware;

/**
 *
 * @author rafr
 */
public class RAM {
    private int totalSize;
    int usedSize;
    private volatile int[] space;
    
    public RAM(int totalSize, int usedSize, int[] space){
    this.totalSize = totalSize;
    this.usedSize = 0;
    this.space = new int[totalSize];
    }

    public int getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(int totalSize) {
        this.totalSize = totalSize;
    }

    public int getUsedSize() {
        return usedSize;
    }

    public void setUsedSize(int usedSize) {
        this.usedSize = usedSize;
    }

    public int[] getSpace() {
        return space;
    }

    public void setSpace(int[] space) {
        this.space = space;
    }
    
    
    
}
