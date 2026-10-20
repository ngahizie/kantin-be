package com.asqi.scholia_kantin_be.service.kasir;

import com.asqi.scholia_kantin_be.dto.HalamanResponse;
import com.asqi.scholia_kantin_be.dto.TransaksiKasirItem;
import com.asqi.scholia_kantin_be.dto.TransaksiSesiTutupItem;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.StatusTransaksi;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.model.KartuTamu;
import com.asqi.scholia_kantin_be.model.SaldoLedger;
import com.asqi.scholia_kantin_be.model.TitikKasir;
import com.asqi.scholia_kantin_be.model.Transaksi;
import com.asqi.scholia_kantin_be.model.TransaksiItem;
import com.asqi.scholia_kantin_be.repository.KartuTamuRepository;
import com.asqi.scholia_kantin_be.repository.SaldoLedgerRepository;
import com.asqi.scholia_kantin_be.repository.TitikKasirRepository;
import com.asqi.scholia_kantin_be.repository.TransaksiItemRepository;
import com.asqi.scholia_kantin_be.repository.TransaksiRepository;
import com.asqi.scholia_kantin_be.service.CariSiswaService;
import com.asqi.scholia_kantin_be.service.integrasi.InfoSiswa;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sisi <b>baca</b> transaksi kasir — daftar transaksi untuk koreksi bendahara
 * (PRD §6.3, §9.2; issue <b>#212</b>).
 *
 * <p><b>Latar.</b> Aksi koreksi sudah ada
 * ({@code POST /api/kasir/transaksi/{transaksiId}/koreksi}), tetapi bendahara
 * tidak punya cara menemukan {@code transaksiId}: FE memanggil endpoint daftar
 * yang belum ada ({@code GET /api/transaksi/sesi-tutup},
 * {@code GET /api/v1/kasir/transaksi-lampau}) dan menerima <b>404</b>. Kelas ini
 * menutup celah tersebut.
 *
 * <p><b>Tenant-scoped</b> — selalu difilter {@code sekolah_id} dari token
 * (PRD §11.4), bukan dari parameter klien.
 *
 * <p><b>Filter dinamis via Criteria API</b>, bukan JPQL
 * {@code :x IS NULL OR ...}: pola terakhir membuat Hibernate mengirim parameter
 * {@code null} tanpa tipe dan PostgreSQL menolaknya
 * ({@code could not determine data type of parameter}) — akar bug #198
 * (endpoint daftar selalu 500). Lihat juga {@code RiwayatStokIT}.
 */
@Service
@RequiredArgsConstructor
public class TransaksiKasirService {

    /** Batas atas ukuran halaman — sama dengan pola {@code /api/stok/riwayat}. */
    private static final int UKURAN_MAKS = 200;
    private static final int UKURAN_DEFAULT = 20;

    /** Batas baris daftar kandidat koreksi sesi tertutup (issue #230). */
    private static final int UKURAN_MAKS_BARIS = 200;

    /** Jendela default kandidat koreksi bila klien tidak mengirim periode (#230). */
    private static final int HARI_KANDIDAT = 7;

    private final TransaksiRepository transaksiRepo;
    private final TitikKasirRepository titikKasirRepo;
    private final KartuTamuRepository kartuTamuRepo;
    private final TransaksiItemRepository transaksiItemRepo;
    private final SaldoLedgerRepository saldoLedgerRepo;
    private final CariSiswaService cariSiswaService;
    private final JamKantin jam;

    /**
     * Daftar transaksi satu sekolah, terbaru dulu, berhalaman.
     *
     * @param sekolahId tenant dari token (wajib)
     * @param sesiId    filter sesi kasir (opsional)
     * @param tanggal   filter satu hari menurut zona sekolah (opsional)
     * @param status    filter {@code SUKSES}/{@code VOID} (opsional)
     */
    @Transactional(readOnly = true)
    public HalamanResponse<TransaksiKasirItem> daftar(Long sekolahId, Long sesiId, LocalDate tanggal,
                                                      StatusTransaksi status, int halaman, int ukuran) {
        int ukuranAman = (ukuran <= 0 || ukuran > UKURAN_MAKS) ? UKURAN_DEFAULT : ukuran;
        int halamanAman = Math.max(0, halaman);
        Pageable pageable = PageRequest.of(halamanAman, ukuranAman,
                Sort.by(Sort.Direction.DESC, "waktu").and(Sort.by(Sort.Direction.DESC, "id")));

        Page<Transaksi> page = transaksiRepo.findAll(
                filter(sekolahId, sesiId, tanggal, status), pageable);

        // Nama untuk tampilan — diambil BATCH (satu query per jenis), bukan per baris.
        Map<Long, String> namaTitik = namaTitikKasir(sekolahId, page.getContent());
        Map<Long, String> namaSubjek = namaPemegangKartu(sekolahId, page.getContent());

        return HalamanResponse.<Transaksi, TransaksiKasirItem>dari(page, t -> {
            TransaksiKasirItem item = TransaksiKasirItem.dari(t);
            item.setTitikKasirNama(namaTitik.get(t.getTitikKasirId()));
            if (t.getSubjekTipe() == SubjekTipe.KARTU_TAMU) {
                item.setSubjekNama(namaSubjek.get(t.getSubjekId()));
            }
            return item;
        });
    }

    // ────────────────────────────────────────────────────────────────
    // Daftar transaksi sesi TERTUTUP — kandidat koreksi bendahara (#230)
    // ────────────────────────────────────────────────────────────────

    /**
     * Daftar transaksi pada sesi yang sudah <b>DITUTUP</b> — kandidat koreksi
     * bendahara (PRD §6.3, §9.2; issue <b>#230</b>).
     *
     * <p>Dipakai {@code GET /api/transaksi/sesi-tutup}: halaman FE
     * {@code KoreksiBendaharaPage} memanggil path itu dan, karena belum ada,
     * diam-diam menampilkan {@code MOCK_KOREKSI_BENDAHARA} — data palsu yang
     * tidak terlihat sebagai error.
     *
     * <p><b>Periode.</b> {@code tanggal} (satu hari, zona sekolah) atau rentang
     * {@code dari}/{@code sampai}; bila ketiganya kosong dipakai <b>7 hari
     * terakhir</b> — kandidat koreksi bersifat operasional harian, bukan
     * arsip, jadi jangan menarik seluruh riwayat.
     *
     * @param sekolahId tenant dari token (wajib)
     * @param tanggal   filter satu hari (zona sekolah), opsional
     * @param dari      awal rentang, opsional
     * @param sampai    akhir rentang (eksklusif), opsional
     * @param batas     jumlah maksimum baris (default {@value #UKURAN_DEFAULT},
     *                  maks {@value #UKURAN_MAKS_BARIS})
     */
    @Transactional(readOnly = true)
    public List<TransaksiSesiTutupItem> sesiTutup(Long sekolahId, LocalDate tanggal,
                                                  OffsetDateTime dari, OffsetDateTime sampai,
                                                  int batas) {
        OffsetDateTime[] r = rentangKandidat(tanggal, dari, sampai);
        int batasAman = (batas <= 0 || batas > UKURAN_MAKS_BARIS) ? UKURAN_DEFAULT : batas;

        List<Transaksi> baris = transaksiRepo.sesiTutupRentang(
                sekolahId, r[0], r[1], PageRequest.of(0, batasAman));
        if (baris.isEmpty()) {
            return List.of();
        }

        Set<Long> ids = baris.stream().map(Transaksi::getId).collect(Collectors.toSet());
        Map<Long, String> namaTitik = namaTitikKasir(sekolahId, baris);
        Map<Long, String> labelKartu = namaPemegangKartu(sekolahId, baris);
        Map<Long, String> nomorKartu = nomorKartuTamu(sekolahId, baris);
        Map<Long, InfoSiswa> siswa = siswaTerkait(sekolahId, baris);
        Map<Long, List<TransaksiItem>> itemPerTransaksi = itemPerTransaksi(ids);
        Map<Long, String> referensiKoreksi = referensiKoreksi(sekolahId, ids);

        List<TransaksiSesiTutupItem> hasil = new ArrayList<>(baris.size());
        for (Transaksi t : baris) {
            InfoSiswa s = t.getSubjekTipe() == SubjekTipe.SISWA
                    ? siswa.get(t.getSubjekId())
                    : null;
            String kartu = labelKartu.get(t.getSubjekId());

            // FE memanggil .toLowerCase() pada kedua field ini → jangan pernah null.
            String subjekNama = s != null ? teks(s.getNama())
                    : (kartu != null ? kartu : "");
            String subjekInfo = s != null ? infoSiswa(s)
                    : (t.getSubjekTipe() == SubjekTipe.KARTU_TAMU ? teks(nomorKartu.get(t.getSubjekId())) : "");

            List<TransaksiSesiTutupItem.Item> items =
                    itemPerTransaksi.getOrDefault(t.getId(), List.of()).stream()
                            .map(i -> new TransaksiSesiTutupItem.Item(
                                    i.getMenuId(), i.getNamaMenu(), i.getQty(),
                                    i.getHargaJual(), i.getSubtotal()))
                            .toList();

            hasil.add(new TransaksiSesiTutupItem(
                    String.valueOf(t.getId()),
                    t.getWaktu(),
                    t.getSesiKasirId(),
                    teks(namaTitik.get(t.getTitikKasirId())),
                    "", // nama petugas ada di admin-be (ADR-0002) — tidak difabrikasi
                    t.getSubjekTipe(),
                    t.getSubjekId(),
                    subjekNama,
                    subjekInfo,
                    t.getTotal(),
                    items,
                    TransaksiSesiTutupItem.statusFe(t.getStatus()),
                    referensiKoreksi.get(t.getId())));
        }
        return hasil;
    }

    /** Rentang kandidat koreksi: tanggal → sehari; dari/sampai → rentang; kosong → 7 hari. */
    private OffsetDateTime[] rentangKandidat(LocalDate tanggal, OffsetDateTime dari,
                                             OffsetDateTime sampai) {
        if (dari != null || sampai != null) {
            OffsetDateTime awal = dari != null ? dari
                    : jam.hariIni().minusDays(HARI_KANDIDAT - 1).atStartOfDay(jam.zona()).toOffsetDateTime();
            OffsetDateTime akhir = sampai != null ? sampai
                    : jam.hariIni().plusDays(1).atStartOfDay(jam.zona()).toOffsetDateTime();
            return new OffsetDateTime[]{awal, akhir};
        }
        if (tanggal != null) {
            OffsetDateTime awal = tanggal.atStartOfDay(jam.zona()).toOffsetDateTime();
            return new OffsetDateTime[]{awal, awal.plusDays(1)};
        }
        OffsetDateTime akhir = jam.hariIni().plusDays(1).atStartOfDay(jam.zona()).toOffsetDateTime();
        return new OffsetDateTime[]{akhir.minusDays(HARI_KANDIDAT), akhir};
    }

    /** Peta {@code transaksiId → daftar item} (satu query untuk seluruh halaman). */
    private Map<Long, List<TransaksiItem>> itemPerTransaksi(Set<Long> transaksiIds) {
        return transaksiItemRepo.findByTransaksiIdIn(transaksiIds).stream()
                .collect(Collectors.groupingBy(i -> i.getTransaksi().getId()));
    }

    /**
     * Peta {@code transaksiId → referensi koreksi} untuk transaksi yang sudah
     * dikoreksi bendahara (mutasi ledger {@code KOREKSI} atas transaksi itu).
     */
    private Map<Long, String> referensiKoreksi(Long sekolahId, Set<Long> transaksiIds) {
        Map<Long, String> hasil = new HashMap<>();
        for (SaldoLedger l : saldoLedgerRepo.findBySekolahIdAndTransaksiIdIn(sekolahId, transaksiIds)) {
            if (l.getJenis() == JenisMutasiSaldo.KOREKSI && l.getTransaksiId() != null) {
                hasil.putIfAbsent(l.getTransaksiId(), l.getReferensiId());
            }
        }
        return hasil;
    }

    /**
     * Data siswa untuk subjek {@code SISWA} di halaman ini.
     *
     * <p>Master siswa milik admin-be (ADR-0002) — diambil lewat
     * {@link CariSiswaService} sekali per permintaan (bukan per baris). Siswa di
     * luar jendela hasil port tetap tampil dengan nama kosong, bukan data karangan.
     */
    private Map<Long, InfoSiswa> siswaTerkait(Long sekolahId, List<Transaksi> baris) {
        boolean adaSiswa = baris.stream().anyMatch(t -> t.getSubjekTipe() == SubjekTipe.SISWA);
        if (!adaSiswa) {
            return Map.of();
        }
        Map<Long, InfoSiswa> peta = new HashMap<>();
        for (InfoSiswa s : cariSiswaService.cari(sekolahId, null, false,
                CariSiswaService.BATAS_MAKS)) {
            peta.put(s.getSiswaId(), s);
        }
        return peta;
    }

    /** {@code "kelas (NIS: nis)"} — bentuk yang dibaca FE ({@code subjek_info}). */
    private static String infoSiswa(InfoSiswa s) {
        String kelas = teks(s.getKelas());
        String nis = teks(s.getNis());
        if (kelas.isEmpty()) {
            return nis.isEmpty() ? "" : "NIS: " + nis;
        }
        return nis.isEmpty() ? kelas : kelas + " (NIS: " + nis + ")";
    }

    /** Peta {@code kartuTamuId → nomor kartu} (satu query) untuk {@code subjek_info}. */
    private Map<Long, String> nomorKartuTamu(Long sekolahId, List<Transaksi> baris) {
        Set<Long> ids = baris.stream()
                .filter(t -> t.getSubjekTipe() == SubjekTipe.KARTU_TAMU)
                .map(Transaksi::getSubjekId)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return kartuTamuRepo.findAllById(ids).stream()
                .filter(k -> k.getSekolahId().equals(sekolahId))
                .collect(Collectors.toMap(KartuTamu::getId, KartuTamu::getNomorKartu, (a, b) -> a));
    }

    private static String teks(String nilai) {
        return nilai == null ? "" : nilai;
    }

    // ────────────────────────────────────────────────────────────────
    // HELPER
    // ────────────────────────────────────────────────────────────────

    /**
     * Predicate filter daftar transaksi (Criteria API) — <b>hanya</b> menambahkan
     * syarat untuk filter yang benar-benar terisi.
     *
     * <p>Kenapa bukan JPQL {@code :x IS NULL OR ...}: lihat javadoc kelas
     * (bug #198 — PostgreSQL menolak parameter {@code null} tanpa tipe).
     */
    private Specification<Transaksi> filter(Long sekolahId, Long sesiId,
                                            LocalDate tanggal, StatusTransaksi status) {
        return (root, query, cb) -> {
            List<Predicate> syarat = new ArrayList<>();
            syarat.add(cb.equal(root.get("sekolahId"), sekolahId));   // tenant-scoped
            if (sesiId != null) {
                syarat.add(cb.equal(root.get("sesiKasirId"), sesiId));
            }
            if (status != null) {
                syarat.add(cb.equal(root.get("status"), status));
            }
            if (tanggal != null) {
                // Batas hari menurut zona sekolah (JamKantin), bukan zona server.
                OffsetDateTime awal = tanggal.atStartOfDay(jam.zona()).toOffsetDateTime();
                syarat.add(cb.greaterThanOrEqualTo(root.get("waktu"), awal));
                syarat.add(cb.lessThan(root.get("waktu"), awal.plusDays(1)));
            }
            return cb.and(syarat.toArray(new Predicate[0]));
        };
    }

    /** Peta {@code titikKasirId → nama} untuk baris di halaman ini (satu query). */
    private Map<Long, String> namaTitikKasir(Long sekolahId, List<Transaksi> baris) {
        Set<Long> ids = baris.stream()
                .map(Transaksi::getTitikKasirId)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return titikKasirRepo.findAllById(ids).stream()
                .filter(t -> t.getSekolahId().equals(sekolahId))
                .collect(Collectors.toMap(TitikKasir::getId, TitikKasir::getNama, (a, b) -> a));
    }

    /**
     * Peta {@code kartuTamuId → label pemegang} untuk subjek {@code KARTU_TAMU}
     * di halaman ini (satu query). Label kosong jatuh ke nomor kartu.
     */
    private Map<Long, String> namaPemegangKartu(Long sekolahId, List<Transaksi> baris) {
        Set<Long> ids = baris.stream()
                .filter(t -> t.getSubjekTipe() == SubjekTipe.KARTU_TAMU)
                .map(Transaksi::getSubjekId)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return kartuTamuRepo.findAllById(ids).stream()
                .filter(k -> k.getSekolahId().equals(sekolahId))
                .collect(Collectors.toMap(KartuTamu::getId, TransaksiKasirService::labelKartu, (a, b) -> a));
    }

    private static String labelKartu(KartuTamu k) {
        String label = k.getLabelPemegang();
        return (label == null || label.isBlank()) ? k.getNomorKartu() : label;
    }
}
