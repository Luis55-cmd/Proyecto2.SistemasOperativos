package com.avilaos.disk;

/**
 * Dirección de movimiento del cabezal del disco simulado (↑ hacia bloques mayores, ↓ hacia bloques menores).
 */
public enum HeadDirection {
    UP("↑", 1),
    DOWN("↓", -1);

    private final String symbol;
    private final int step;

    HeadDirection(String symbol, int step) {
        this.symbol = symbol;
        this.step = step;
    }

    public String getSymbol() {
        return symbol;
    }

    public int getStep() {
        return step;
    }

    public HeadDirection opposite() {
        return this == UP ? DOWN : UP;
    }

    @Override
    public String toString() {
        return symbol;
    }
}

