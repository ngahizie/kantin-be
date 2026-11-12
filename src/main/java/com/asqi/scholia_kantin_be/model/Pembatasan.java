package com.asqi.scholia_kantin_be.model;

import com.asqi.scholia_kantin_be.enums.JenisPembatasan;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Pembatasan siswa — penggabungan {@code blokir_kartu}, {@code blokir_item}, dan
 * {@code limit_harian} menjadi satu tabel pada ERD v2 (PRD §6.1 tahap 2/3/5, §8.3).
 *
 * <p>Dibedakan lewat kolom {@link JenisPembatasan jenis}:
 * <ul>
 *   <li>{@code BLOKIR_SISWA} — tap ditolak (kartu hilang/diblokir ortu).</li>
 *   <li>{@code BLOKIR_ITEM} — satu {@code menu_id} atau satu {@code kategori_id} diblokir.</li>
 *   <li>{@code LIMIT_HARIAN} — batas belanja per hari ({@code nominal} wajib).</li>
 * </ul>
 *
 * <p><b>Berlaku instan / tanpa cache (PRD §11.11):</b> baris ini dibaca server pada
 * <b>setiap</b> tap, jadi pembatasan yang baru disimpan langsung ditolak.
 *
 * <p>{@code aktif=false} dipakai untuk "membuka" pembatasan tanpa menghapus riwayat;
 * untuk {@code LIMIT_HARIAN} "tanpa limit" berarti baris dihapus (CHECK mewajibkan
 * {@code nominal} terisi).
 */
@Entity
@Table(name = "pembatasan")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pembatasan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "sekolah_id", nullable = false)
    private Long sekolahId;

    @Column(name = "siswa_id", nullable = false)
    private Long siswaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "jenis", nullable = false, length = 20)
    private JenisPembatasan jenis;

    /** Menu yang diblokir (khusus {@code BLOKIR_ITEM}). */
    @Column(name = "menu_id")
    private Long menuId;

    /** Kategori yang diblokir (khusus {@code BLOKIR_ITEM}). */
    @Column(name = "kategori_id")
    private Long kategoriId;

    /** Batas belanja harian (khusus {@code LIMIT_HARIAN}). */
    @Column(name = "nominal")
    private Long nominal;

    @Column(name = "alasan", length = 500)
    private String alasan;

    @Column(name = "aktif", nullable = false)
    private boolean aktif;

    @Column(name = "diubah_oleh")
    private Long diubahOleh;

    @Column(name = "waktu", nullable = false)
    private OffsetDateTime waktu;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
