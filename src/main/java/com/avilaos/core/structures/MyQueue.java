package com.avilaos.core.structures;
import com.avilaos.core.model.ProcessControlBlock;

/**
 * Cola genérica propia (FIFO - First In, First Out).
 * 
 * Se utilizará para gestionar las distintas colas del sistema operativo:
 * nuevos, listos, bloqueados, terminados y colas de semáforos.
 * 
 * @param <T> Tipo de elemento almacenado en la cola.
 */
public class MyQueue<T> {
    Node<T> pFirst;
    Node<T> pLast;
    int size;

    public MyQueue() {
        this.pFirst = null;
        this.pLast = null;
        this.size = 0;
    }

    public boolean isEmpty() {
        return this.pFirst == null && this.size == 0;
    }

    public void Empty() {
        this.pFirst = null;
        this.pLast = null;
        this.size = 0;
    }

    public int size() {
        return this.size;
    }

    /**
     * Encola un elemento al final de la estructura (contrato oficial).
     *
     * @param item Elemento a encolar.
     */
    public void enqueue(T item) {
        queue(item);
    }

    public void queue(T data) {
        Node<T> pNew = new Node<>(data);
        if (isEmpty()) {
            this.pFirst = pNew;
            this.pLast = pNew;
        } else {
            this.pLast.pNext = pNew;
            this.pLast = pNew;
        }
        this.size++;
    }

    public T dequeue() {
        if (isEmpty()) {
            throw new EstructuraVaciaException("No se puede desencolar de una cola vacia");
        }
        T data = this.pFirst.data;
        this.pFirst = this.pFirst.pNext;
        this.size--;
        if (this.pFirst == null) {
            this.pLast = null;
        }
        return data;
    }

    public T peek() {
        if (isEmpty()) {
            throw new EstructuraVaciaException("No se puede consultar una cola vacia");
        }
        return this.pFirst.data;
    }
    
    public boolean removeElement(T data) {
        if (isEmpty()) {
            return false;
        }
        if (this.pFirst.data.equals(data)) {
            dequeue();
            return true;
        }
        Node<T> current = this.pFirst;
        while (current.pNext != null) {
            if (current.pNext.data.equals(data)) {
                current.pNext = current.pNext.pNext;
                if (current.pNext == null) {
                    this.pLast = current;
                }
                this.size--;
                return true;
            }
            current = current.pNext;
        }
        return false; 
    }

    /**
     * Retorna una copia de los elementos actuales de la cola en un arreglo primitivo.
     *
     * @return Arreglo con los elementos en orden FIFO.
     */
    public Object[] toArray() {
        int count = this.size;
        Object[] arr = new Object[count];
        Node<T> current = this.pFirst;
        int i = 0;
        while (current != null && i < count) {
            arr[i++] = current.data;
            current = current.pNext;
        }
        return arr;
    }

    @Override
    public String toString() {
        if (isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        Node<T> current = pFirst;
        while (current != null) {
            if (current.data instanceof ProcessControlBlock pcb) {
                sb.append(pcb.getName() != null ? pcb.getName() : "P").append("(").append(pcb.getId()).append(")");
            } else {
                sb.append(current.data.toString());
            }
            current = current.pNext;
            if (current != null) sb.append(", ");
        }
        sb.append("]");
        return sb.toString();
    }
}
