package edu.msmk.clases.service;

import edu.msmk.clases.dto.GraphDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GrafoServiceImpl implements GrafoService {

    @Autowired
    private PedidosService pedidosService; // <--- Conexión con el servicio que tiene los datos

    @Override
    public GraphDTO obtenerGrafoActual() {
        // En lugar de crear listas vacías, llamamos al método que creamos en PedidosService
        return pedidosService.obtenerUltimoGrafo();
    }
}