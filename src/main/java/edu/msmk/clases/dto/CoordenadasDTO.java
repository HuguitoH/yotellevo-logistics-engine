package edu.msmk.clases.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CoordenadasDTO {
    private double latitud;
    private double longitud;
}
