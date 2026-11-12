package com.avilaos.core.structures;

/**
 * Lista enlazada simple genérica propia sin usar colecciones de java.util
 * y sin declarar ninguna interfaz.
 * 
 * Responsabilidades:
 * - Inserción al inicio, al final y por posición.
 * - Eliminación por valor, por posición y de extremos.
 * - Búsqueda, verificación de contención y acceso por índice.
 * - Ordenamiento in-situ con clase abstracta comparadora.
 * - Soporte para iteración manual mediante clase concreta.
 * 
 * @param <T> Tipo de elemento almacenado.
 */
public class MyLinkedList<T> {
    public static abstract class MyComparator<E> {
        public abstract int compare(E o1, E o2);
    }

    /** Excepción propia para reemplazar NoSuchElementException */
    public static class ElementoNoEncontradoException extends RuntimeException {
        public ElementoNoEncontradoException(String message) {
            super(message);
        }
    }

    // --- ESTRUCTURA DE LA LISTA ---

    Node<T> pFirst;
    Node<T> pLast;
    int size;

    public MyLinkedList() {
        this.pFirst = null;
        this.pLast = null;
        this.size = 0;
    }

    public boolean isEmpty() {
        return (this.pFirst == null && this.pLast == null) && this.size == 0;
    }

    public int size() {
        return this.size;
    }

    public void add(T data) {
        append(data);
    }

    public void append(T data) {
        Node<T> pNew = new Node<>(data);
        if (isEmpty()) {
            this.pFirst = this.pLast = pNew;
        } else {
            this.pLast.pNext = pNew;
            this.pLast = pNew;
        }
        this.size++;
    }

    public void addFirst(T data) {
        insertFirst(data);
    }

    public void insertFirst(T data) {
        Node<T> pNew = new Node<>(data);
        if (isEmpty()) {
            this.pFirst = this.pLast = pNew;
        } else {
            pNew.pNext = this.pFirst;
            this.pFirst = pNew;
        }
        this.size++;
    }

    public void deleteFirst() {
        if (isEmpty()) {
            throw new EstructuraVaciaException("No se puede borrar de una lista vacia");
        }
        this.pFirst = this.pFirst.pNext;
        this.size--;
        if (this.pFirst == null) {
            this.pLast = null;
        }
    }

    public void deleteLast() {
        if (isEmpty()) {
            throw new EstructuraVaciaException("No se puede borrar de una lista vacia");
        }
        if (this.size == 1) {
            deleteFirst();
            return;
        }
        Node<T> aux = this.pFirst;
        while (aux.pNext != this.pLast) {
            aux = aux.pNext;
        }
        this.pLast = aux;
        this.pLast.pNext = null;
        this.size--;
    }

    public void insertPos(int pos, T data) {
        if (pos < 0 || pos > this.size) {
            throw new IndexOutOfBoundsException("Posicion de insercion invalida: " + pos);
        }
        if (pos == 0) {
            insertFirst(data);
            return;
        }
        if (pos == this.size) {
            append(data);
            return;
        }
        Node<T> aux = this.pFirst;
        for (int i = 0; i < pos - 1; i++) {
            aux = aux.pNext;
        }
        Node<T> pNew = new Node<>(data);
        pNew.pNext = aux.pNext;
        aux.pNext = pNew;
        this.size++;
    }

    public void deletePos(int pos) {
        if (pos < 0 || pos >= this.size) {
            throw new IndexOutOfBoundsException("Posicion de borrado invalida: " + pos);
        }
        if (pos == 0) {
            deleteFirst();
            return;
        }
        if (pos == this.size - 1) {
            deleteLast();
            return;
        }
        Node<T> aux = this.pFirst;
        for (int i = 0; i < pos - 1; i++) {
            aux = aux.pNext;
        }
        aux.pNext = aux.pNext.pNext;
        this.size--;
    }

    public boolean remove(T data) {
        if (isEmpty()) {
            return false;
        }
        if (equalsData(this.pFirst.data, data)) {
            deleteFirst();
            return true;
        }
        Node<T> current = this.pFirst;
        while (current.pNext != null) {
            if (equalsData(current.pNext.data, data)) {
                if (current.pNext == this.pLast) {
                    this.pLast = current;
                }
                current.pNext = current.pNext.pNext;
                this.size--;
                return true;
            }
            current = current.pNext;
        }
        return false;
    }

    public T get(int index) {
        if (index < 0 || index >= this.size) {
            throw new IndexOutOfBoundsException("Posicion invalida: " + index);
        }
        Node<T> current = this.pFirst;
        for (int i = 0; i < index; i++) {
            current = current.pNext;
        }
        return current.data;
    }

    public boolean contains(T data) {
        Node<T> current = this.pFirst;
        while (current != null) {
            if (equalsData(current.data, data)) {
                return true;
            }
            current = current.pNext;
        }
        return false;
    }

    /**
     * Ordena la lista enlazada in-situ utilizando una clase abstracta comparadora.
     */
    public void sort(MyComparator<T> comp) {
        if (this.size <= 1) {
            return;
        }
        Node<T> sortedHead = null;
        Node<T> current = this.pFirst;
        while (current != null) {
            Node<T> next = current.pNext;
            if (sortedHead == null || comp.compare(current.data, sortedHead.data) <= 0) {
                current.pNext = sortedHead;
                sortedHead = current;
            } else {
                Node<T> search = sortedHead;
                while (search.pNext != null && comp.compare(search.pNext.data, current.data) < 0) {
                    search = search.pNext;
                }
                current.pNext = search.pNext;
                search.pNext = current;
            }
            current = next;
        }
        this.pFirst = sortedHead;
        Node<T> tail = sortedHead;
        while (tail != null && tail.pNext != null) {
            tail = tail.pNext;
        }
        this.pLast = tail;
    }

    // --- RECORRIDO DE LA LISTA SIN INTERFACES ---

    /**
     * Devuelve una instancia de la clase concreta iteradora.
     */
    public MyLinkedListIterator iterator() {
        return new MyLinkedListIterator();
    }

    /** Clase concreta para recorrer la lista */
    public class MyLinkedListIterator {
        private Node<T> current = pFirst;

        public boolean hasNext() {
            return current != null;
        }

        public T next() {
            if (!hasNext()) {
                throw new ElementoNoEncontradoException("No hay mas elementos en la lista");
            }
            T val = current.data;
            current = current.pNext;
            return val;
        }
    }

    private boolean equalsData(T a, T b) {
        return (a == null) ? (b == null) : a.equals(b);
    }
}