package com.asqi.scholia_kantin_be.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Data orang tua / wali siswa (ERD v2, tabel {@code orang_tua}).
 *
 * <p>Diminta eksplisit oleh sekolah: siswa harus terhubung ke data orang tua
 * beserta <b>namanya</b>, supaya layar TU/kasir bisa menampilkan wali yang bisa
 * dihubungi saat kartu hilang atau saldo mencurigakan. Satu siswa boleh punya
 * lebih dari satu wali (mis. ayah &amp; ibu) — karena itu tabel terpisah, bukan
 * kolom di {@code siswa}.
 *
 * <p>Tenant-scoped lewat {@code sekolah_id} (PRD §11.4), sama seperti
 * {@link Siswa}.
 */
@Entity
@Table(name = "orang_tua")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrangTua {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "sekolah_id", nullable = false)
    private Long sekolahId;

    @Column(name = "siswa_id", nullable = false)
    private Long siswaId;

    @Column(name = "nama", nullable = false)
    private String nama;

    @Column(name = "telepon")
    private String telepon;

    /** {@code AYAH} / {@code IBU} / {@code WALI}. */
    @Column(name = "hubungan", nullable = false)
    private String hubungan;

    @Column(name = "punya_aplikasi", nullable = false)
    private boolean punyaAplikasi;

    @Column(name = "aktif", nullable = false)
    private boolean aktif;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
