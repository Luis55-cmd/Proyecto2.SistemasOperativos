package com.avilaos.disk;

import com.avilaos.core.structures.MyLinkedList;

/**
 * Política opcional LOOK — Issue #8.
 * Igual que SCAN, pero invierte el sentido en la última solicitud en esa dirección
 * sin llegar hasta el extremo físico del disco.
 */
public class LOOKDiskPolicy implements DiskSchedulingPolicy {

    private HeadDirection direction = HeadDirection.UP;

    @Override
    public IORequest selectNext(MyLinkedList<IORequest> pending, int head, int totalBlocks) {
        if (pending == null || pending.isEmpty()) {
            return null;
        }
        IORequest next = findInDirection(pending, head, direction);
        if (next != null) {
            return next;
        }
        direction = direction.opposite();
        return findInDirection(pending, head, direction);
    }

    private IORequest findInDirection(MyLinkedList<IORequest> pending, int head, HeadDirection dir) {
        IORequest best = null;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < pending.size(); i++) {
            IORequest req = pending.get(i);
            int target = req.getTargetBlock();
            boolean ok = (dir == HeadDirection.UP) ? (target >= head) : (target <= head);
            if (ok) {
                int dist = Math.abs(target - head);
                if (best == null || dist < bestDist
                        || (dist == bestDist && FIFODiskPolicy.compareArrival(req, best) < 0)) {
                    best = req;
                    bestDist = dist;
                }
            }
        }
        return best;
    }

    public HeadDirection getDirection() {
        return direction;
    }

    public void setDirection(HeadDirection direction) {
        if (direction != null) {
            this.direction = direction;
        }
    }

    @Override
    public DiskSchedulingPolicyType getPolicyType() {
        return DiskSchedulingPolicyType.LOOK;
    }

    @Override
    public void reset() {
        this.direction = HeadDirection.UP;
    }
}

