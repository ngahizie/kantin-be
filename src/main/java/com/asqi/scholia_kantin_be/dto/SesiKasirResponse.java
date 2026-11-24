package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.enums.StatusSesiKasir;
import com.asqi.scholia_kantin_be.model.SesiKasir;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Detail satu sesi kasir untuk response {@code tutup}/{@code detail} (issue #409).
 *
 * <p>Mirror seluruh field yang sebelumnya terkirim ketika controller
 * mengembalikan entity {@link SesiKasir} langsung, sehingga kontrak JSON tidak
 * berubah. Bedakan dari {@link SesiKasirAktifData} yang dipakai layar POS untuk
 * sesi masih TERBUKA dan punya field turunan (nama titik, jumlah transaksi).
 *
 * <p>Entity JPA tidak boleh keluar dari layer service: skema DB boleh berubah
 * tanpa mengubah API bila proyeksi berhenti di DTO ini.
 */
public record SesiKasirResponse(
        Long id,
        Long sekolahId,
        Long titikKasirId,
        LocalDate tanggal,
        StatusSesiKasir status,
        Long totalBruto,
        Long totalVoid,
        Long totalBersih,
        OffsetDateTime dibukaAt,
        Long dibukaOleh,
        OffsetDateTime ditutupAt,
        Long ditutupOleh,
        Long uangFisik,
        Long selisih,
        String catatan,
        boolean autoTutup,
        boolean postingBukuKas,
        String referensiBukuKas,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    /**
     * Proyeksikan entity sesi ke bentuk response.
     *
     * @param sesi sesi dari service; tidak boleh {@code null}
     * @return DTO dengan nilai field identik dengan entity
     */
    public static SesiKasirResponse dari(SesiKasir sesi) {
        return new SesiKasirResponse(
                sesi.getId(),
                sesi.getSekolahId(),
                sesi.getTitikKasirId(),
                sesi.getTanggal(),
                sesi.getStatus(),
                sesi.getTotalBruto(),
                sesi.getTotalVoid(),
                sesi.getTotalBersih(),
                sesi.getDibukaAt(),
                sesi.getDibukaOleh(),
                sesi.getDitutupAt(),
                sesi.getDitutupOleh(),
                sesi.getUangFisik(),
                sesi.getSelisih(),
                sesi.getCatatan(),
                sesi.isAutoTutup(),
                sesi.isPostingBukuKas(),
                sesi.getReferensiBukuKas(),
                sesi.getCreatedAt(),
                sesi.getUpdatedAt());
    }
}
