package com.avilaos.core.structures;

// Se utiliza MyComparator propio en lugar de java.util.Comparator

/**
 * Pruebas de verificación de métodos oficiales de Fase 1:
 * - MyLinkedList: add, addFirst, remove, get, contains, sort, iterador manual
 * - MyQueue: enqueue, size()
 * - MyStack: peek()
 */
public class NuevasPrimitivasTest {

    public static void main(String[] args) {
        testLinkedListAddAndGet();
        testLinkedListRemoveAndContains();
        testLinkedListSort();
        testLinkedListIterable();
        testQueueEnqueueAndSize();
        testStackPeek();
        System.out.println("TODAS LAS PRUEBAS DE NUEVAS PRIMITIVAS PASARON EXITOSAMENTE.");
    }

    private static void testLinkedListAddAndGet() {
        MyLinkedList<String> list = new MyLinkedList<>();
        list.add("B");
        list.add("C");
        list.addFirst("A");

        if (list.size() != 3) throw new AssertionError("size incorrecto");
        if (!"A".equals(list.get(0))) throw new AssertionError("get(0) incorrecto");
        if (!"B".equals(list.get(1))) throw new AssertionError("get(1) incorrecto");
        if (!"C".equals(list.get(2))) throw new AssertionError("get(2) incorrecto");
    }

    private static void testLinkedListRemoveAndContains() {
        MyLinkedList<Integer> list = new MyLinkedList<>();
        list.add(10);
        list.add(20);
        list.add(30);

        if (!list.contains(20)) throw new AssertionError("debe contener 20");
        if (list.contains(99)) throw new AssertionError("no debe contener 99");

        boolean removed = list.remove(Integer.valueOf(20));
        if (!removed) throw new AssertionError("debe remover 20");
        if (list.size() != 2) throw new AssertionError("size debe ser 2");
        if (list.contains(20)) throw new AssertionError("ya no debe contener 20");
        if (list.get(0) != 10 || list.get(1) != 30) throw new AssertionError("orden incorrecto tras remove");

        // Remover extremos
        list.remove(Integer.valueOf(10));
        list.remove(Integer.valueOf(30));
        if (!list.isEmpty() || list.size() != 0) throw new AssertionError("debe quedar vacia");
    }

    private static void testLinkedListSort() {
        MyLinkedList<Integer> list = new MyLinkedList<>();
        list.add(40);
        list.add(10);
        list.add(30);
        list.add(20);

        list.sort(new MyLinkedList.MyComparator<Integer>() {
            @Override
            public int compare(Integer o1, Integer o2) {
                return Integer.compare(o1, o2);
            }
        });

        if (list.size() != 4) throw new AssertionError("size incorrecto tras sort");
        if (list.get(0) != 10 || list.get(1) != 20 || list.get(2) != 30 || list.get(3) != 40) {
            throw new AssertionError("sort no ordeno correctamente");
        }
    }

    private static void testLinkedListIterable() {
        MyLinkedList<String> list = new MyLinkedList<>();
        list.add("uno");
        list.add("dos");
        list.add("tres");

        StringBuilder sb = new StringBuilder();
        MyLinkedList<String>.MyLinkedListIterator iter = list.iterator();
        while (iter.hasNext()) {
            sb.append(iter.next()).append(",");
        }

        if (!"uno,dos,tres,".equals(sb.toString())) {
            throw new AssertionError("iterador fallo: " + sb);
        }
    }

    private static void testQueueEnqueueAndSize() {
        MyQueue<String> queue = new MyQueue<>();
        if (queue.size() != 0) throw new AssertionError("size inicial incorrecto");

        queue.enqueue("P1");
        queue.enqueue("P2");
        if (queue.size() != 2) throw new AssertionError("size incorrecto tras enqueue");
        if (!"P1".equals(queue.peek())) throw new AssertionError("peek incorrecto");
        if (!"P1".equals(queue.dequeue())) throw new AssertionError("dequeue incorrecto");
        if (queue.size() != 1) throw new AssertionError("size incorrecto tras dequeue");
    }

    private static void testStackPeek() {
        MyStack<Integer> stack = new MyStack<>();
        stack.push(100);
        stack.push(200);

        if (stack.peek() != 200) throw new AssertionError("peek incorrecto");
        if (stack.size() != 2) throw new AssertionError("peek no debe reducir size");
        if (stack.pop() != 200) throw new AssertionError("pop incorrecto");
        if (stack.peek() != 100) throw new AssertionError("peek tras pop incorrecto");
    }
}
