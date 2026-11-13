package com.avilaos.core.structures;

/** Operación que requiere un elemento sobre una estructura vacía. */
public class EstructuraVaciaException extends RuntimeException {

    public EstructuraVaciaException(String mensaje) {
        super(mensaje);
    }
}
