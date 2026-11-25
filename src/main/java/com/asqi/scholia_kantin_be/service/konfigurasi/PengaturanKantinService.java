package com.asqi.scholia_kantin_be.service.konfigurasi;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.component.logging.AuditLogger;
import com.asqi.scholia_kantin_be.dto.konfigurasi.PengaturanKantinRequest;
import com.asqi.scholia_kantin_be.dto.konfigurasi.PengaturanKantinResponse;
import com.asqi.scholia_kantin_be.enums.KebijakanSaldoMengendap;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.model.PengaturanKantin;
import com.asqi.scholia_kantin_be.model.Pengguna;
import com.asqi.scholia_kantin_be.repository.konfigurasi.PengaturanKantinRepository;
import com.asqi.scholia_kantin_be.repository.auth.PenggunaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Locale;

/**
 * Pengaturan kantin per sekolah (PRD §9.1, issue <b>#42</b>).
 *
 * <p>Menyimpan nama kantin, jam tutup kasir otomatis (dipakai penjadwal
 * auto-close §6.4), langkah konfirmasi manual, durasi foto, min/maks top-up,
 * serta batas saldo maksimum per siswa &amp; per Kartu Tamu.
 *
 * <p><b>Default aman:</b> bila sekolah belum pernah menyimpan, {@link #ambil}
 * mengembalikan default (tanpa menyimpan) — jam tutup 23:59, konfirmasi manual
 * nonaktif, durasi foto 3 detik (PRD §6.1/§6.4). Perubahan dicatat di audit
 * (§11.7).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PengaturanKantinService {

    /** Default jam tutup kasir otomatis (PRD §6.4). */
    public static final LocalTime DEFAULT_JAM_TUTUP = LocalTime.of(23, 59);
    /** Default durasi tampil foto setelah transaksi (PRD §6.1). */
    public static final int DEFAULT_DURASI_FOTO_DETIK = 3;

    /** Default kebijakan saldo mengendap: kembalikan ke ortu (PRD §9.3). */
    public static final String KEBIJAKAN_DEFAULT = "REFUND";

    /** Default ambang hari tindak lanjut saldo mengendap. */
    public static final int AMBANG_HARI_DEFAULT = 90;

    private final PengaturanKantinRepository repo;
    private final PenggunaRepository penggunaRepo;
    private final JamKantin jam;
    private final AuditLogger auditLogger;

    /**
     * Ambil pengaturan kantin; bila belum ada, kembalikan default aman
     * (tanpa menyimpan) — sekolah tidak wajib mengisi apa pun.
     */
    @Transactional(readOnly = true)
    public PengaturanKantinResponse ambil(Long sekolahId) {
        return repo.findById(sekolahId)
                .map(baris -> proyeksi(baris, sekolahId))
                .orElseGet(() -> PengaturanKantinResponse.builder()
                        .sekolahId(sekolahId)
                        .namaKantin(null)
                        .jamTutupOtomatis(DEFAULT_JAM_TUTUP)
                        .konfirmasiManual(false)
                        .durasiFotoDetik(DEFAULT_DURASI_FOTO_DETIK)
                        // Audit #401: endpoint demo GET /api/konfigurasi/kebijakan dihapus;
                        // default kebijakan saldo mengendap kini dilayani di sini agar
                        // sekolah yang belum mengisi tetap mendapat nilai aman (Q16).
                        .kebijakanSaldoMengendap(KEBIJAKAN_DEFAULT)
                        .ambangHari(AMBANG_HARI_DEFAULT)
                        .disimpan(false)
                        .build());
    }

    /**
     * Jam tutup kasir otomatis efektif untuk sekolah (default 23:59 bila belum
     * diatur). Dipakai penjadwal auto-tutup (PRD §6.4).
     */
    @Transactional(readOnly = true)
    public LocalTime jamTutupOtomatis(Long sekolahId) {
        return repo.findById(sekolahId)
                .map(PengaturanKantin::getJamTutupOtomatis)
                .orElse(DEFAULT_JAM_TUTUP);
    }

    /**
     * Ubah pengaturan kantin (upsert). Field {@code null} = pertahankan nilai
     * lama. Perubahan dicatat di audit (PRD §11.7).
     */
    @Transactional
    public PengaturanKantinResponse ubah(Long sekolahId, PengaturanKantinRequest request, Long aktorId) {
        OffsetDateTime now = jam.sekarang();

        PengaturanKantin baris = repo.findById(sekolahId)
                .orElseGet(() -> PengaturanKantin.builder()
                        .sekolahId(sekolahId)
                        .jamTutupOtomatis(DEFAULT_JAM_TUTUP)
                        .konfirmasiManual(false)
                        .durasiFotoDetik(DEFAULT_DURASI_FOTO_DETIK)
                        .kebijakanSaldoMengendap(KEBIJAKAN_DEFAULT)
                        .ambangHari(AMBANG_HARI_DEFAULT)
                        .createdAt(now)
                        .build());

        Long minTopup = request.getMinTopup() != null ? request.getMinTopup() : baris.getMinTopup();
        Long maksTopup = request.getMaksTopup() != null ? request.getMaksTopup() : baris.getMaksTopup();
        Long batasSaldoSiswa = request.getBatasSaldoSiswa() != null
                ? request.getBatasSaldoSiswa()
                : baris.getBatasSaldoSiswa();
        validasiBatasTopup(minTopup, maksTopup, batasSaldoSiswa);

        String lama = ringkas(baris);
        if (request.getNamaKantin() != null) {
            baris.setNamaKantin(request.getNamaKantin());
        }
        if (request.getJamTutupOtomatis() != null) {
            baris.setJamTutupOtomatis(request.getJamTutupOtomatis());
        }
        if (request.getKonfirmasiManual() != null) {
            baris.setKonfirmasiManual(request.getKonfirmasiManual());
        }
        if (request.getDurasiFotoDetik() != null) {
            baris.setDurasiFotoDetik(request.getDurasiFotoDetik());
        }
        baris.setMinTopup(minTopup);
        baris.setMaksTopup(maksTopup);
        baris.setBatasSaldoSiswa(batasSaldoSiswa);
        if (request.getKebijakanSaldoMengendap() != null) {
            baris.setKebijakanSaldoMengendap(validasiKebijakan(request.getKebijakanSaldoMengendap()));
        }
        if (request.getAmbangHari() != null) {
            baris.setAmbangHari(Math.max(0, request.getAmbangHari()));
        }
        baris.setDiperbaruiOleh(aktorId);
        baris.setUpdatedAt(now);
        repo.save(baris);

        auditLogger.catat(aktorId, sekolahId, "UBAH_PENGATURAN_KANTIN", "PengaturanKantin",
                String.valueOf(sekolahId), "Ubah pengaturan kantin", lama, ringkas(baris));
        log.info("Pengaturan kantin sekolah={} diperbarui oleh={}", sekolahId, aktorId);
        return proyeksi(baris, sekolahId);
    }

    /**
     * Proyeksi baris + nama aktor pengubah (issue #416).
     *
     * <p>Lookup dibatasi {@code sekolah_id} lewat
     * {@link PenggunaRepository#findByIdAndSekolahId}: ID pengguna sekolah lain
     * tidak boleh muncul sebagai nama, dan aktor yang sudah dihapus tidak
     * membuat endpoint gagal — namanya cukup {@code null}.
     */
    private PengaturanKantinResponse proyeksi(PengaturanKantin baris, Long sekolahId) {
        return PengaturanKantinResponse.dari(baris, namaAktor(sekolahId, baris.getDiperbaruiOleh()));
    }

    /** Nama tampilan aktor, atau {@code null} bila tidak ada di sekolah ini. */
    private String namaAktor(Long sekolahId, Long aktorId) {
        if (aktorId == null) {
            return null;
        }
        return penggunaRepo.findByIdAndSekolahId(aktorId, sekolahId)
                .map(Pengguna::getNama)
                .orElse(null);
    }

    /**
     * Validasi silang batas top-up dengan plafon saldo siswa (issue #414).
     *
     * <p>Frontend sudah menolak kombinasi ini, tetapi klien dapat memanggil
     * {@code PUT /api/pengaturan-kantin} langsung. Tanpa pemeriksaan di server,
     * {@code maksTopup} yang melebihi {@code batasSaldoSiswa} akan tersimpan dan
     * top-up berikutnya dapat mendorong saldo siswa melewati plafon
     * (OWASP A06:2025 — Insecure Design, CWE-20).
     *
     * <p>Aturan hanya berlaku bila <b>kedua</b> nilai terisi; {@code null} berarti
     * "tanpa batas" sehingga tidak ada yang perlu diperiksa:
     * <ol>
     *   <li>{@code minTopup <= maksTopup}</li>
     *   <li>{@code batasSaldoSiswa >= maksTopup}</li>
     *   <li>{@code batasSaldoSiswa >= minTopup}</li>
     * </ol>
     *
     * <p>Nilai yang divalidasi adalah nilai <b>efektif</b> (gabungan request dan
     * baris tersimpan), sehingga permintaan yang hanya mengirim {@code maksTopup}
     * tetap diperiksa terhadap plafon yang sudah tersimpan.
     *
     * @throws InvalidOperationException (HTTP 400) bila salah satu aturan dilanggar
     */
    private static void validasiBatasTopup(Long minTopup, Long maksTopup, Long batasSaldoSiswa) {
        if (minTopup != null && maksTopup != null && minTopup > maksTopup) {
            throw new InvalidOperationException(
                    "Minimum top-up tidak boleh melebihi maksimum top-up");
        }
        if (batasSaldoSiswa != null && maksTopup != null && batasSaldoSiswa < maksTopup) {
            throw new InvalidOperationException(
                    "Batas saldo siswa tidak boleh lebih kecil dari maksimum top-up");
        }
        if (batasSaldoSiswa != null && minTopup != null && batasSaldoSiswa < minTopup) {
            throw new InvalidOperationException(
                    "Batas saldo siswa tidak boleh lebih kecil dari minimum top-up");
        }
    }

    /**
     * Validasi nilai kebijakan saldo mengendap (Q16).
     *
     * <p>Dulu validasi ini hanya ada di {@code PUT /api/konfigurasi/kebijakan}
     * yang menerima enum bertipe; endpoint itu dihapus pada audit #401 sehingga
     * nilainya kini masuk lewat {@link PengaturanKantinRequest} berupa teks —
     * tanpa pemeriksaan di sini, nilai ngawur akan tersimpan dan memecah
     * pembacaan kebijakan di kemudian hari.
     *
     * @throws InvalidOperationException (HTTP 400) bila nilai di luar enum
     */
    private static String validasiKebijakan(String nilai) {
        String kandidat = nilai.trim().toUpperCase(Locale.ROOT);
        try {
            return KebijakanSaldoMengendap.valueOf(kandidat).name();
        } catch (IllegalArgumentException e) {
            throw new InvalidOperationException("Kebijakan saldo mengendap tidak dikenal: " + nilai
                    + ". Pilihan: " + Arrays.toString(KebijakanSaldoMengendap.values()));
        }
    }

    /** Ringkasan pengaturan untuk audit (nilai lama → baru). */
    private String ringkas(PengaturanKantin c) {
        return "nama=" + c.getNamaKantin()
                + ";jamTutup=" + c.getJamTutupOtomatis()
                + ";konfirmasiManual=" + c.isKonfirmasiManual()
                + ";durasiFoto=" + c.getDurasiFotoDetik()
                + ";minTopup=" + c.getMinTopup()
                + ";maksTopup=" + c.getMaksTopup()
                + ";batasSaldoSiswa=" + c.getBatasSaldoSiswa()
                + ";kebijakanSaldoMengendap=" + c.getKebijakanSaldoMengendap()
                + ";ambangHari=" + c.getAmbangHari();
    }
}
