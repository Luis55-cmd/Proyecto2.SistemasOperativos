package com.avilaos.disk;

import com.avilaos.core.structures.MyLinkedList;

/**
 * Política de planificación de disco SSTF (Shortest Seek Time First) — Issue #7.
 * Selecciona la solicitud con menor distancia absoluta respecto a la posición
 * actual del cabezal; desempata por orden de llegada.
 */
public class SSTFDiskPolicy implements DiskSchedulingPolicy {

    @Override
    public IORequest selectNext(MyLinkedList<IORequest> pending, int head, int totalBlocks) {
        if (pending == null || pending.isEmpty()) {
            return null;
        }
        IORequest best = null;
        int bestDistance = Integer.MAX_VALUE;

        for (int i = 0; i < pending.size(); i++) {
            IORequest candidate = pending.get(i);
            int distance = Math.abs(candidate.getTargetBlock() - head);
            if (best == null || distance < bestDistance
                    || (distance == bestDistance && FIFODiskPolicy.compareArrival(candidate, best) < 0)) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    @Override
    public DiskSchedulingPolicyType getPolicyType() {
        return DiskSchedulingPolicyType.SSTF;
    }

    @Override
    public void reset() {
        // SSTF evalúa distancia respecto al cabezal actual en cada selección
    }
}

