package com.asqi.scholia_kantin_be.service.laporan;

import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import com.asqi.scholia_kantin_be.dto.LaporanPerSiswa;
import com.asqi.scholia_kantin_be.dto.LaporanRiwayatKeseluruhanItem;
import com.asqi.scholia_kantin_be.dto.LaporanRiwayatSiswa;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.JenisPembatasan;
import com.asqi.scholia_kantin_be.enums.StatusTransaksi;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.model.MutasiSaldo;
import com.asqi.scholia_kantin_be.model.Pembatasan;
import com.asqi.scholia_kantin_be.model.Pengguna;
import com.asqi.scholia_kantin_be.model.Saldo;
import com.asqi.scholia_kantin_be.model.Transaksi;
import com.asqi.scholia_kantin_be.model.TransaksiItem;
import com.asqi.scholia_kantin_be.repository.MutasiSaldoRepository;
import com.asqi.scholia_kantin_be.repository.PembatasanRepository;
import com.asqi.scholia_kantin_be.repository.PenggunaRepository;
import com.asqi.scholia_kantin_be.repository.SaldoRepository;
import com.asqi.scholia_kantin_be.repository.TitikKasirRepository;
import com.asqi.scholia_kantin_be.repository.TransaksiItemRepository;
import com.asqi.scholia_kantin_be.repository.TransaksiRepository;
import com.asqi.scholia_kantin_be.service.CariSiswaService;
import com.asqi.scholia_kantin_be.service.integrasi.InfoSiswa;
import com.asqi.scholia_kantin_be.service.kasir.LedgerSaldoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Laporan per siswa (PRD §9.5, issue #117 &amp; #223): riwayat satu siswa,
 * riwayat keseluruhan, dan laporan per siswa (ringkasan + transaksi + mutasi).
 *
 * <p>Semua query tenant-scoped dan baca-saja. Identitas siswa (NIS/nama/kelas)
 * tidak disimpan kantin-be (ADR-0002) sehingga diambil dari admin-be via
 * {@link CariSiswaService}; bila lookup gagal, profil tetap dikirim dengan
 * identitas null (fail-open).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LaporanSiswaService {

    /** Rentang default riwayat siswa bila FE tidak mengirim dari/sampai. */
    private static final int HARI_RIWAYAT_DEFAULT = 30;

    /** Format label periode laporan — dibuat sekali, bukan tiap pemanggilan (issue #362). */
    private static final DateTimeFormatter LABEL_PERIODE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final MutasiSaldoRepository saldoLedgerRepo;
    private final SaldoRepository saldoCacheRepo;
    private final TransaksiRepository transaksiRepo;
    private final TransaksiItemRepository transaksiItemRepo;
    private final TitikKasirRepository titikKasirRepo;
    private final PenggunaRepository penggunaRepo;
    private final PembatasanRepository pembatasanRepo;
    private final LedgerSaldoService ledgerSaldo;
    private final CariSiswaService cariSiswaService;
    private final JamKantin jam;

    /**
     * Riwayat mutasi saldo satu subjek pada periode (tanpa validasi tenant).
     *
     * @return mutasi saldo terbaru lebih dulu
     */
    @Transactional(readOnly = true)
    public List<MutasiSaldo> mutasiSaldoRentang(Long sekolahId, LocalDate tanggal,
                                                 OffsetDateTime dari, OffsetDateTime sampai) {
        OffsetDateTime[] r = LaporanHelper.rentang(jam, tanggal, dari, sampai);
        return saldoLedgerRepo.padaRentang(sekolahId, r[0], r[1]);
    }

    /**
     * Riwayat satu siswa untuk komplain orang tua (issue #223): profil + seluruh
     * mutasi saldo pada rentang, terbaru lebih dulu, dengan rincian belanja.
     *
     * <p><b>Tenant guard.</b> Siswa dianggap ada hanya bila punya baris
     * {@code saldo_cache} di sekolah pemanggil — id siswa milik sekolah lain
     * menghasilkan <b>404</b>. Default rentang 30 hari terakhir bila FE tidak
     * mengirim {@code dari}/{@code sampai}.
     *
     * @param sekolahId tenant pemanggil
     * @param siswaId   id subjek
     * @param dari      awal periode (opsional)
     * @param sampai    akhir periode (opsional)
     * @return profil + daftar riwayat
     * @throws NotFoundEntity bila siswa tidak punya saldo_cache di sekolah ini
     */
    @Transactional(readOnly = true)
    public LaporanRiwayatSiswa riwayatSiswa(Long sekolahId, Long siswaId,
                                            OffsetDateTime dari, OffsetDateTime sampai) {
        Saldo cache = saldoCacheRepo.findBySekolahIdAndSiswaId(sekolahId, siswaId)
                .orElseThrow(() -> new NotFoundEntity("Siswa " + siswaId + " tidak ditemukan"));

        OffsetDateTime akhir = sampai != null ? sampai : OffsetDateTime.now(jam.zona());
        OffsetDateTime awal = dari != null ? dari : akhir.minusDays(HARI_RIWAYAT_DEFAULT);

        List<MutasiSaldo> mutasi = new ArrayList<>(
                saldoLedgerRepo.padaRentangSubjek(sekolahId, siswaId, awal, akhir));
        mutasi.sort(Comparator.comparing(MutasiSaldo::getWaktu).reversed()
                .thenComparing(MutasiSaldo::getId, Comparator.reverseOrder()));

        Set<Long> transaksiIds = mutasi.stream()
                .map(MutasiSaldo::getTransaksiId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Transaksi> transaksiById = transaksiIds.isEmpty() ? Map.of()
                : transaksiRepo.findAllById(transaksiIds).stream()
                .filter(t -> sekolahId.equals(t.getSekolahId()))
                .collect(Collectors.toMap(Transaksi::getId, Function.identity()));

        Map<Long, String> namaTitik = new HashMap<>();
        titikKasirRepo.findBySekolahId(sekolahId).forEach(t -> namaTitik.put(t.getId(), t.getNama()));

        Map<Long, List<TransaksiItem>> itemByTrx = transaksiIds.isEmpty() ? Map.of()
                : transaksiItemRepo.findByTransaksiIdIn(transaksiIds).stream()
                .collect(Collectors.groupingBy(i -> i.getTransaksi().getId()));

        Map<Long, String> namaPetugas = ambilNamaPetugas(sekolahId, mutasi, transaksiById);

        List<LaporanRiwayatSiswa.Baris> riwayat = mutasi.stream().map(l -> {
            Transaksi t = l.getTransaksiId() == null ? null : transaksiById.get(l.getTransaksiId());
            List<LaporanRiwayatSiswa.Item> items = t == null ? List.of()
                    : itemByTrx.getOrDefault(t.getId(), List.of()).stream()
                    .map(i -> new LaporanRiwayatSiswa.Item(i.getMenuId(), i.getNamaMenu(),
                            i.getQty(), i.getHargaJual(), i.getSubtotal()))
                    .toList();

            String petugas = namaPetugasUntuk(l, t, namaPetugas);

            return new LaporanRiwayatSiswa.Baris(
                    String.valueOf(l.getId()),
                    l.getWaktu(),
                    jenisRiwayat(l.getJenis()),
                    l.getArah() == null ? null : l.getArah().name(),
                    l.getNominal() == null ? 0L : l.getNominal(),
                    l.getSaldoSetelah() == null ? 0L : l.getSaldoSetelah(),
                    t == null ? "-" : namaTitik.getOrDefault(t.getTitikKasirId(), "-"),
                    petugas,
                    t == null ? String.valueOf(l.getId()) : String.valueOf(t.getId()),
                    l.getKeterangan() == null ? "" : l.getKeterangan(),
                    items);
        }).toList();

        List<Pembatasan> pembatasan = pembatasanRepo
                .findBySekolahIdAndSiswaId(sekolahId, siswaId);
        long limit = pembatasan.stream()
                .filter(x -> x.getJenis() == JenisPembatasan.LIMIT_HARIAN && x.isAktif())
                .map(Pembatasan::getNominal)
                .findFirst()
                .orElse(0L);
        Pembatasan blokir = pembatasan.stream()
                .filter(x -> x.getJenis() == JenisPembatasan.BLOKIR_SISWA && x.isAktif())
                .findFirst().orElse(null);
        boolean diblokir = blokir != null;

        // Identitas siswa (NIS/nama/kelas/orang tua) berasal dari admin-be lewat lookup per ID
        // (issue #297). Fail-open: bila integrasi belum aktif / siswa tidak ditemukan,
        // profil tetap dikembalikan dengan identitas null dan riwayat tetap tampil.
        InfoSiswa identitas = null;
        try {
            identitas = cariSiswaService.identitas(sekolahId, siswaId).orElse(null);
        } catch (RuntimeException e) {
            log.warn("Identitas siswa {} tidak dapat diambil (riwayat tetap dikirim): {}",
                    siswaId, e.getMessage());
        }

        LaporanRiwayatSiswa.Profil profil = new LaporanRiwayatSiswa.Profil(
                siswaId,
                identitas == null ? null : identitas.getNis(),
                identitas == null ? null : identitas.getNama(),
                identitas == null ? null : identitas.getKelas(),
                identitas == null ? null : identitas.getFotoUrl(),
                cache.getSaldo() == null ? 0L : cache.getSaldo(),
                ledgerSaldo.belanjaHariIni(sekolahId, siswaId),
                limit,
                diblokir,
                identitas == null ? null : identitas.getNamaOrangTua(),
                identitas == null ? null : identitas.getTeleponOrangTua(),
                diblokir ? blokir.getAlasan() : null);

        return new LaporanRiwayatSiswa(profil, riwayat);
    }

    /**
     * Riwayat transaksi/mutasi saldo keseluruhan siswa untuk halaman Laporan
     * Riwayat Transaksi.
     *
     * @param sekolahId tenant pemanggil
     * @param tanggal   filter tanggal tunggal (opsional)
     * @param dari      awal periode (opsional)
     * @param sampai    akhir periode (opsional)
     * @param batas     jumlah baris, dibatasi 1..500
     * @return daftar item riwayat terbaru lebih dulu
     */
    @Transactional(readOnly = true)
    public List<LaporanRiwayatKeseluruhanItem> riwayatKeseluruhan(
            Long sekolahId, LocalDate tanggal, OffsetDateTime dari, OffsetDateTime sampai, int batas) {

        int batasAman = Math.min(Math.max(batas, 1), 500);
        Pageable pageable = PageRequest.of(0, batasAman);

        // SEC-RIW-06 (#398): rentang terbalik ditolak 400, bukan nol baris senyap.
        if (dari != null && sampai != null) {
            LaporanHelper.pastikanUrutan(dari, sampai);
        }

        List<MutasiSaldo> mutasi;
        if (tanggal != null) {
            OffsetDateTime awal = tanggal.atStartOfDay(jam.zona()).toOffsetDateTime();
            OffsetDateTime akhir = awal.plusDays(1);
            mutasi = saldoLedgerRepo.riwayatKeseluruhanRentang(sekolahId, awal, akhir, pageable);
        } else if (dari != null || sampai != null) {
            OffsetDateTime akhir = sampai != null ? sampai : OffsetDateTime.now(jam.zona());
            OffsetDateTime awal = dari != null ? dari : akhir.minusDays(HARI_RIWAYAT_DEFAULT);
            mutasi = saldoLedgerRepo.riwayatKeseluruhanRentang(sekolahId, awal, akhir, pageable);
        } else {
            mutasi = saldoLedgerRepo.riwayatKeseluruhanTerbaru(sekolahId, pageable);
        }

        Set<Long> transaksiIds = mutasi.stream()
                .map(MutasiSaldo::getTransaksiId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Transaksi> transaksiById = transaksiIds.isEmpty() ? Map.of()
                : transaksiRepo.findAllById(transaksiIds).stream()
                .filter(t -> sekolahId.equals(t.getSekolahId()))
                .collect(Collectors.toMap(Transaksi::getId, Function.identity()));

        Map<Long, String> namaTitik = new HashMap<>();
        titikKasirRepo.findBySekolahId(sekolahId).forEach(t -> namaTitik.put(t.getId(), t.getNama()));

        Map<Long, List<TransaksiItem>> itemByTrx = transaksiIds.isEmpty() ? Map.of()
                : transaksiItemRepo.findByTransaksiIdIn(transaksiIds).stream()
                .collect(Collectors.groupingBy(i -> i.getTransaksi().getId()));

        Map<Long, String> namaPetugas = ambilNamaPetugas(sekolahId, mutasi, transaksiById);

        // SEC-RIW-02 (#398): identitas diambil per ID yang benar-benar muncul di
        // mutasi (bukan 50 hasil pencarian pertama yang memotong senyap).
        Map<Long, InfoSiswa> siswaById = identitasSiswa(sekolahId, mutasi);

        return mutasi.stream().map(l -> {
            Transaksi t = l.getTransaksiId() == null ? null : transaksiById.get(l.getTransaksiId());
            List<LaporanRiwayatSiswa.Item> items = t == null ? List.of()
                    : itemByTrx.getOrDefault(t.getId(), List.of()).stream()
                    .map(i -> new LaporanRiwayatSiswa.Item(i.getMenuId(), i.getNamaMenu(),
                            i.getQty(), i.getHargaJual(), i.getSubtotal()))
                    .toList();

            Long sId = l.getSiswaId();
            InfoSiswa info = sId != null ? siswaById.get(sId) : null;
            String siswaNama = info != null ? info.getNama() : null;
            String siswaNis = info != null ? info.getNis() : null;
            String siswaKelas = info != null ? info.getKelas() : null;

            String petugas = namaPetugasUntuk(l, t, namaPetugas);

            return new LaporanRiwayatKeseluruhanItem(
                    String.valueOf(l.getId()),
                    l.getWaktu(),
                    jenisRiwayat(l.getJenis()),
                    l.getArah() == null ? null : l.getArah().name(),
                    l.getNominal() == null ? 0L : l.getNominal(),
                    l.getSaldoSetelah() == null ? 0L : l.getSaldoSetelah(),
                    t == null ? "-" : namaTitik.getOrDefault(t.getTitikKasirId(), "-"),
                    petugas,
                    t == null ? String.valueOf(l.getId()) : String.valueOf(t.getId()),
                    l.getKeterangan() == null ? "" : l.getKeterangan(),
                    items,
                    sId,
                    siswaNama,
                    siswaNis,
                    siswaKelas);
        }).toList();
    }

    /**
     * Laporan <b>Per siswa</b> (PRD §9.5, issue #117): riwayat lengkap satu
     * subjek ({@code SISWA}) pada periode — ringkasan + daftar transaksi +
     * mutasi saldo. Untuk menjawab komplain orang tua. Tenant-scoped.
     *
     * @return ringkasan sukses/void/topup/HPP + daftar transaksi + daftar mutasi
     */
    @Transactional(readOnly = true)
    public LaporanPerSiswa laporanPerSiswa(Long sekolahId, Long siswaId,
                                           LocalDate tanggal, OffsetDateTime dari, OffsetDateTime sampai) {
        OffsetDateTime[] r = LaporanHelper.rentang(jam, tanggal, dari, sampai);
        List<Transaksi> trx = transaksiRepo.transaksiSubjekRentang(sekolahId, siswaId, r[0], r[1]);
        List<MutasiSaldo> mutasi = saldoLedgerRepo.padaRentangSubjek(sekolahId, siswaId, r[0], r[1]);

        long jmlSukses = 0, nilaiSukses = 0, jmlVoid = 0, nilaiVoid = 0, totalHpp = 0, totalTopup = 0;
        for (Transaksi t : trx) {
            if (t.getStatus() == StatusTransaksi.SUKSES) {
                jmlSukses++;
                nilaiSukses += LaporanHelper.nolJikaNull(t.getTotal());
                totalHpp += LaporanHelper.nolJikaNull(t.getTotalHpp());
            } else if (t.getStatus() == StatusTransaksi.VOID) {
                jmlVoid++;
                nilaiVoid += LaporanHelper.nolJikaNull(t.getTotal());
            }
        }
        for (MutasiSaldo l : mutasi) {
            if (l.getJenis() == JenisMutasiSaldo.TOPUP_TUNAI) {
                totalTopup += LaporanHelper.nolJikaNull(l.getNominal());
            }
        }

        long saldo = saldoCacheRepo.findBySekolahIdAndSiswaId(sekolahId, siswaId)
                .map(c -> c.getSaldo() == null ? 0L : c.getSaldo())
                .orElse(0L);

        return new LaporanPerSiswa(SubjekTipe.SISWA, siswaId, saldo, label(r),
                new LaporanPerSiswa.Ringkasan(jmlSukses, nilaiSukses, jmlVoid, nilaiVoid, totalTopup, totalHpp),
                trx.stream().map(LaporanPerSiswa.BarisTransaksi::dari).toList(),
                mutasi.stream().map(LaporanPerSiswa.BarisMutasi::dari).toList());
    }

    /** Label periode untuk header laporan, format {@code yyyy-MM-dd HH:mm}. */
    private String label(OffsetDateTime[] r) {
        return LABEL_PERIODE.format(r[0]) + " s/d " + LABEL_PERIODE.format(r[1]);
    }

    /**
     * Kumpulkan id petugas dari mutasi &amp; transaksi terkait, lalu ambil namanya.
     *
     * @return map id pengguna → nama; map kosong bila tidak ada id
     */
    private Map<Long, String> ambilNamaPetugas(Long sekolahId, List<MutasiSaldo> mutasi,
                                               Map<Long, Transaksi> transaksiById) {
        Set<Long> penggunaIds = new java.util.HashSet<>();
        for (MutasiSaldo m : mutasi) {
            if (m.getAktorId() != null) {
                penggunaIds.add(m.getAktorId());
            }
        }
        for (Transaksi t : transaksiById.values()) {
            if (t.getPetugasId() != null) {
                penggunaIds.add(t.getPetugasId());
            }
            if (t.getVoidOleh() != null) {
                penggunaIds.add(t.getVoidOleh());
            }
        }
        if (penggunaIds.isEmpty()) {
            return Map.of();
        }
        return penggunaRepo.findByIdInAndSekolahId(penggunaIds, sekolahId).stream()
                .collect(Collectors.toMap(Pengguna::getId, Pengguna::getNama));
    }

    /**
     * Nama petugas untuk satu baris riwayat: transaksi dulu, lalu aktor mutasi.
     *
     * @return nama petugas, atau {@code "-"} bila tidak diketahui
     */
    private String namaPetugasUntuk(MutasiSaldo l, Transaksi t, Map<Long, String> namaPetugas) {
        if (t != null && t.getPetugasId() != null && namaPetugas.containsKey(t.getPetugasId())) {
            return namaPetugas.get(t.getPetugasId());
        }
        if (l.getAktorId() != null && namaPetugas.containsKey(l.getAktorId())) {
            return namaPetugas.get(l.getAktorId());
        }
        return "-";
    }

    /**
     * Jenis mutasi BE → istilah riwayat yang dipakai FE.
     *
     * @return label FE, atau {@code null} bila jenis null
     */
    private static String jenisRiwayat(JenisMutasiSaldo jenis) {
        if (jenis == null) {
            return null;
        }
        return switch (jenis) {
            case PENJUALAN -> "BELANJA";
            case VOID_PENJUALAN -> "VOID";
            default -> jenis.name();
        };
    }

    /**
     * Peta identitas siswa untuk baris mutasi (SEC-RIW-02, issue #398).
     *
     * <p>ID dikumpulkan dari mutasi lalu diambil dalam satu panggilan
     * {@link CariSiswaService#identitasBanyak}. Fail-open (PRD §8.4): integrasi
     * tidak tersedia → peta kosong, riwayat tetap dikirim dengan identitas null.
     */
    private Map<Long, InfoSiswa> identitasSiswa(Long sekolahId, List<MutasiSaldo> mutasi) {
        Set<Long> siswaIds = mutasi.stream()
                .map(MutasiSaldo::getSiswaId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (siswaIds.isEmpty()) {
            return Map.of();
        }
        try {
            return cariSiswaService.identitasBanyak(sekolahId, siswaIds);
        } catch (RuntimeException e) {
            log.warn("Identitas siswa tidak dapat diambil (riwayat tetap dikirim): {}", e.getMessage());
            return Map.of();
        }
    }
}
