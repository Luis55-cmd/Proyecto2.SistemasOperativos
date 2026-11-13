package com.avilaos.core.model;

/**
 * Operación que un proceso de E/S solicita al disco.
 * CREATE/READ/UPDATE/DELETE: CRUD sobre archivos.
 * SUSPEND/REACTIVATE: swap-out y swap-in del planificador a mediano plazo.
 */
public enum IOOperationType {
    CREATE,
    READ,
    UPDATE,
    DELETE,
    SUSPEND,
    REACTIVATE
}