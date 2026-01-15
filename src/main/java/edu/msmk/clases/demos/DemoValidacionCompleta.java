package edu.msmk.clases.demos;

import edu.msmk.clases.service.CoberturaServicio;
import edu.msmk.clases.exchange.PeticionCliente;
import java.lang.reflect.Field;
import java.util.HashMap;

public class DemoValidacionCompleta {

    public static void main(String[] args) {
        System.out.println("=== DEMO VALIDACIÓN 2026: CLAVES ANCHO FIJO ===");

        CoberturaServicio cobertura = new CoberturaServicio();

        // Simulación manual de lo que haría Spring con el @Value
        // solo para que la demo no de NullPointerException
        inyectarMaestroProvinciasManual(cobertura);

        System.out.println("\n[1] CARGANDO DATOS...");

        // CPRO, CMUM, CUN, CVIA, EIN, ESN, TINUM, CP
        cobertura.addTramo(1, 1, 1701, 1001, 1, 27, 0, "01240");
        cobertura.addTramo(28, 79, 0, 12345, 1, 100, 0, "28001");
        cobertura.addTramo(8, 19, 1901, 54321, 2, 200, 2, "08002"); // Solo pares

        System.out.println("Tramos: " + cobertura.numeroTramosCubiertos());
        System.out.println("Provincias: " + cobertura.numeroProvinciasCubiertas());

        // CASO 1: ÉXITO
        System.out.println("\nCASO 1: Madrid - Castellana 15");
        PeticionCliente p1 = new PeticionCliente(28, 79, 0, 12345, 15);
        if (cobertura.damosServicio(p1)) {
            System.out.println("OK. CP: " + p1.getCodigoPostalOficial());
            System.out.println("Clave: " + p1.getClave());
        }

        // CASO 2: PARIDAD
        System.out.println("\nCASO 2: Barcelona - Calle Pares, nº 15 (Impar)");
        PeticionCliente p2 = new PeticionCliente(8, 19, 1901, 54321, 15);
        boolean res2 = cobertura.damosServicio(p2);
        System.out.println(res2 ? "Error" : " Rechazado correctamente (es par)");

        // CASO 3: RENDIMIENTO
        System.out.println("\nCASO 3: Test de velocidad (1 millón de peticiones)");
        long inicio = System.currentTimeMillis();
        for(int i=0; i<1_000_000; i++) {
            cobertura.damosServicio(p1);
        }
        System.out.println("Tiempo: " + (System.currentTimeMillis() - inicio) + "ms");
    }

    /**
     * Auxiliar para la demo: Inyecta datos en el campo privado maestroProvincias
     * ya que en un main() Spring no está activo.
     */
    private static void inyectarMaestroProvinciasManual(CoberturaServicio service) {
        try {
            Field field = CoberturaServicio.class.getDeclaredField("maestroProvincias");
            field.setAccessible(true);
            field.set(service, new HashMap<Integer, String>() {{
                put(28, "MADRID");
                put(1, "ALAVA");
                put(8, "BARCELONA");
            }});
        } catch (Exception e) {
            // Si falla no pasa nada, la demo usa addTramo directo
        }
    }
}