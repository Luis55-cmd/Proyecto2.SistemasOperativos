package com.avilaos.core.structures;


/**
 * Pila genérica propia (LIFO - Last In, First Out).
 * 
 * @param <T> Tipo de elemento almacenado en la pila.
 */
public class MyStack<T> {
    Node<T> top;
    int size;

    public MyStack() {
        this.top = null;
        this.size = 0;
    }

    public boolean isEmpty() {
        return this.top == null && this.size == 0;
    }

    public void putEmpty() {
        this.top = null;
        this.size = 0;
    }

    public void push(T data) {
        Node<T> pNew = new Node<>(data);
        pNew.pNext = this.top;
        this.top = pNew;
        this.size++;
    }

    public T pop() {
        if (isEmpty()) {
            throw new EstructuraVaciaException("No se puede desempilar de una pila vacia");
        }
        T data = this.top.data;
        this.top = this.top.pNext;
        this.size--;
        return data;
    }

    /**
     * Consulta el elemento en el tope de la pila sin removerlo.
     *
     * @return Elemento en el tope.
     * @throws EstructuraVaciaException si la pila está vacía.
     */
    public T peek() {
        if (isEmpty()) {
            throw new EstructuraVaciaException("No se puede consultar una pila vacia");
        }
        return this.top.data;
    }

    public int size() {
        return this.size;
    }
}
