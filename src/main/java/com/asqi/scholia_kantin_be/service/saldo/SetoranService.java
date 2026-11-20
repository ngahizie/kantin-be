package com.asqi.scholia_kantin_be.service.saldo;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import com.asqi.scholia_kantin_be.component.logging.AuditLogger;
import com.asqi.scholia_kantin_be.dto.RekapSetoranItem;
import com.asqi.scholia_kantin_be.dto.SetoranResponse;
import com.asqi.scholia_kantin_be.enums.ArahMutasi;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.helper.IdGenerator;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.model.Pengguna;
import com.asqi.scholia_kantin_be.model.Setoran;
import com.asqi.scholia_kantin_be.repository.MutasiSaldoRepository;
import com.asqi.scholia_kantin_be.repository.PenggunaRepository;
import com.asqi.scholia_kantin_be.repository.SetoranRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Setoran kas TU harian (PRD §9.2, issue #39).
 *
 * <p><b>Alur:</b> rekap top-up tunai <b>per petugas per hari</b> dari
 * {@code saldo_ledger} (jenis {@code TOPUP_TUNAI}) → bendahara mengonfirmasi
 * uang fisik yang disetor. <b>Selisih kas dicatat, tidak dihapus</b>
 * (append-only): selisih dihitung kolom generated
 * {@code total_topup − jumlah_disetor}, tidak pernah mengubah baris lama.
 *
 * <p>Idempoten lewat {@code referensiId} (nomor berita acara) yang UNIQUE per
 * sekolah — double-submit tidak mencatat setoran dua kali. Semua aksi diaudit
 * (PRD §11.7).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SetoranService {

    private final MutasiSaldoRepository ledgerRepo;
    private final SetoranRepository setoranRepo;
    private final PenggunaRepository penggunaRepo;
    private final AuditLogger auditLogger;
    private final IdGenerator idGenerator;
    private final JamKantin jam;

    /**
     * Rekap setoran TU pada satu tanggal: per petugas, total top-up tunai,
     * uang yang sudah dikonfirmasi disetor, dan selisihnya (PRD §9.2).
     *
     * <p>Baca-saja; tenant-scoped (PRD §11.4). {@code tanggal} {@code null} =
     * hari ini menurut zona kantin.
     */
    @Transactional(readOnly = true)
    public List<RekapSetoranItem> rekap(Long sekolahId, LocalDate tanggal) {
        LocalDate hari = (tanggal != null) ? tanggal : jam.hariIni();
        OffsetDateTime sejak = hari.atStartOfDay(jam.zona()).toOffsetDateTime();
        OffsetDateTime sampai = sejak.plusDays(1);

        List<MutasiSaldoRepository.RekapTopupPetugas> rekapTopup =
                ledgerRepo.rekapTopupTunaiPerPetugas(sekolahId, JenisMutasiSaldo.TOPUP_TUNAI,
                        ArahMutasi.KREDIT, sejak, sampai);
        List<Setoran> setoran = setoranRepo
                .findBySekolahIdAndTanggalOrderByPetugasIdAsc(sekolahId, hari);

        return rekapTopup.stream().map(r -> {
            long total = r.getTotal() == null ? 0L : r.getTotal();
            Setoran s = setoran.stream()
                    .filter(x -> x.getPetugasId().equals(r.getPetugasId()))
                    .findFirst().orElse(null);
            long disetor = (s == null || s.getJumlahDisetor() == null) ? 0L : s.getJumlahDisetor();
            return RekapSetoranItem.builder()
                    .petugasId(r.getPetugasId())
                    .tanggal(hari)
                    .totalTopup(total)
                    .jumlahDisetor(disetor)
                    .selisih(total - disetor)
                    .referensiId(s == null ? null : s.getReferensiId())
                    .build();
        }).toList();
    }

    /** Kunci pengelompokan rekap harian per petugas pada mode rentang tanggal. */
    private record KunciHarianPetugas(LocalDate tanggal, Long petugasId) {}

    /**
     * Rekap setoran TU pada satu tanggal <b>atau</b> rentang waktu {@code [dari, sampai)}.
     * Bila {@code dari} dan {@code sampai} kosong, mendelegasikan ke mode harian tunggal
     * {@link #rekap(Long, LocalDate)}. Bila rentang diberikan, menghasilkan satu baris
     * per kombinasi {@code (tanggal, petugasId)} (terbaru lebih dulu).
     */
    @Transactional(readOnly = true)
    public List<RekapSetoranItem> rekap(Long sekolahId, LocalDate tanggal,
                                        OffsetDateTime dari, OffsetDateTime sampai) {
        if (dari == null && sampai == null) {
            return rekap(sekolahId, tanggal);
        }

        LocalDate hariDasar = (tanggal != null) ? tanggal : jam.hariIni();
        OffsetDateTime awal = (dari != null)
                ? dari
                : hariDasar.atStartOfDay(jam.zona()).toOffsetDateTime();
        OffsetDateTime akhir = (sampai != null) ? sampai : awal.plusDays(1);
        if (!akhir.isAfter(awal)) {
            throw new InvalidOperationException("Parameter sampai harus lebih besar dari dari");
        }

        List<MutasiSaldoRepository.TopupTunaiBaris> barisTopup =
                ledgerRepo.daftarTopupTunaiPetugas(sekolahId, JenisMutasiSaldo.TOPUP_TUNAI,
                        ArahMutasi.KREDIT, awal, akhir);

        Map<KunciHarianPetugas, Long> totalPerHari = new HashMap<>();
        for (MutasiSaldoRepository.TopupTunaiBaris b : barisTopup) {
            LocalDate tglBaris = b.getWaktu().atZoneSameInstant(jam.zona()).toLocalDate();
            long nominal = b.getNominal() == null ? 0L : b.getNominal();
            totalPerHari.merge(new KunciHarianPetugas(tglBaris, b.getPetugasId()), nominal, Long::sum);
        }

        LocalDate tanggalMulai = awal.atZoneSameInstant(jam.zona()).toLocalDate();
        LocalDate tanggalSelesai = akhir.minusNanos(1).atZoneSameInstant(jam.zona()).toLocalDate();
        if (tanggalSelesai.isBefore(tanggalMulai)) {
            tanggalSelesai = tanggalMulai;
        }

        List<Setoran> daftarSetoran = setoranRepo
                .findBySekolahIdAndTanggalBetweenOrderByTanggalDescPetugasIdAsc(
                        sekolahId, tanggalMulai, tanggalSelesai);
        Map<KunciHarianPetugas, Setoran> setoranMap = new HashMap<>();
        for (Setoran s : daftarSetoran) {
            setoranMap.putIfAbsent(new KunciHarianPetugas(s.getTanggal(), s.getPetugasId()), s);
        }

        Comparator<KunciHarianPetugas> urutTerbaru = Comparator
                .comparing(KunciHarianPetugas::tanggal, Comparator.reverseOrder())
                .thenComparing(KunciHarianPetugas::petugasId);
        TreeSet<KunciHarianPetugas> semuaKunci = new TreeSet<>(urutTerbaru);
        semuaKunci.addAll(totalPerHari.keySet());
        semuaKunci.addAll(setoranMap.keySet());

        return semuaKunci.stream().map(k -> {
            Setoran s = setoranMap.get(k);
            long total = totalPerHari.getOrDefault(k,
                    s != null && s.getTotalTopup() != null ? s.getTotalTopup() : 0L);
            long disetor = (s == null || s.getJumlahDisetor() == null) ? 0L : s.getJumlahDisetor();
            return RekapSetoranItem.builder()
                    .petugasId(k.petugasId())
                    .tanggal(k.tanggal())
                    .totalTopup(total)
                    .jumlahDisetor(disetor)
                    .selisih(total - disetor)
                    .referensiId(s == null ? null : s.getReferensiId())
                    .build();
        }).toList();
    }

    /**
     * Konfirmasi setoran kas TU oleh bendahara (PRD §9.2).
     *
     * <p>Mencatat baris {@code setoran_tu} append-only: {@code totalTopup}
     * diambil dari rekap ledger saat konfirmasi (sumber kebenaran), lalu
     * {@code selisih = totalTopup − jumlahDisetor} dihitung DB. Selisih
     * <b>dicatat, tidak dihapus</b>.
     *
     * <p>Idempoten: bila {@code referensiId} sudah pernah dipakai (per sekolah),
     * mengembalikan baris lama tanpa mencatat ulang — <b>termasuk</b> saat
     * petugasnya sudah dinonaktifkan setelah konfirmasi pertama (retry jaringan
     * tidak boleh berubah menjadi 404; lihat urutan di badan metode).
     *
     * <p><b>Validasi petugas (SET-05):</b> pada jalur yang benar-benar membuat
     * baris baru, {@code petugasId} wajib menunjuk pengguna <b>aktif di sekolah
     * pemanggil</b>. FK {@code setoran.petugas_id} hanya unik global, jadi tanpa
     * cek ini id staf tenant lain akan lolos dan tercatat sebagai petugas setoran.
     * Role tidak dibatasi di sini: sumber kebenaran petugas adalah aktor
     * {@code TOPUP_TUNAI} di ledger (itulah yang muncul di rekap), sementara
     * siapa yang boleh melakukan top-up sudah dijaga RBAC endpoint top-up
     * (hanya TU/bendahara).
     *
     * @param referensiId nomor berita acara setoran (unik, idempotency)
     * @param aktorId     bendahara yang mengonfirmasi (audit)
     * @throws NotFoundEntity bila petugas tidak ada di sekolah ini atau nonaktif
     */
    @Transactional
    public SetoranResponse konfirmasi(Long sekolahId, LocalDate tanggal, Long petugasId,
                                        long jumlahDisetor, String referensiId, String catatan,
                                        Long aktorId) {
        if (tanggal == null) {
            throw new InvalidOperationException("Tanggal rekap setoran wajib diisi");
        }
        if (petugasId == null) {
            throw new InvalidOperationException("ID petugas TU wajib diisi");
        }
        if (jumlahDisetor < 0) {
            throw new InvalidOperationException("Jumlah disetor tidak boleh negatif");
        }
        if (referensiId == null || referensiId.isBlank()) {
            throw new InvalidOperationException(
                    "Nomor berita acara setoran wajib diisi (idempotency)");
        }
        // Idempotency DULU, validasi petugas kemudian: konfirmasi yang sudah
        // tercatat lalu petugasnya dinonaktifkan harus tetap mengembalikan baris
        // lama saat klien retry (mis. timeout jaringan). Kalau validasi aktif
        // dijalankan lebih dulu, retry itu dijawab 404 dan klien menyimpulkan
        // setoran gagal padahal uangnya sudah tercatat — persis yang ingin
        // dicegah idempotency. Lookup ini tenant-scoped, jadi replay tidak bisa
        // mengembalikan baris sekolah lain.
        var lama = setoranRepo.findBySekolahIdAndReferensiId(sekolahId, referensiId);
        if (lama.isPresent()) {
            log.debug("Setoran TU replay referensi={} → id={}", referensiId, lama.get().getId());
            return SetoranResponse.dari(lama.get());
        }

        // Jalur tulis: petugas wajib pengguna aktif di sekolah pemanggil.
        pastikanPetugasAktifDiSekolah(sekolahId, petugasId);

        OffsetDateTime sejak = tanggal.atStartOfDay(jam.zona()).toOffsetDateTime();
        OffsetDateTime sampai = sejak.plusDays(1);
        Long total = ledgerRepo.hitungTopupTunaiPetugas(sekolahId, JenisMutasiSaldo.TOPUP_TUNAI,
                ArahMutasi.KREDIT, petugasId, sejak, sampai);
        long totalTopup = total == null ? 0L : total;

        long selisih = totalTopup - jumlahDisetor;
        if (selisih != 0 && (catatan == null || catatan.isBlank())) {
            throw new InvalidOperationException(
                    "Selisih kas Rp" + selisih + " — catatan alasan selisih wajib diisi (PRD §9.2)");
        }

        OffsetDateTime now = jam.sekarang();
        Setoran baris = Setoran.builder()
                .id(idGenerator.berikutnyaLong())
                .sekolahId(sekolahId)
                .tanggal(tanggal)
                .petugasId(petugasId)
                .totalTopup(totalTopup)
                .jumlahDisetor(jumlahDisetor)
                .referensiId(referensiId)
                .catatan(catatan)
                .dikonfirmasiOleh(aktorId)
                .waktu(now)
                .createdAt(now)
                .build();
        Setoran tersimpan = setoranRepo.save(baris);

        auditLogger.catat(aktorId, sekolahId, "KONFIRMASI_SETORAN_TU", "Setoran",
                String.valueOf(tersimpan.getId()),
                catatan == null || catatan.isBlank() ? "Setoran sesuai" : catatan,
                String.valueOf(totalTopup), String.valueOf(jumlahDisetor));

        log.info("Setoran TU sekolah={} tanggal={} petugas={} topup={} disetor={} selisih={}",
                sekolahId, tanggal, petugasId, totalTopup, jumlahDisetor, selisih);
        return SetoranResponse.dari(tersimpan);
    }

    /**
     * Pastikan {@code petugasId} adalah pengguna aktif di sekolah pemanggil.
     *
     * <p>Tenant lain dijawab 404 (bukan 403) agar keberadaan akun sekolah lain
     * tidak bocor — konsisten dengan aturan isolasi tenant PRD §11.4.
     *
     * <p>Pesan "tidak ditemukan" dan "tidak aktif" sengaja dibedakan: pemanggil
     * endpoint ini hanya TU/bendahara sekolah yang sama, jadi membedakan status
     * akun staf sendiri membuat galat bisa ditindaklanjuti dan bukan kebocoran.
     */
    private void pastikanPetugasAktifDiSekolah(Long sekolahId, Long petugasId) {
        Pengguna petugas = penggunaRepo.findByIdAndSekolahId(petugasId, sekolahId)
                .orElseThrow(() -> new NotFoundEntity(
                        "Petugas TU tidak ditemukan di sekolah ini"));
        if (!petugas.isAktif()) {
            throw new NotFoundEntity("Petugas TU tidak aktif");
        }
    }
}
