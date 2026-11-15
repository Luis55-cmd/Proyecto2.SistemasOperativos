package com.avilaos.disk;

import com.avilaos.core.structures.MyLinkedList;

/**
 * Política de planificación de disco FIFO (First-In, First-Out) — Issue #7.
 * Atiende estrictamente por orden de llegada (menor ciclo de llegada / ID).
 */
public class FIFODiskPolicy implements DiskSchedulingPolicy {

    @Override
    public IORequest selectNext(MyLinkedList<IORequest> pending, int head, int totalBlocks) {
        if (pending == null || pending.isEmpty()) {
            return null;
        }
        IORequest earliest = pending.get(0);
        for (int i = 1; i < pending.size(); i++) {
            IORequest candidate = pending.get(i);
            if (compareArrival(candidate, earliest) < 0) {
                earliest = candidate;
            }
        }
        return earliest;
    }

    public static int compareArrival(IORequest a, IORequest b) {
        int cmp = Integer.compare(a.getArrivalCycle(), b.getArrivalCycle());
        if (cmp != 0) {
            return cmp;
        }
        return Integer.compare(a.getId(), b.getId());
    }

    @Override
    public DiskSchedulingPolicyType getPolicyType() {
        return DiskSchedulingPolicyType.FIFO;
    }

    @Override
    public void reset() {
        // FIFO no mantiene estado direccional interno
    }
}

