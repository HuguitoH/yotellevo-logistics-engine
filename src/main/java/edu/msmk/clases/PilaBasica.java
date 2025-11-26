package edu.msmk.clases;

import java.util.ArrayList;
import java.util.EmptyStackException;

/**
 * Implementación de una Pila (Stack) usando ArrayList
 * Estructura LIFO (Last In, First Out)
 */
public class PilaBasica {
    private ArrayList<Integer> elementos;
    private int capacidadMaxima;

    /**
     * Constructor: Crea una pila sin límite de capacidad
     */
    public PilaBasica() {
        this.elementos = new ArrayList<>();
        this.capacidadMaxima = Integer.MAX_VALUE;
    }

    /**
     * Constructor: Crea una pila con capacidad máxima
     */
    public PilaBasica(int capacidadMaxima) {
        this.elementos = new ArrayList<>();
        this.capacidadMaxima = capacidadMaxima;
    }

    /**
     * Verifica si la pila está vacía
     * Complejidad: O(1)
     */
    public boolean isEmpty() {
        return elementos.isEmpty();
    }

    /**
     * Verifica si la pila está llena
     * Complejidad: O(1)
     */
    public boolean isFull() {
        return elementos.size() >= capacidadMaxima;
    }

    /**
     * Añade un elemento al tope de la pila (PUSH)
     * Complejidad: O(1) amortizado
     * @throws IllegalStateException si la pila está llena
     */
    public void push(int elemento) {
        if (isFull()) {
            throw new IllegalStateException("La pila está llena. Capacidad máxima: " + capacidadMaxima);
        }
        elementos.add(elemento);
    }

    /**
     * Elimina y devuelve el elemento del tope (POP)
     * Complejidad: O(1)
     * @throws EmptyStackException si la pila está vacía
     */
    public int pop() {
        if (isEmpty()) {
            throw new EmptyStackException();
        }
        return elementos.remove(elementos.size() - 1);
    }

    /**
     * Devuelve el elemento del tope SIN eliminarlo (PEEK/TOP)
     * Complejidad: O(1)
     * @throws EmptyStackException si la pila está vacía
     */
    public int top() {
        if (isEmpty()) {
            throw new EmptyStackException();
        }
        return elementos.get(elementos.size() - 1);
    }

    /**
     * Devuelve el número de elementos en la pila
     * Complejidad: O(1)
     */
    public int size() {
        return elementos.size();
    }

    /**
     * Devuelve la capacidad máxima de la pila
     */
    public int getCapacidadMaxima() {
        return capacidadMaxima;
    }

    /**
     * Limpia todos los elementos de la pila
     */
    public void clear() {
        elementos.clear();
    }

    @Override
    public String toString() {
        if (isEmpty()) {
            return "PilaBasica[]";
        }
        return "PilaBasica" + elementos.toString() + " <- TOPE";
    }
}