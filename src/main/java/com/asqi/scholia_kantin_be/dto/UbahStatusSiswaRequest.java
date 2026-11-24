package com.asqi.scholia_kantin_be.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request aktivasi/nonaktivasi siswa sementara.
 */
@Data
public class UbahStatusSiswaRequest {

    @NotNull(message = "Status aktif wajib diisi")
    private Boolean aktif;
}
