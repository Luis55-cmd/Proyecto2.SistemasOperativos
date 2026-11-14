package com.avilaos.disk;

import com.avilaos.core.structures.MyLinkedList;

/**
 * Estrategia de planificación de disco (patrón Strategy).
 * Atiende una única cola de E/S: una solicitud a la vez.
 */
public interface DiskSchedulingPolicy {

    /**
     * Elige la próxima solicitud a atender SIN retirarla de la cola
     * (el disco la retira bajo su propio semáforo).
     *
     * @param pending     solicitudes pendientes, en orden de llegada
     * @param head        posición actual del cabezal
     * @param totalBlocks bloques del área de datos (el último bloque es totalBlocks - 1)
     * @return la solicitud elegida, o null si la cola está vacía
     */
    IORequest selectNext(MyLinkedList<IORequest> pending, int head, int totalBlocks);

    /** Política que implementa esta estrategia. */
    DiskSchedulingPolicyType getPolicyType();

    /** Reinicia el estado interno (p. ej. la dirección en SCAN/C-SCAN). */
    void reset();
}
