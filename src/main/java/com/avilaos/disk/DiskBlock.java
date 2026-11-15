package com.avilaos.disk;

/**
 * Representa un bloque físico numerado del Disco Simulado (SD).
 * Registra su posición, estado (libre/ocupado), el índice del siguiente bloque
 * en la asignación encadenada (-1 si es fin de archivo o está libre) y qué
 * archivo o proceso suspendido lo ocupa.
 */
public class DiskBlock {
    public static final int END_OF_CHAIN = -1;

    private final int blockNumber;
    private final boolean swapBlock;
    private boolean occupied;
    private int nextBlock;
    private String occupiedBy;
    private int ownerProcessId;

    public DiskBlock(int blockNumber, boolean swapBlock) {
        this.blockNumber = blockNumber;
        this.swapBlock = swapBlock;
        this.occupied = false;
        this.nextBlock = END_OF_CHAIN;
        this.occupiedBy = null;
        this.ownerProcessId = -1;
    }

    /**
     * Marca el bloque como ocupado dentro de una cadena de asignación.
     *
     * @param occupiedBy     Ruta del archivo o identificador del proceso suspendido.
     * @param ownerProcessId ID del proceso dueño/creador (-1 si es del sistema).
     * @param nextBlock      Índice del siguiente bloque (-1 si es el último).
     */
    public void allocate(String occupiedBy, int ownerProcessId, int nextBlock) {
        this.occupied = true;
        this.occupiedBy = occupiedBy;
        this.ownerProcessId = ownerProcessId;
        this.nextBlock = nextBlock;
    }

    /**
     * Libera el bloque y limpia su referencia al siguiente bloque.
     */
    public void free() {
        this.occupied = false;
        this.nextBlock = END_OF_CHAIN;
        this.occupiedBy = null;
        this.ownerProcessId = -1;
    }

    public int getBlockNumber() {
        return blockNumber;
    }

    public boolean isSwapBlock() {
        return swapBlock;
    }

    public boolean isOccupied() {
        return occupied;
    }

    public boolean isFree() {
        return !occupied;
    }

    public int getNextBlock() {
        return nextBlock;
    }

    public void setNextBlock(int nextBlock) {
        this.nextBlock = nextBlock;
    }

    public String getOccupiedBy() {
        return occupiedBy;
    }

    public void setOccupiedBy(String occupiedBy) {
        this.occupiedBy = occupiedBy;
    }

    public int getOwnerProcessId() {
        return ownerProcessId;
    }

    public void setOwnerProcessId(int ownerProcessId) {
        this.ownerProcessId = ownerProcessId;
    }

    @Override
    public String toString() {
        if (!occupied) {
            return "[" + blockNumber + (swapBlock ? ":SWAP:FREE]" : ":FREE]");
        }
        return "[" + blockNumber + (swapBlock ? ":SWAP:" : ":") + occupiedBy + " -> " + nextBlock + "]";
    }
}

