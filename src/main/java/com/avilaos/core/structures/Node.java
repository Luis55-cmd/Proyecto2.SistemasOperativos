package com.avilaos.core.structures;

/**
 * Nodo simple genérico para estructuras enlazadas propias.
 * 
 * @param <T> Tipo de dato almacenado en el nodo.
 */

public class Node<T> {
    public T data;
    public Node<T> pNext;

    public Node(T data) {
        this.data = data;
        this.pNext = null;
    }

    public Node(T data, Node<T> next) {
        this.data = data;
        this.pNext = next;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public Node<T> getNext() {
        return pNext;
    }

    public void setNext(Node<T> next) {
        this.pNext = next;
    }
}
