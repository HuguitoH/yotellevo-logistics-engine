package edu.msmk.clases.service.routing;

import edu.msmk.clases.model.Paquete;
import edu.msmk.clases.model.PaqueteApilado;
import lombok.Data;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

/**
 * Representa una pila LIFO de paquetes en una furgoneta.
 * <p>
 * CONCEPTO CLAVE:
 * - La ruta optimizada es: Almacén → A → B → C → Almacén
 * - Pero los paquetes se apilan en ORDEN INVERSO: C, B, A
 * - Así el primero que sacas (A) es el primero que entregas
 * <p>
 * Ejemplo:
 * ┌─────────────────┐
 * │   Paquete A     │ ← ÚLTIMO en entrar (arriba) = PRIMERO en salir
 * ├─────────────────┤
 * │   Paquete B     │
 * ├─────────────────┤
 * │   Paquete C     │ ← PRIMERO en entrar (abajo) = ÚLTIMO en salir
 * └─────────────────┘
 */
@Data
public class PilaFurgoneta {

    private final String id;
    private final int capacidadMaxima;
    private final double pesoMaximo; // kg
    private final Deque<PaqueteApilado> pila;
    private int ordenCarga;
    private double distanciaRuta;

    public PilaFurgoneta(String id, int capacidadMaxima, double pesoMaximo) {
        this.id = id;
        this.capacidadMaxima = capacidadMaxima;
        this.pesoMaximo = pesoMaximo;
        this.pila = new LinkedList<>();
        this.ordenCarga = 0;
    }

    /**
     * Apilar un paquete (PUSH)
     *
     * @return PaqueteApilado con información de posición
     * @throws IllegalStateException si la furgoneta está llena o excede peso
     */
    public PaqueteApilado apilar(Paquete paquete) {
        if (estaLlena()) {
            throw new IllegalStateException(
                    String.format("Furgoneta %s llena. Capacidad máxima: %d paquetes",
                            id, capacidadMaxima));
        }

        if (getPesoTotal() + paquete.getPeso() > pesoMaximo) {
            throw new IllegalStateException(
                    String.format("Peso excedido. Actual: %.2f kg, Máximo: %.2f kg",
                            getPesoTotal() + paquete.getPeso(), pesoMaximo));
        }

        ordenCarga++;
        double posicionZ = calcularPosicionZ();

        PaqueteApilado paqueteApilado = new PaqueteApilado(
                paquete,
                ordenCarga,
                posicionZ
        );

        pila.push(paqueteApilado);

        return paqueteApilado;
    }

    /**
     * Desapilar el siguiente paquete (POP)
     *
     * @return El paquete que está arriba de la pila
     * @throws IllegalStateException si la pila está vacía
     */
    public PaqueteApilado desapilar() {
        if (estaVacia()) {
            throw new IllegalStateException("La furgoneta está vacía");
        }
        return pila.pop();
    }

    /**
     * Ver el siguiente paquete sin sacarlo (PEEK)
     *
     * @return El paquete que está arriba, sin sacarlo de la pila
     */
    public PaqueteApilado verSiguiente() {
        return pila.peek();
    }

    /**
     * Calcula la altura actual de la pila para posicionar el próximo paquete
     */
    private double calcularPosicionZ() {
        return pila.stream()
                .mapToDouble(p -> PaqueteApilado.ALTURA_CAJA)
                .sum();
    }

    /**
     * Calcula el peso total de los paquetes en la furgoneta
     */
    public double getPesoTotal() {
        return pila.stream()
                .mapToDouble(p -> p.getPaquete().getPeso())
                .sum();
    }

    /**
     * Obtiene una vista de todos los paquetes (de arriba a abajo)
     */
    public List<PaqueteApilado> obtenerVista() {
        return new ArrayList<>(pila);
    }

    /**
     * Obtiene el número de paquetes actuales
     */
    public int getPaquetesActuales() {
        return pila.size();
    }

    /**
     * Calcula el porcentaje de ocupación
     */
    public double getPorcentajeOcupacion() {
        return (getPaquetesActuales() / (double) capacidadMaxima) * 100.0;
    }

    /**
     * Verifica si la pila está vacía
     */
    public boolean estaVacia() {
        return pila.isEmpty();
    }

    /**
     * Verifica si la pila está llena
     */
    public boolean estaLlena() {
        return pila.size() >= capacidadMaxima;
    }

    /**
     * Obtiene información de resumen
     */
    public String obtenerResumen() {
        return String.format(
                "Furgoneta %s: %d/%d paquetes, %.2f/%.2f kg (%.1f%% ocupación)",
                id,
                getPaquetesActuales(),
                capacidadMaxima,
                getPesoTotal(),
                pesoMaximo,
                getPorcentajeOcupacion()
        );
    }
}
