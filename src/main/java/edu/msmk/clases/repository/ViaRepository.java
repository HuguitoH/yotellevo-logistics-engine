package edu.msmk.clases.repository;


import edu.msmk.clases.repository.Via; // Asegúrate de que la ruta a tu entidad Via sea correcta
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ViaRepository extends JpaRepository<Via, Long> {

    // Este método buscará en MySQL por provincia, municipio y nombre de calle
    Optional<Via> findFirstByCproAndCmumAndNombre(Integer cpro, Integer cmum, String nombre);
}