package com.avilaos.disk;

import com.avilaos.core.structures.MyLinkedList;

/**
 * Política de planificación de disco SCAN (Elevador) — Issue #8.
 * El cabezal se mueve en la dirección actual (por defecto ↑ hacia bloques mayores)
 * atendiendo solicitudes en el camino hasta llegar al extremo del disco
 * ({@code totalBlocks - 1} o {@code 0}), donde rebota e invierte su dirección.
 */
public class SCANDiskPolicy implements DiskSchedulingPolicy {

    private HeadDirection direction;
    private final HeadDirection initialDirection;
    private boolean bouncedOnLastSelect;
    private int lastBounceBoundary = -1;

    public SCANDiskPolicy() {
        this(HeadDirection.UP);
    }

    public SCANDiskPolicy(HeadDirection initialDirection) {
        this.initialDirection = initialDirection != null ? initialDirection : HeadDirection.UP;
        this.direction = this.initialDirection;
    }

    @Override
    public IORequest selectNext(MyLinkedList<IORequest> pending, int head, int totalBlocks) {
        bouncedOnLastSelect = false;
        lastBounceBoundary = -1;
        if (pending == null || pending.isEmpty()) {
            return null;
        }

        IORequest inDirection = findNextInDirection(pending, head, direction);
        if (inDirection != null) {
            return inDirection;
        }

        // No hay más solicitudes en la dirección actual: llega al extremo y rebota
        bouncedOnLastSelect = true;
        lastBounceBoundary = (direction == HeadDirection.UP) ? Math.max(0, totalBlocks - 1) : 0;
        direction = direction.opposite();
        return findNextInDirection(pending, lastBounceBoundary, direction);
    }

    private IORequest findNextInDirection(MyLinkedList<IORequest> pending, int fromHead, HeadDirection dir) {
        IORequest best = null;
        int bestDist = Integer.MAX_VALUE;

        for (int i = 0; i < pending.size(); i++) {
            IORequest req = pending.get(i);
            int target = req.getTargetBlock();
            boolean reachable = (dir == HeadDirection.UP) ? (target >= fromHead) : (target <= fromHead);
            if (reachable) {
                int dist = Math.abs(target - fromHead);
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

    public boolean didBounceOnLastSelect() {
        return bouncedOnLastSelect;
    }

    public int getLastBounceBoundary() {
        return lastBounceBoundary;
    }

    @Override
    public DiskSchedulingPolicyType getPolicyType() {
        return DiskSchedulingPolicyType.SCAN;
    }

    @Override
    public void reset() {
        this.direction = this.initialDirection;
        this.bouncedOnLastSelect = false;
        this.lastBounceBoundary = -1;
    }
}
