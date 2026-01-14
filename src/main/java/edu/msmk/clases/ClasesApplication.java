package edu.msmk.clases;

import edu.msmk.clases.model.Direccion;
import edu.msmk.clases.service.CoberturaServicio;
import edu.msmk.clases.service.PilaBasica;
import edu.msmk.clases.exchange.PeticionCliente;
import edu.msmk.clases.model.Paquete;
import edu.msmk.clases.model.Punto;
import edu.msmk.clases.routing.GrafoEntregas;
import edu.msmk.clases.routing.OptimizadorRutas;
import edu.msmk.clases.routing.VisualizadorGrafos;
import edu.msmk.clases.service.SimuladorClientes;
import edu.msmk.clases.service.TramoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@SpringBootApplication
public class ClasesApplication implements CommandLineRunner {

    private final TramoService tramoService;
    private final SimuladorClientes simuladorClientes;
    private final CoberturaServicio coberturaServicio;


    public ClasesApplication(TramoService tramoService, SimuladorClientes simuladorClientes,CoberturaServicio coberturaServicio) {
        this.tramoService = tramoService;
        this.simuladorClientes = simuladorClientes;
        this.coberturaServicio = coberturaServicio;
    }

    public static void main(String[] args) {
        SpringApplication.run(ClasesApplication.class, args);
    }

    @Override
    public void run(String... args) {
        log.info("YoTeLoLlevo - Sistema de Entregas 2026");

        try {
            // 1. CARGAR COBERTURA (Usamos la instancia inyectada 'this.coberturaServicio')
            log.info("\n1. Cargando cobertura de tramos desde BOE...");
            long inicioGlobal = System.currentTimeMillis();

            // IMPORTANTE: Asegúrate de que leerTramos acepte el objeto por parámetro y no devuelva uno nuevo
            tramoService.leerTramos(this.coberturaServicio);

            long tiempoCarga = System.currentTimeMillis() - inicioGlobal;
            log.info("Cobertura cargada correctamente en {} ms", tiempoCarga);
            log.info(" Provincias cubiertas: {}", this.coberturaServicio.numeroProvinciasCubiertas());
            log.info(" Tramos cubiertos: {}", this.coberturaServicio.numeroTramosCubiertos());

            // 2. DEMOS (Pasamos siempre la instancia inyectada 'this.coberturaServicio')
            log.info("\n2. Demostración de validación de direcciones:");
            demoPeticiones(this.coberturaServicio);

            log.info("\n3. Demostración de Pila Básica:");
            demoPilaBasica();

            log.info("\n4. Demostración de Pila de Paquetes:");
            demoPilaPaquetes();

            log.info("\n5. Midiendo rendimiento:");
            medirRendimiento(this.coberturaServicio);

            log.info("\n6. Simulación concurrente:");
            demoSimulacionConcurrente(this.coberturaServicio);

            log.info("\n7. Optimización de rutas:");
            demoOptimizacionRutas();

            log.info("\nSistema inicializado correctamente. API lista para recibir pedidos.");

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
        Integer elemento1 = (Integer) pila.pop();
        Integer elemento2 = (Integer) pila.pop();

        log.info(" Extraído: {} (último en entrar, primero en salir)", elemento1);
        log.info(" Extraído: {}", elemento2);
        log.info(" Nuevo tope: {}", pila.top());
        log.info(" Tamaño actual: {}", pila.size());
        log.info(" ¿Está vacía?: {}", pila.isEmpty());

    }

    private void demoPilaPaquetes() {
        log.info("\n=== DEMO: Pila de Paquetes (Caso Real 2026) ===");

        // Asumimos que PilaBasica está correctamente implementada
        edu.msmk.clases.service.PilaBasica<Paquete> furgoneta = new edu.msmk.clases.service.PilaBasica<>(50);

        // 1. Crear Direcciones (Usando tu nueva clase de modelo edu.msmk.clases.model.Direccion)
        edu.msmk.clases.model.Direccion dir1 = new edu.msmk.clases.model.Direccion(
                "01", "ALEGRIA-DULANTZI", "CALLE", "TORRONDOA", "15", "01001", null, null, null);

        edu.msmk.clases.model.Direccion dir2 = new edu.msmk.clases.model.Direccion(
                "01", "ALEGRIA-DULANTZI", "AVENIDA", "AÑUA BIDEA", "8", "01002", null, null, null);

        // 2. Crear Coordenadas (Puntos)
        Punto coord1 = new Punto(42.8467, -2.5123, "Punto 1");
        Punto coord2 = new Punto(42.8480, -2.5150, "Punto 2");

        // 3. Crear paquetes respetando el nuevo constructor:
        // (id, destinatarioString, direccionModelo, coordenadas, peso, prioridad)
        Paquete p1 = new Paquete(
                "PKG-001",
                "Juan Pérez",
                dir1,
                coord1,
                5.0,
                2
        );

        Paquete p2 = new Paquete(
                "PKG-002",
                "María López",
                dir2,
                coord2,
                3.8,
                1
        );

        // 4. Cargar furgoneta
        log.info("Cargando furgoneta...");
        furgoneta.push(p1);
        furgoneta.push(p2);

        log.info("Paquetes cargados: {}", furgoneta.size());

        // 5. Descargar (LIFO)
        log.info("\nDescargando paquetes:");
        while (!furgoneta.isEmpty()) {
            Paquete p = furgoneta.pop();
            log.info("→ {}", p);
        }
    }


    /**
     * Demuestra la capacidad del sistema para manejar carga concurrente
     */

    private void demoSimulacionConcurrente(CoberturaServicio coberturaServicio) {
        // PRUEBA LIGERA (comentar después)
        // simuladorClientes.simularCargaConcurrente(
        //     coberturaServicio,
        //     10,      // 10 clientes
        //     1000     // 1,000 peticiones = 10,000 total
        // );

        // PRUEBA EXTREMA
        simuladorClientes.simularCargaConcurrente(
                coberturaServicio,
                100,     // 100 clientes simultáneos
                10000    // 10,000 peticiones cada uno = 1,000,000 total
        );
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

    /**
     * Demuestra el sistema de optimización de rutas con todos los algoritmos
     */
    private void demoOptimizacionRutas() {
        log.info("\n");
        log.info("DEMO: Optimizacion de Rutas (Comparativa de Algoritmos)");

        // Crear almacén
        Punto almacen = new Punto(40.4168, -3.7038, "Almacen Central Madrid");

        // Crear paquetes
        List<Paquete> paquetes = new ArrayList<>();
        paquetes.add(crearPaqueteDemo("PKG-001", "Juan Pérez", 40.4200, -3.7050, 2.5, 1));
        paquetes.add(crearPaqueteDemo("PKG-002", "María López", 40.4400, -3.6900, 1.8, 2));
        paquetes.add(crearPaqueteDemo("PKG-003", "Carlos Ruiz", 40.3900, -3.7200, 3.2, 1));
        paquetes.add(crearPaqueteDemo("PKG-004", "Ana García", 40.4500, -3.7100, 2.0, 2));
        paquetes.add(crearPaqueteDemo("PKG-005", "Pedro Martín", 40.4100, -3.6800, 1.5, 3));
        paquetes.add(crearPaqueteDemo("PKG-006", "Laura Sánchez", 40.4300, -3.7300, 2.8, 2));
        paquetes.add(crearPaqueteDemo("PKG-007", "Diego Torres", 40.3800, -3.7100, 1.9, 1));

        log.info("Paquetes a entregar: {}", paquetes.size());

        // Crear grafo
        GrafoEntregas grafo = new GrafoEntregas(almacen, paquetes);

        // Optimizar con diferentes algoritmos
        OptimizadorRutas optimizador = new OptimizadorRutas();

        log.info("\n--- Ruta sin optimizar ---");
        OptimizadorRutas.ResultadoOptimizacion rutaOriginal =
                optimizador.rutaSinOptimizar(grafo);

        log.info("\n--- Nearest Neighbor ---");
        OptimizadorRutas.ResultadoOptimizacion rutaNN =
                optimizador.optimizarNearestNeighbor(grafo);

        log.info("\n--- 2-opt (mejora de NN) ---");
        OptimizadorRutas.ResultadoOptimizacion ruta2Opt =
                optimizador.optimizar2Opt(grafo, rutaNN);

        // Comparar resultados
        log.info("\n");
        log.info("COMPARATIVA:");
        log.info("  Sin optimizar: {} km",
                String.format("%.2f", rutaOriginal.getDistanciaTotal()));
        log.info("  Nearest Neighbor: {} km (ahorro: {} %)",
                String.format("%.2f", rutaNN.getDistanciaTotal()),
                String.format("%.1f",
                        ((rutaOriginal.getDistanciaTotal() - rutaNN.getDistanciaTotal()) /
                                rutaOriginal.getDistanciaTotal()) * 100));
        log.info("  2-opt: {} km (ahorro total: {} %)",
                String.format("%.2f", ruta2Opt.getDistanciaTotal()),
                String.format("%.1f",
                        ((rutaOriginal.getDistanciaTotal() - ruta2Opt.getDistanciaTotal()) /
                                rutaOriginal.getDistanciaTotal()) * 100));

        // VISUALIZAR GRAFO
        log.info("\nGenerando visualizacion del grafo...");

        try {
            VisualizadorGrafos visualizador = new VisualizadorGrafos();
            visualizador.mostrarRutaOptimizada(grafo, ruta2Opt);

            log.info("Ventana grafica mostrada.");
            log.info("IMPORTANTE: Cierra la ventana del grafo manualmente para que el programa continue.");

            // Esperar a que el usuario cierre la ventana
            Thread.sleep(2000);

        } catch (Exception e) {
            log.error("No se pudo mostrar ventana grafica: {}", e.getMessage());
        }
    }

    private Paquete crearPaqueteDemo(String id, String nombre, double lat, double lon, double peso, int prioridad) {
        // 1. Creamos las coordenadas
        Punto coordenadas = new Punto(lat, lon, "Destino " + id);

        // 2. Creamos una dirección ficticia para cumplir con el constructor
        Direccion dir = new Direccion();
        dir.setNombreVia("Calle Demo " + id);
        dir.setMunicipio("Madrid");
        dir.setProvincia("Madrid");
        dir.setNumero("1");

        // 3. Retornamos el paquete real (asegúrate de que el orden de parámetros coincida con tu clase Paquete)
        return new Paquete(
                id,
                nombre,
                dir,
                coordenadas,
                peso,
                prioridad
        );
    }

}