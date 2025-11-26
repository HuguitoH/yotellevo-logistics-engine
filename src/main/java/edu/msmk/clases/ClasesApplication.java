package edu.msmk.clases;

import edu.msmk.clases.exchange.PeticionCliente;
import edu.msmk.clases.service.TramoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;

@Slf4j
@SpringBootApplication
public class ClasesApplication implements CommandLineRunner {

    private final TramoService tramoService;

    public ClasesApplication(TramoService tramoService) {
        this.tramoService = tramoService;
    }

    public static void main(String[] args) {
        SpringApplication.run(ClasesApplication.class, args);
    }

    @Override
    public void run(String... args) {
        log.info("YoTeLoLlevo - Sistema de Entregas");

        try {
            // 1. CARGAR COBERTURA DESDE ARCHIVO
            log.info("\n1. Cargando cobertura de tramos desde BOE...");
            long inicioGlobal = System.currentTimeMillis();

            CoberturaServicio coberturaServicio = tramoService.leerTramos();

            long tiempoCarga = System.currentTimeMillis() - inicioGlobal;
            log.info("Cobertura cargada correctamente en {} ms", tiempoCarga);
            log.info(" Provincias cubiertas: {}", coberturaServicio.numeroProvinciasCubiertas());
            log.info(" Tramos cubiertos: {}", coberturaServicio.numeroTramosCubiertos());

            // 2. DEMO: VALIDACIÓN DE PETICIONES
            log.info("\n2. Demostracion de validacion de direcciones COMPLETAS:");
            demoPeticiones(coberturaServicio);

            // 3. DEMO: PILA BÁSICA
            log.info("\n3. Demostracion de Pila Basica (Stack LIFO):");
            demoPilaBasica();

            // 4. MÉTRICAS DE RENDIMIENTO
            log.info("\n4. Midiendo rendimiento de validaciones:");
            medirRendimiento(coberturaServicio);

            log.info("\n");
            log.info("Sistema inicializado correctamente");

        } catch (IOException e) {
            log.error("Error al procesar el archivo: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Error inesperado: {}", e.getMessage(), e);
        }
    }

    /**
     * Demuestra la validación de diferentes peticiones de clientes
     * TODAS las peticiones deben tener los 5 campos completos
     */
    private void demoPeticiones(CoberturaServicio coberturaServicio) {
        log.info("\n");
        log.info("VALIDACIÓN DE DIRECCIONES (Estructura BOE)");


        log.info("\nEstructura BOE de tramos:");
        log.info("  CPRO (0-2):   Código provincia (2 dígitos)");
        log.info("  CMUM (2-5):   Código municipio (3 dígitos)");
        log.info("  CUN (13-20):  Código unidad poblacional (7 dígitos)");
        log.info("  CVIA (20-25): Código vía (5 dígitos)");
        log.info("  Número:       Número del portal");

        // Petición 1: ÁLAVA - ALEGRIA-DULANTZI - TORRONDOA (CUBIERTA)
        log.info("\n PETICIÓN 1: Álava - ALEGRIA-DULANTZI - TORRONDOA ");
        try {
            PeticionCliente peticion1 = new PeticionCliente(
                    1,      // CPRO: Provincia Álava
                    1,      // CMUM: Municipio 001
                    1701,   // CUN: Unidad poblacional ALEGRIA-DULANTZI
                    1001,   // CVIA: Código vía TORRONDOA
                    15      // Número: 15 (dentro del rango 1-27)
            );
            validarPeticion(coberturaServicio, peticion1, "TORRONDOA 15, 01001 ALEGRIA-DULANTZI, ÁLAVA");
        } catch (Exception e) {
            log.error("Error: {}", e.getMessage());
        }

        // Petición 2: ÁLAVA - ALEGRIA-DULANTZI - AÑUA BIDEA (CUBIERTA)
        log.info("\n  PETICIÓN 2: Álava - ALEGRIA-DULANTZI - AÑUA BIDEA ");
        try {
            PeticionCliente peticion2 = new PeticionCliente(
                    1,      // CPRO: Provincia Álava
                    1,      // CMUM: Municipio 001
                    1701,   // CUN: Unidad poblacional
                    1002,   // CVIA: Código vía AÑUA BIDEA
                    8       // Número: 8
            );
            validarPeticion(coberturaServicio, peticion2, "AÑUA BIDEA 8, 01002 ALEGRIA-DULANTZI, ÁLAVA");
        } catch (Exception e) {
            log.error("Error: {}", e.getMessage());
        }

        // Petición 3: MADRID (si está en el archivo)
        log.info("\n PETICIÓN 3: Madrid ");
        try {
            PeticionCliente peticion3 = new PeticionCliente(
                    28,     // CPRO: Provincia Madrid
                    79,     // CMUM: Municipio 079
                    7901,   // CUN: Unidad poblacional
                    12345,  // CVIA: Código vía (ejemplo)
                    42      // Número: 42
            );
            validarPeticion(coberturaServicio, peticion3, "Vía 12345 Num 42, MADRID");
        } catch (Exception e) {
            log.error("Error: {}", e.getMessage());
        }

        // Petición 4: ÁLAVA - Vía NO CUBIERTA
        log.info("\n PETICIÓN 4: Álava - Vía NO CUBIERTA ");
        try {
            PeticionCliente peticion4 = new PeticionCliente(
                    1,      // CPRO: Provincia Álava (cubierta)
                    1,      // CMUM: Municipio 001 (cubierto)
                    1701,   // CUN: Unidad poblacional (cubierta)
                    9999,   // CVIA: Código vía NO EXISTE
                    10      // Número: 10
            );
            validarPeticion(coberturaServicio, peticion4, "Vía INEXISTENTE 10, ÁLAVA");
        } catch (Exception e) {
            log.error("Error: {}", e.getMessage());
        }

        // Petición 5: Provincia NO CUBIERTA
        log.info("\n PETICIÓN 5: Provincia NO CUBIERTA ");
        try {
            PeticionCliente peticion5 = new PeticionCliente(
                    99,     // CPRO: Provincia INEXISTENTE
                    999,    // CMUM: Municipio
                    9999999,// CUN: Unidad poblacional
                    99999,  // CVIA: Código vía
                    99      // Número
            );
            validarPeticion(coberturaServicio, peticion5, "PROVINCIA INEXISTENTE");
        } catch (Exception e) {
            log.error("Error: {}", e.getMessage());
        }

        // Petición 6: Intento con campos incompletos (debe fallar)
        log.info("\n PETICIÓN 6: INCOMPLETA (debe fallar en constructor) ");
        try {
            PeticionCliente peticion6 = new PeticionCliente(1, null, null, null, null);
            log.error("ERROR: Esta petición NO debería haberse creado");
        } catch (IllegalArgumentException e) {
            log.info("Constructor rechazó petición incompleta (comportamiento esperado)");
            log.info(" Motivo: {}", e.getMessage());
        }

        // Petición 7: Intento TRAM.EMPRESA
        log.info("\n Petición 7: Prueba TRAM.EMPRESA");
        try {
            PeticionCliente peticion7 = new PeticionCliente(28,115,4301,2492,5);
            validarPeticion(coberturaServicio, peticion7, "Vía 2492 Num 5, POZUELO");
        } catch (Exception e) {
            log.error("Error: {}", e.getMessage());
        }

    }

    /**
     * Valida una petición y muestra los resultados
     */
    private void validarPeticion(CoberturaServicio cobertura, PeticionCliente peticion, String descripcion) {
        log.info("Dirección: {}", descripcion);
        log.info("Provincia: {}, Municipio: {}, UnidadPobl: {}, Via: {}, Numero: {}",
                peticion.getProvincia(),
                peticion.getMunicipio(),
                peticion.getUnidadPoblacional(),
                peticion.getVia(),
                peticion.getNumero());
        log.info("Clave generada: {}", peticion.getClave());

        long inicio = System.nanoTime();
        boolean resultado = cobertura.damosServicio(peticion);
        long tiempo = System.nanoTime() - inicio;

        String emoji = resultado ? "✓" : "✗";
        String estado = resultado ? "CUBIERTA" : "NO CUBIERTA";

        log.info("  {} Resultado: {}", emoji, estado);
        log.info("  {} Tiempo: {} ns ({} μs)",
                emoji, tiempo, String.format("%.3f", tiempo / 1000.0));
        log.info("  {} ¿Podemos entregar?: {}", emoji, resultado ? "SÍ" : "NO");
    }

    /**
     * Demuestra el funcionamiento de la pila básica
     */
    private void demoPilaBasica() {
        log.info("\n");
        log.info("Pila Básica (Stack LIFO)");

        PilaBasica pila = new PilaBasica(10);

        log.info("\nPila creada con capacidad máxima: {}", pila.getCapacidadMaxima());
        log.info("¿Está vacía?: {}", pila.isEmpty());
        log.info("Tamaño inicial: {}", pila.size());

        // Push de elementos
        log.info("\n OPERACIÓN: PUSH (Añadir elementos)");
        log.info("Agregando elementos: 10, 20, 30, 40, 50");
        pila.push(10);
        pila.push(20);
        pila.push(30);
        pila.push(40);
        pila.push(50);

        log.info(" Estado pila: {}", pila);
        log.info(" Tamaño actual: {}", pila.size());
        log.info(" Elemento en el tope: {}", pila.top());

        // Pop de elementos
        log.info("\n OPERACIÓN: POP (Extraer elementos)");
        int elemento1 = pila.pop();
        int elemento2 = pila.pop();

        log.info(" Extraído: {} (último en entrar, primero en salir)", elemento1);
        log.info(" Extraído: {}", elemento2);
        log.info(" Nuevo tope: {}", pila.top());
        log.info(" Tamaño actual: {}", pila.size());
        log.info(" ¿Está vacía?: {}", pila.isEmpty());

    }
    /**
     * Mide el rendimiento del sistema con múltiples validaciones
     */
    private void medirRendimiento(CoberturaServicio coberturaServicio) {
        log.info("\n");
        log.info("Prueba de Rendimiento");

        int numPruebas = 100000;

        // Petición válida y completa para pruebas
        PeticionCliente peticionPrueba = new PeticionCliente(
                1,      // Provincia
                1,      // Municipio
                1701,   // Unidad poblacional
                1001,   // Vía
                10      // Número
        );

        log.info("\nRealizando {} validaciones de una dirección completa...", numPruebas);
        log.info("Petición de prueba: {}", peticionPrueba.getClave());

        long inicioTotal = System.nanoTime();

        for (int i = 0; i < numPruebas; i++) {
            coberturaServicio.damosServicio(peticionPrueba);
        }

        long tiempoTotal = System.nanoTime() - inicioTotal;
        double tiempoPromedioNs = tiempoTotal / (double) numPruebas;
        double tiempoPromedioUs = tiempoPromedioNs / 1000.0;
        double consultasPorSegundo = 1_000_000_000.0 / tiempoPromedioNs;

        log.info("\n RESULTADOS ");
        log.info(" Tiempo total: {} ms", tiempoTotal / 1_000_000);
        log.info(" Tiempo promedio: {} ns ({} μs)",
                String.format("%.2f", tiempoPromedioNs),
                String.format("%.3f", tiempoPromedioUs));
        log.info(" Capacidad estimada: {} consultas/segundo",
                String.format("%.0f", consultasPorSegundo));

    }
}