package com.asqi.scholia_kantin_be.service.laporan;

import com.asqi.scholia_kantin_be.dto.RingkasanRekonsiliasi;
import com.asqi.scholia_kantin_be.dto.RingkasanSaldoMengendap;
import com.asqi.scholia_kantin_be.dto.SiswaSaldoDetail;
import com.asqi.scholia_kantin_be.enums.ArahMutasi;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.JenisPembatasan;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.model.Pembatasan;
import com.asqi.scholia_kantin_be.model.Saldo;
import com.asqi.scholia_kantin_be.repository.MutasiSaldoRepository;
import com.asqi.scholia_kantin_be.repository.PembatasanRepository;
import com.asqi.scholia_kantin_be.repository.SaldoRepository;
import com.asqi.scholia_kantin_be.repository.projection.ArusSaldo;
import com.asqi.scholia_kantin_be.repository.projection.SaldoMengendap;
import com.asqi.scholia_kantin_be.repository.projection.TotalPerArah;
import com.asqi.scholia_kantin_be.service.siswa.CariSiswaService;
import com.asqi.scholia_kantin_be.service.integrasi.InfoSiswa;
import com.asqi.scholia_kantin_be.service.kasir.LedgerSaldoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Laporan saldo siswa (PRD §9.5): saldo mengendap, rincian per siswa, dan
 * rekonsiliasi harian {@code Σ KREDIT − Σ DEBIT == saldo mengendap}.
 *
 * <p>Semua query <b>tenant-scoped</b> ({@code sekolahId} eksplisit) dan
 * <b>baca-saja</b> — tidak mengubah ledger. Periode memakai rentang
 * inklusif-dari, eksklusif-sampai (lihat {@link LaporanHelper#rentang}).
 *
 * <p>Rekonsiliasi hanya membaca kedua sisi (ledger &amp; cache), tidak menulis
 * koreksi; selisih yang muncul dilaporkan lewat {@code seimbang == false}.
 */
@Service
@RequiredArgsConstructor
public class LaporanSaldoService {

    private final MutasiSaldoRepository saldoLedgerRepo;
    private final SaldoRepository saldoCacheRepo;
    private final PembatasanRepository pembatasanRepo;
    private final CariSiswaService cariSiswaService;
    private final LedgerSaldoService ledgerSaldo;
    private final JamKantin jam;

    /**
     * Total dana titipan (kewajiban sekolah) — seluruhnya saldo siswa.
     *
     * @param sekolahId tenant pemanggil
     * @return ringkasan jumlah siswa &amp; total saldo dari cache
     */
    @Transactional(readOnly = true)
    public RingkasanSaldoMengendap saldoMengendap(Long sekolahId) {
        long saldo = 0, jumlah = 0;
        for (SaldoMengendap b : saldoCacheRepo.totalMengendap(sekolahId)) {
            saldo = LaporanHelper.nolJikaNull(b.getSaldo());
            jumlah = LaporanHelper.nolJikaNull(b.getJumlah());
        }
        return new RingkasanSaldoMengendap(saldo, saldo, jumlah);
    }

    /**
     * Rincian saldo mengendap per siswa (issue #224).
     * Identitas dicari melalui {@link CariSiswaService}; saldo, pembatasan, dan
     * waktu transaksi terakhir dibaca tenant-lokal.
     *
     * <p><b>Query konstan (issue #413):</b> seluruh data pelengkap dibaca
     * <b>batch</b> untuk semua {@code siswaIds} sekaligus — satu query saldo
     * cache ({@code IN}), satu query pembatasan ({@code IN}), satu query waktu
     * terakhir, dan satu query belanja hari ini. Sebelumnya tiga query
     * dijalankan <b>per siswa</b> (N+1), sehingga 50 siswa berarti ±150 query
     * berturut-turut dan berisiko menguras pool koneksi HikariCP
     * (OWASP A06:2025 — Insecure Design, NIST PR.PT-01).
     *
     * @param sekolahId tenant pemanggil
     * @param search    kata kunci NIS/nama (boleh kosong)
     * @param batas     jumlah maksimum siswa yang dikembalikan
     * @return daftar saldo per siswa, terurut sesuai hasil pencarian identitas
     */
    @Transactional(readOnly = true)
    public List<SiswaSaldoDetail> saldoMengendapSiswa(Long sekolahId, String search, int batas) {
        List<InfoSiswa> siswaList = cariSiswaService.cari(sekolahId, search, false, batas);
        List<Long> siswaIds = siswaList.stream()
                .map(InfoSiswa::getSiswaId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // Baca batch: jumlah query tetap, tidak lagi 3 query per siswa.
        Map<Long, Long> saldoPerSiswa = new HashMap<>();
        Map<Long, List<Pembatasan>> pembatasanPerSiswa = new HashMap<>();
        Map<Long, OffsetDateTime> waktuTerakhir = new HashMap<>();
        Map<Long, Long> belanjaPerSiswa = new HashMap<>();
        if (!siswaIds.isEmpty()) {
            for (Saldo s : saldoCacheRepo.findBySekolahIdAndSiswaIdIn(sekolahId, siswaIds)) {
                if (s.getSiswaId() != null) {
                    saldoPerSiswa.put(s.getSiswaId(), LaporanHelper.nolJikaNull(s.getSaldo()));
                }
            }
            pembatasanRepo.findBySekolahIdAndSiswaIdIn(sekolahId, siswaIds)
                    .forEach(p -> pembatasanPerSiswa
                            .computeIfAbsent(p.getSiswaId(), k -> new ArrayList<>())
                            .add(p));
            saldoLedgerRepo.waktuTerakhirPerSubjek(sekolahId, siswaIds)
                    .forEach(b -> waktuTerakhir.put(b.getSiswaId(), b.getWaktu()));
            belanjaPerSiswa.putAll(ledgerSaldo.belanjaHariIniBanyak(sekolahId, siswaIds));
        }

        return siswaList.stream()
                .map(siswa -> {
                    Long siswaId = siswa.getSiswaId();
                    List<Pembatasan> pembatasan = siswaId == null
                            ? List.of()
                            : pembatasanPerSiswa.getOrDefault(siswaId, List.of());
                    boolean diblokir = pembatasan.stream()
                            .anyMatch(x -> x.getJenis() == JenisPembatasan.BLOKIR_SISWA && x.isAktif());
                    Long limit = pembatasan.stream()
                            .filter(x -> x.getJenis() == JenisPembatasan.LIMIT_HARIAN && x.isAktif())
                            .map(Pembatasan::getNominal)
                            .findFirst()
                            .orElse(null);
                    return new SiswaSaldoDetail(
                            siswaId, siswa.getNis(), null, siswa.getNama(), siswa.getKelas(),
                            siswaId == null ? 0L : saldoPerSiswa.getOrDefault(siswaId, 0L),
                            siswaId == null ? 0L : belanjaPerSiswa.getOrDefault(siswaId, 0L),
                            limit, diblokir,
                            siswa.getNamaOrangTua(), siswa.getTeleponOrangTua(),
                            siswaId == null ? null : waktuTerakhir.get(siswaId));
                })
                .toList();
    }

    /**
     * Arus saldo periode + pemeriksaan invariant menyeluruh (PRD §5).
     *
     * <p>Filter {@code tanggal}/{@code dari}/{@code sampai} <b>hanya</b> membatasi
     * field arus per jenis. {@code selisih}, {@code seimbang},
     * {@code totalKreditSemua}, {@code totalDebitSemua}, dan {@code saldoMengendap}
     * selalu dihitung <b>all-time</b> untuk tenant, sesuai kontrak
     * {@link RingkasanRekonsiliasi}.
     *
     * <p>Selisih dihitung {@code (Σ KREDIT − Σ DEBIT) − saldo mengendap};
     * {@code seimbang} bernilai true hanya bila selisih nol.
     *
     * @param sekolahId tenant pemanggil
     * @param tanggal   tanggal tunggal (opsional)
     * @param dari      awal periode eksplisit (opsional)
     * @param sampai    akhir periode eksplisit, eksklusif (opsional)
     * @return arus per jenis mutasi, total kredit/debit, saldo mengendap, dan selisih
     */
    @Transactional(readOnly = true)
    public RingkasanRekonsiliasi rekonsiliasi(Long sekolahId, LocalDate tanggal,
                                              OffsetDateTime dari, OffsetDateTime sampai) {
        OffsetDateTime[] r = LaporanHelper.rentang(jam, tanggal, dari, sampai);
        OffsetDateTime awal = r[0];
        OffsetDateTime akhir = r[1];

        long topupOnline = 0, topupTunai = 0, penjualan = 0, voidPenjualan = 0;
        long koreksiMasuk = 0, koreksiKeluar = 0, refund = 0, transfer = 0;

        for (ArusSaldo b : saldoLedgerRepo.rekapArusRentang(sekolahId, awal, akhir)) {
            ArahMutasi arah = b.getArah();
            JenisMutasiSaldo jenis = b.getJenis();
            long nominal = LaporanHelper.nolJikaNull(b.getNominal());
            switch (jenis) {
                case TOPUP_ONLINE -> topupOnline += nominal;
                case TOPUP_TUNAI -> topupTunai += nominal;
                case PENJUALAN -> penjualan += nominal;
                case VOID_PENJUALAN -> voidPenjualan += nominal;
                case REFUND -> refund += nominal;
                case TRANSFER -> transfer += nominal;
                case KOREKSI, PENYESUAIAN -> {
                    if (arah == ArahMutasi.KREDIT) {
                        koreksiMasuk += nominal;
                    } else {
                        koreksiKeluar += nominal;
                    }
                }
                default -> { /* tidak ada */ }
            }
        }

        long totalKredit = 0, totalDebit = 0;
        for (TotalPerArah b : saldoLedgerRepo.totalPerArah(sekolahId)) {
            ArahMutasi arah = b.getArah();
            long nominal = LaporanHelper.nolJikaNull(b.getNominal());
            if (arah == ArahMutasi.KREDIT) {
                totalKredit = nominal;
            } else {
                totalDebit = nominal;
            }
        }

        long mengendap = saldoMengendap(sekolahId).total();
        long selisih = (totalKredit - totalDebit) - mengendap;

        return new RingkasanRekonsiliasi(awal, akhir, topupOnline, topupTunai,
                penjualan, voidPenjualan, koreksiMasuk, koreksiKeluar, refund,
                transfer, mengendap, totalKredit, totalDebit, selisih, selisih == 0);
    }
}
