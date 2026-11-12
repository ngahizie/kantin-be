package com.asqi.scholia_kantin_be.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Master siswa milik kantin (ERD v2, tabel {@code siswa}).
 *
 * <p>Pada skema v1 data siswa hanya ada di admin-be dan kantin-be menyimpan
 * {@code dev_siswa_rfid}. ERD v2 menaikkan siswa menjadi tabel nyata supaya
 * identitas (NIS, nama, kelas, foto) bisa dibaca lokal tanpa integrasi —
 * dipakai laporan riwayat, kandidat refund, dan layar kasir.
 *
 * <p>ID <b>sama</b> dengan ID subjek ledger ({@code saldo.siswa_id},
 * {@code mutasi_saldo.siswa_id}) — jadi satu siswa = satu baris di sini.
 * Relasi ke wali ada di {@link OrangTua}; kartu RFID di {@link Kartu}.
 */
@Entity
@Table(name = "siswa")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Siswa {

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "sekolah_id", nullable = false)
    private Long sekolahId;

    @Column(name = "nis", nullable = false)
    private String nis;

    @Column(name = "nisn")
    private String nisn;

    @Column(name = "nama", nullable = false)
    private String nama;

    @Column(name = "kelas")
    private String kelas;

    @Column(name = "foto_url")
    private String fotoUrl;

    @Column(name = "aktif", nullable = false)
    private boolean aktif;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
