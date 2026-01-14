package edu.msmk.clases.demos;

import edu.msmk.clases.service.CoberturaServicio;
import edu.msmk.clases.exchange.PeticionCliente;

public class DemoValidacionCompleta {

    public static void main(String[] args) {
        System.out.println("DEMO: Validación de Direcciones COMPLETAS");
        System.out.println("\n");

        // Cargar tramos
        CoberturaServicio cobertura = new CoberturaServicio();

        System.out.println("CARGANDO TRAMOS DE COBERTURA ");
        /*cobertura.addTramo(1, 1, 1701, 1001, 1, 27);     // Álava - TORRONDOA
        cobertura.addTramo(1, 1, 1701, 1002, 1, 50);     // Álava - AÑUA BIDEA
        cobertura.addTramo(28, 79, 7901, 12345, 1, 100); // Madrid
        cobertura.addTramo(8, 19, 1901, 54321, 2, 200);  // Barcelona */

        System.out.println("Tramos cargados: " + cobertura.numeroTramosCubiertos());
        System.out.println("Provincias cubiertas: " + cobertura.numeroProvinciasCubiertas());

        // CASO 1: Petición COMPLETA y CUBIERTA
        System.out.println("\n\n CASO 1: Petición COMPLETA y CUBIERTA ");
        try {
            PeticionCliente p1 = new PeticionCliente(1, 1, 1701, 1001, 15);
            System.out.println("Petición: " + p1.getDireccionLegible());
            System.out.println("Clave: " + p1.getClave());

            long inicio = System.nanoTime();
            boolean resultado = cobertura.damosServicio(p1);
            long tiempo = System.nanoTime() - inicio;

            System.out.println("Resultado: " + (resultado ? "CUBIERTA" : "NO CUBIERTA"));
            System.out.println("Tiempo: " + tiempo + " ns (" +
                    String.format("%.3f", tiempo/1000.0) + " μs)");
            System.out.println("¿Podemos entregar?: " + (resultado ? "SÍ" : "NO"));
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }

        // CASO 2: Petición COMPLETA pero NO CUBIERTA
        System.out.println("\n\n CASO 2: Petición COMPLETA pero NO CUBIERTA ");
        try {
            PeticionCliente p2 = new PeticionCliente(1, 1, 1701, 9999, 10);
            System.out.println("Petición: " + p2.getDireccionLegible());
            System.out.println("Clave: " + p2.getClave());

            long inicio = System.nanoTime();
            boolean resultado = cobertura.damosServicio(p2);
            long tiempo = System.nanoTime() - inicio;

            System.out.println(" Resultado: " + (resultado ? "CUBIERTA" : "NO CUBIERTA"));
            System.out.println(" Tiempo: " + tiempo + " ns (" +
                    String.format("%.3f", tiempo/1000.0) + " μs)");
            System.out.println(" ¿Podemos entregar?: " + (resultado ? "SÍ" : "NO"));
        } catch (Exception e) {
            System.out.println(" Error: " + e.getMessage());
        }

        // CASO 3: Petición INCOMPLETA (falta número)
        System.out.println("\n\n CASO 3: Petición INCOMPLETA (falta número)");
        try {
            PeticionCliente p3 = new PeticionCliente(1, 1, 1701, 1001, null);
            System.out.println("Petición: " + p3.getDireccionLegible());

            boolean resultado = cobertura.damosServicio(p3);
            System.out.println("Resultado: " + resultado);
        } catch (IllegalArgumentException e) {
            System.out.println("ERROR CAPTURADO (esperado): " + e.getMessage());
        }

        // CASO 4: Petición INCOMPLETA (falta municipio)
        System.out.println("\n\n CASO 4: Petición INCOMPLETA (solo provincia)");
        try {
            // Esto debería lanzar excepción en el constructor
            PeticionCliente p4 = new PeticionCliente(1, null, null, null, null);
            System.out.println("Petición: " + p4);
        } catch (IllegalArgumentException e) {
            System.out.println("ERROR CAPTURADO en constructor: " + e.getMessage());
        }

        // CASO 5: Múltiples validaciones (rendimiento)
        System.out.println("\n\n  CASO 5: Prueba de RENDIMIENTO");
        PeticionCliente pTest = new PeticionCliente(1, 1, 1701, 1001, 10);
        int numPruebas = 100000;

        System.out.println("Realizando " + numPruebas + " validaciones...");
        long inicioTotal = System.nanoTime();

        for (int i = 0; i < numPruebas; i++) {
            cobertura.damosServicio(pTest);
        }

        long tiempoTotal = System.nanoTime() - inicioTotal;
        double promedio = tiempoTotal / (double) numPruebas;

        System.out.println("Tiempo total: " + tiempoTotal / 1_000_000 + " ms");
        System.out.println("Tiempo promedio: " + String.format("%.2f", promedio) + " ns");
        System.out.println("Tiempo promedio: " + String.format("%.3f", promedio/1000.0) + " μs");
        System.out.println("Consultas/segundo: " + String.format("%.0f", 1_000_000_000.0 / promedio));

        System.out.println("\n");
        System.out.println("CONCLUSIONES");
        System.out.println("Solo se validan direcciones COMPLETAS (5 campos)");
        System.out.println("Validación en O(1) usando HashSet");
        System.out.println("Excepciones claras si faltan campos");
        System.out.println("Rendimiento: < 1 microsegundo por validación");
    }
}