package com.avilaos.core.kernel;

/**
 * Interfaz común para todos los componentes de hardware/SO
 * que reaccionan sincrónicamente al pulso del reloj global.
 * 
 * Requisito: RF §1 (Uso obligatorio de interfaces para componentes de reloj).
 */
public interface IClockTickable {

    /**
     * Notificación de avance de un ciclo de reloj.
     * 
     * @param currentCycle Número de ciclo global actual.
     */
    void tick(int currentCycle);
}
