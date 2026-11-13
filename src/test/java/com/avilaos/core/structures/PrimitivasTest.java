package com.avilaos.core.structures;

/** Regresiones secuenciales de primitivas; ver docs/contratos/primitivas-correccion.md. */
public class PrimitivasTest {
    static int failures;
    static int passed;
    interface Action { void run(); }
    interface Check { boolean ok(); }
    static void test(String id, String expected, Action action, Check check) {
        String outcome;
        try { action.run(); outcome = check.ok() ? "PASS" : "FAIL: estado/valor incorrecto"; }
        catch (Exception e) { outcome = "FAIL: " + e.getClass().getSimpleName(); }
        if (outcome.equals("PASS")) passed++; else failures++;
        System.out.println(id + "\t" + expected + "\t" + outcome);
    }
    static void expectException(Class<? extends RuntimeException> type, Action action) {
        try { action.run(); }
        catch (RuntimeException e) {
            if (e.getClass() == type) return;
            throw e;
        }
        throw new IllegalStateException("Falta excepcion " + type.getSimpleName());
    }
    static void observe(String id, Action action) {
        String outcome = "retorno normal";
        try { action.run(); } catch (Exception e) { outcome = e.getClass().getSimpleName(); }
        System.out.println(id + "\tcontrato por acordar\tOBS: " + outcome);
    }
    static MyLinkedList<Integer> list(int n) {
        MyLinkedList<Integer> l = new MyLinkedList<>();
        for (int i = 0; i < n; i++) l.append(i);
        return l;
    }
    static boolean chain(Node<?> first, Node<?> last, int size, int[] expected) {
        if (size != expected.length) return false;
        Node<?> node = first, seenLast = null;
        for (int x : expected) {
            if (node == null || !Integer.valueOf(x).equals(node.data)) return false;
            seenLast = node; node = node.pNext;
        }
        return node == null && last == seenLast;
    }
    static boolean matches(MyLinkedList<Integer> l, int[] expected) {
        return chain(l.pFirst, l.pLast, l.size(), expected)
                && l.isEmpty() == (expected.length == 0);
    }
    static boolean stackMatches(MyStack<Integer> s, int[] expected) {
        if (s.size() != expected.length || s.isEmpty() != (expected.length == 0)) return false;
        Node<?> node = s.top;
        for (int x : expected) {
            if (node == null || !Integer.valueOf(x).equals(node.data)) return false;
            node = node.pNext;
        }
        return node == null;
    }
    static int[] seq(int n) {
        int[] a = new int[n]; for (int i = 0; i < n; i++) a[i] = i; return a;
    }
    static int[] inserted(int n, int pos) {
        int[] a = new int[n + 1];
        for (int i = 0; i < a.length; i++) a[i] = i < pos ? i : i == pos ? 99 : i - 1;
        return a;
    }
    static int[] removed(int n, int pos) {
        int[] a = new int[n - 1];
        for (int i = 0; i < a.length; i++) a[i] = i < pos ? i : i + 1;
        return a;
    }
    public static void main(String[] args) {
        System.out.println("caso\tesperado\tresultado");
        for (int n = 0; n <= 5; n++) {
            final int count = n;
            MyLinkedList<Integer> base = list(n);
            test("list.construct.n=" + n, "contenido/size/extremos coherentes", () -> {}, () -> matches(base, seq(count)));
            MyLinkedList<Integer> append = list(n);
            test("list.append.n=" + n, "un elemento al final", () -> append.append(99), () -> matches(append, inserted(count, count)));
            MyLinkedList<Integer> first = list(n);
            test("list.insertFirst.n=" + n, "un elemento al inicio", () -> first.insertFirst(99), () -> matches(first, inserted(count, 0)));
            for (int pos = 0; pos <= n; pos++) {
                final int at = pos; MyLinkedList<Integer> l = list(n);
                test("list.insertPos.n=" + n + ".p=" + pos, "insertar una vez, conservar resto", () -> l.insertPos(at, 99), () -> matches(l, inserted(count, at)));
            }
            if (n > 0) {
                MyLinkedList<Integer> l = list(n), r = list(n);
                test("list.deleteFirst.n=" + n, "eliminar primero", l::deleteFirst, () -> matches(l, removed(count, 0)));
                test("list.deleteLast.n=" + n, "eliminar ultimo", r::deleteLast, () -> matches(r, removed(count, count - 1)));
                for (int pos = 0; pos < n; pos++) {
                    final int at = pos; MyLinkedList<Integer> d = list(n);
                    test("list.deletePos.n=" + n + ".p=" + pos, "eliminar uno, conservar resto/extremos", () -> d.deletePos(at), () -> matches(d, removed(count, at)));
                }
            }
            for (int pos : new int[]{-1, n + 1}) {
                final int at = pos; MyLinkedList<Integer> l = list(n);
                test("list.invalidInsert.n=" + n + ".p=" + pos, "IndexOutOfBoundsException sin mutacion", () -> expectException(IndexOutOfBoundsException.class, () -> l.insertPos(at, 99)), () -> matches(l, seq(count)));
            }
            for (int pos : new int[]{-1, n}) {
                final int at = pos; MyLinkedList<Integer> l = list(n);
                test("list.invalidDelete.n=" + n + ".p=" + pos, "IndexOutOfBoundsException sin mutacion", () -> expectException(IndexOutOfBoundsException.class, () -> l.deletePos(at)), () -> matches(l, seq(count)));
            }
        }
        MyLinkedList<Integer> cycle = list(3);
        test("list.drainReuse", "vaciar, reutilizar y conservar extremos", () -> { cycle.deleteFirst(); cycle.deleteFirst(); cycle.deleteFirst(); cycle.append(9); }, () -> matches(cycle, new int[]{9}));
        MyLinkedList<Integer> emptyFirst = new MyLinkedList<>();
        test("list.deleteFirst.empty", "EstructuraVaciaException sin mutacion", () -> expectException(EstructuraVaciaException.class, emptyFirst::deleteFirst), () -> matches(emptyFirst,new int[]{}));
        MyLinkedList<Integer> emptyLast = new MyLinkedList<>();
        test("list.deleteLast.empty", "EstructuraVaciaException sin mutacion", () -> expectException(EstructuraVaciaException.class, emptyLast::deleteLast), () -> matches(emptyLast,new int[]{}));
        observe("list.append.null", () -> new MyLinkedList<Integer>().append(null));
        MyLinkedList<Integer> duplicates = new MyLinkedList<>();
        test("list.duplicates", "conservar duplicados", () -> { duplicates.append(7); duplicates.append(7); }, () -> matches(duplicates, new int[]{7,7}));
        MyQueue<Integer> q = new MyQueue<>();
        test("queue.construct", "vacia/extremos/size", () -> {}, () -> q.isEmpty() && chain(q.pFirst,q.pLast,q.size,new int[]{}));
        test("queue.enqueue", "orden 1,2,3", () -> { q.queue(1);q.queue(2);q.queue(3); }, () -> chain(q.pFirst,q.pLast,q.size,new int[]{1,2,3}));
        test("queue.peek", "devuelve 1 sin mutacion", () -> { if(q.peek()!=1) throw new IllegalStateException(); }, () -> chain(q.pFirst,q.pLast,q.size,new int[]{1,2,3}));
        test("queue.drain", "extraer 1,2,3 y quedar vacia", () -> { if(q.dequeue()!=1 || q.dequeue()!=2 || q.dequeue()!=3) throw new IllegalStateException(); }, () -> q.isEmpty() && chain(q.pFirst,q.pLast,q.size,new int[]{}));
        test("queue.reuse", "encolar tras vaciar", () -> q.queue(9), () -> chain(q.pFirst,q.pLast,q.size,new int[]{9}));
        test("queue.clear", "vaciar y vaciar de nuevo", () -> {q.Empty();q.Empty();}, () -> q.isEmpty() && chain(q.pFirst,q.pLast,q.size,new int[]{}));
        test("queue.dequeue.empty", "EstructuraVaciaException sin mutacion", () -> expectException(EstructuraVaciaException.class, q::dequeue), () -> q.isEmpty() && chain(q.pFirst,q.pLast,q.size,new int[]{}));
        test("queue.peek.empty", "EstructuraVaciaException sin mutacion", () -> expectException(EstructuraVaciaException.class, q::peek), () -> q.isEmpty() && chain(q.pFirst,q.pLast,q.size,new int[]{}));
        observe("queue.enqueue.null", () -> q.queue(null));
        MyQueue<Integer> dq = new MyQueue<>();
        test("queue.duplicates", "conservar duplicados", () -> {dq.queue(7);dq.queue(7);}, () -> chain(dq.pFirst,dq.pLast,dq.size,new int[]{7,7}));
        MyStack<Integer> s = new MyStack<>();
        test("stack.construct", "vacia", () -> {}, () -> stackMatches(s,new int[]{}));
        test("stack.push", "tope 3,2,1", () -> {s.push(1);s.push(2);s.push(3);}, () -> stackMatches(s,new int[]{3,2,1}));
        test("stack.pop", "retira 3", () -> { if(s.pop()!=3) throw new IllegalStateException(); }, () -> stackMatches(s,new int[]{2,1}));
        test("stack.drain", "vaciar", () -> {if(s.pop()!=2 || s.pop()!=1) throw new IllegalStateException();}, () -> stackMatches(s,new int[]{}));
        test("stack.pop.empty", "EstructuraVaciaException sin mutacion", () -> expectException(EstructuraVaciaException.class, s::pop), () -> stackMatches(s,new int[]{}));
        test("stack.emptyInvariant", "vacia tras pop vacio", () -> {}, () -> stackMatches(s,new int[]{}));
        test("stack.reuse", "push tras vaciar", () -> s.push(9), () -> stackMatches(s,new int[]{9}));
        test("stack.clear", "clear idempotente", () -> {s.putEmpty();s.putEmpty();}, () -> stackMatches(s,new int[]{}));
        observe("stack.push.null", () -> s.push(null));
        MyStack<Integer> ds = new MyStack<>();
        test("stack.duplicates", "conservar duplicados", () -> {ds.push(7);ds.push(7);}, () -> stackMatches(ds,new int[]{7,7}));
        System.out.println("PASS=" + passed + "; FAIL=" + failures + "; OBS=3 (null sin contrato definitivo)");
        if (failures != 0) throw new AssertionError("Fallaron " + failures + " casos");
    }
}
