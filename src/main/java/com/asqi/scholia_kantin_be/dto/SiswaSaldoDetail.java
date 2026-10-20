package com.asqi.scholia_kantin_be.dto;

import java.time.OffsetDateTime;

/** Detail saldo mengendap per siswa untuk kontrak FE laporan (issue #224). */
public record SiswaSaldoDetail(
        Long siswaId,
        String nis,
        String nisn,
        String nama,
        String kelas,
        long saldo,
        long belanjaHariIni,
        Long limitHarian,
        boolean isBlocked,
        String parentName,
        String parentPhone,
        OffsetDateTime terakhirTransaksi) {
}
