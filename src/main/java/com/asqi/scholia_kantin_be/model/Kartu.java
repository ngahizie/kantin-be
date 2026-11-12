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
 * Kartu RFID siswa (ERD v2, tabel {@code kartu}).
 *
 * <p>Primary key adalah {@code uid} (string yang dibaca reader), bukan id
 * numerik: tap hanya membawa UID, jadi lookup kasir langsung ke PK tanpa
 * indeks tambahan. Satu siswa boleh punya banyak kartu (kartu lama tetap
 * tersimpan dengan {@code aktif=false}) tetapi hanya <b>satu</b> yang aktif —
 * dijaga partial unique index {@code ux_kartu_aktif_per_siswa}.
 *
 * <p>Kartu tamu sudah dihapus dari skema v2; semua kartu terikat ke
 * {@code siswa_id}.
 */
@Entity
@Table(name = "kartu")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Kartu {

    @Id
    @Column(name = "uid", nullable = false)
    private String uid;

    @Column(name = "sekolah_id", nullable = false)
    private Long sekolahId;

    @Column(name = "siswa_id", nullable = false)
    private Long siswaId;

    @Column(name = "nomor_kartu", nullable = false)
    private String nomorKartu;

    @Column(name = "aktif", nullable = false)
    private boolean aktif;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
