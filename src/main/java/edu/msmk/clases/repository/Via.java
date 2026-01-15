package edu.msmk.clases.repository;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "vias", indexes = {
        @Index(name = "idx_cobertura", columnList = "cpro, cmum, nombre")
})
@Data
public class Via {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer cpro;
    private Integer cmum;
    private String nombre;
    private String cp;
}