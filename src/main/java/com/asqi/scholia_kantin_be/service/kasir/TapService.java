package com.asqi.scholia_kantin_be.service.kasir;

import com.asqi.scholia_kantin_be.component.exception.ConflictException;
import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import com.asqi.scholia_kantin_be.component.metrics.MetrikTap;
import com.asqi.scholia_kantin_be.dto.TapRequest;
import com.asqi.scholia_kantin_be.dto.TapResponse;
import com.asqi.scholia_kantin_be.enums.MetodeBayar;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.enums.JenisNotifikasi;
import com.asqi.scholia_kantin_be.enums.MetodeRequestKartu;
import com.asqi.scholia_kantin_be.enums.StatusPendingTap;
import com.asqi.scholia_kantin_be.enums.StatusTransaksi;
import com.asqi.scholia_kantin_be.helper.NomorStruk;
import com.asqi.scholia_kantin_be.helper.UidKartu;
import com.asqi.scholia_kantin_be.helper.IdGenerator;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.model.SesiKasir;
import com.asqi.scholia_kantin_be.model.Transaksi;
import com.asqi.scholia_kantin_be.model.TransaksiItem;
import com.asqi.scholia_kantin_be.model.TransaksiTertunda;
import com.asqi.scholia_kantin_be.repository.TransaksiTertundaRepository;
import com.asqi.scholia_kantin_be.repository.TitikKasirRepository;
import com.asqi.scholia_kantin_be.repository.TransaksiRepository;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.service.integrasi.InfoKartu;
import com.asqi.scholia_kantin_be.service.integrasi.KartuLookupPort;
import com.asqi.scholia_kantin_be.service.integrasi.MenuLookupPort;
import com.asqi.scholia_kantin_be.service.integrasi.NotifikasiService;
import com.asqi.scholia_kantin_be.service.integrasi.PerintahNotifikasi;
import com.asqi.scholia_kantin_be.service.kartu.KontrolKartuService;
import com.asqi.scholia_kantin_be.service.konfigurasi.PengaturanKantinService;
import com.asqi.scholia_kantin_be.service.stok.HasilMutasiStok;
import com.asqi.scholia_kantin_be.service.stok.LedgerStokService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Alur logika bisnis utama: <b>satu tap di kasir</b> (PRD §6.1–6.2).
 *
 * <p>Langkah (semua dalam <b>satu</b> transaksi DB — PRD §11.2):
 * <ol>
 *   <li><b>Idempotency.</b> Key dari klien diperiksa lebih dulu; key yang sama
 *       mengembalikan hasil transaksi pertama (tap ganda ≠ 2× potong).</li>
 *   <li><b>Lookup kartu</b> (port) — kartu dikenal &amp; tidak diblokir
 *       (diperiksa server tiap tap, tanpa cache — PRD §11.11).</li>
 *   <li><b>Validasi 6 tahap</b> via {@link TapValidator}.</li>
 *   <li><b>Eksekusi atomik:</b> buka/ambil sesi → potong stok (FOR UPDATE) →
 *       catat transaksi + item (snapshot HPP) → debit saldo (FOR UPDATE).</li>
 * </ol>
 *
 * <p><b>Kenapa {@link TransactionTemplate} &amp; bukan {@code @Transactional}?</b>
 * Bila dua request ber-idempotency-key sama masuk nyaris bersamaan, yang kalah
 * akan kena pelanggaran UNIQUE saat menyimpan transaksi. Dengan menangkapnya
 * <i>di luar</i> transaksi (transaksi sudah di-rollback bersih), request yang
 * kalah dapat mengembalikan <b>hasil pemenang</b> — bukan error 409. Ini menjaga
 * kontrak idempotency (PRD §11.3) tetap benar pada kondisi balapan.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TapService {

    private final TransaksiRepository transaksiRepo;
    private final TitikKasirRepository titikKasirRepo;
    private final TransaksiTertundaRepository pendingRepo;
    private final TapValidator validator;
    private final LedgerSaldoService ledgerSaldo;
    private final LedgerStokService ledgerStok;
    private final SesiKasirService sesiKasir;
    private final KartuLookupPort kartuLookup;
    private final MenuLookupPort menuLookup;
    private final IdGenerator idGenerator;
    private final JamKantin jam;
    private final TransactionTemplate txTemplate;
    private final NotifikasiService notifikasi;
    private final KontrolKartuService kontrolKartu;
    private final PengaturanKantinService pengaturan;
    private final MetrikTap metrikTap;
    private final JsonMapper objectMapper = JsonMapper.builder().build();

    /**
     * Proses satu tap.
     *
     * @param sekolahId tenant dari JWT (sudah diverifikasi filter)
     * @param identitas principal petugas (audit)
     * @param request   data tap (uid, items, idempotencyKey, titikKasir)
     * @return hasil tap untuk ditampilkan di layar kasir
     */
    public TapResponse tap(Long sekolahId, IdentitasKantin identitas, TapRequest request) {
        // UID kartu dipakai untuk lookup sekaligus disimpan di transaksi/pending.
        // Pembaca kartu bisa mengirim huruf kecil/campur, sementara kartu.uid
        // case-sensitive → kanonikkan sekali di pintu masuk (issue #346).
        // DTO request milik pemanggil tidak diubah (issue #362): nilai kanonik
        // dibawa sebagai variabel lokal ke seluruh alur tap di bawah.
        String rfidUid = UidKartu.normalisasi(request.getRfidUid());
        long mulai = System.nanoTime();
        try {
            TapResponse hasil = jalankanTap(sekolahId, identitas, request, rfidUid);
            metrikTap.catat(Duration.ofNanos(System.nanoTime() - mulai),
                    hasil.isMenungguKonfirmasi() ? MetrikTap.OUTCOME_MENUNGGU : MetrikTap.OUTCOME_SUKSES);
            return hasil;
        } catch (RuntimeException e) {
            // Gagal validasi/konflik → tetap dicatat agar rasio error & latensi
            // jalur gagal terlihat (SLO tap diukur pada jalur sukses).
            metrikTap.catat(Duration.ofNanos(System.nanoTime() - mulai), MetrikTap.OUTCOME_GAGAL);
            throw e;
        }
    }

    /** Isi alur tap (dipisah agar pengukuran durasi membungkus seluruh jalur). */
    private TapResponse jalankanTap(Long sekolahId, IdentitasKantin identitas, TapRequest request,
                                    String rfidUid) {
        String key = request.getIdempotencyKey();

        try {
            // Semua pra-cek idempotency + eksekusi berjalan dalam SATU transaksi DB,
            // sehingga satu tap hanya mengambil koneksi Hikari SEKALI (perbaikan #180).
            // Sebelumnya kedua pra-cek berada di luar transaksi → 1 tap = 3× acquire,
            // yang menjadi antrean kolam koneksi saat JVM masih dingin (cold-start).
            return txTemplate.execute(status -> {
                // 1) Idempotency jalur cepat: transaksi sudah pernah dibuat → hasil lama.
                if (transaksiRepo.existsBySekolahIdAndIdempotencyKey(sekolahId, key)) {
                    log.debug("Idempotency replay tap key={}", key);
                    return bangunReplay(sekolahId, key);
                }

                // 2) Idempotency jalur pending: tap yang sama masih menunggu konfirmasi.
                var pendingLama = pendingRepo.findBySekolahIdAndIdempotencyKey(sekolahId, key);
                if (pendingLama.isPresent() && pendingLama.get().menunggu()) {
                    log.debug("Idempotency replay pending key={}", key);
                    return bangunMenunggu(pendingLama.get());
                }

                // 3) Eksekusi tap.
                return eksekusi(sekolahId, identitas, request, rfidUid);
            });
        } catch (DataIntegrityViolationException e) {
            // Balapan idempotency: transaksi/pending kalah sudah di-rollback; pakai pemenang.
            if (transaksiRepo.existsBySekolahIdAndIdempotencyKey(sekolahId, key)) {
                return txTemplate.execute(status -> bangunReplay(sekolahId, key));
            }
            var pemenang = pendingRepo.findBySekolahIdAndIdempotencyKey(sekolahId, key);
            if (pemenang.isPresent()) {
                return txTemplate.execute(status -> bangunMenunggu(pemenang.get()));
            }
            throw e;
        }
    }

    /** Inti eksekusi — berjalan di dalam satu transaksi DB. */
    private TapResponse eksekusi(Long sekolahId, IdentitasKantin identitas, TapRequest request,
                                 String rfidUid) {
        Long petugasId = identitas.aktorIdWajib();
        Siap siap = siapkan(sekolahId, request, rfidUid);

        // Konfirmasi manual (PRD §6.1/§9.1): bila aktif, JANGAN potong apa pun —
        // simpan permintaan & minta petugas mengonfirmasi.
        //
        // Pengecualian (C.2a): pembayaran TUNAI tidak lewat pending. Tabel
        // `transaksi_tertunda` mensyaratkan kartu/siswa (NOT NULL) karena isinya
        // adalah "tap kartu yang menunggu persetujuan memotong saldo"; uang tunai
        // sudah diterima kasir saat itu juga, jadi tidak ada yang perlu ditahan.
        // Konsekuensinya tunai langsung tercatat — dicatat eksplisit di PR C.2a.
        if (!request.tunai() && pengaturan.ambil(sekolahId).isKonfirmasiManual()) {
            return buatPending(sekolahId, petugasId, request, siap, rfidUid);
        }

        return commit(sekolahId, petugasId, request, siap, rfidUid);
    }

    /** Hasil lookup kartu + validasi 6 tahap (read-only, belum mengubah state). */
    private Siap siapkan(Long sekolahId, TapRequest request, String rfidUid) {
        // 2) Lookup kartu (port). Status blokir diperiksa server tiap tap.
        InfoKartu kartu = kartuLookup.cariBerdasarkanUid(sekolahId, rfidUid);

        // 2b) Tempelkan kontrol kantin-be (blokir kartu/limit/blokir item) — data
        // milik kantin-be, dibaca tiap tap TANPA cache (PRD §11.11) sehingga
        // blokir yang baru disimpan langsung menolak tap berikutnya.
        kartu = kontrolKartu.terapkan(sekolahId, kartu);

        long saldo = 0L;
        long belanjaHariIni = 0L;
        if (kartu.isDikenal() && kartu.getSubjekTipe() != null && kartu.getSiswaId() != null) {
            saldo = ledgerSaldo.saldo(sekolahId, kartu.getSiswaId());
            if (kartu.siswa()) {
                belanjaHariIni = ledgerSaldo.belanjaHariIni(sekolahId, kartu.getSiswaId());
            }
        }

        // 3) Validasi 6 tahap.
        TapValidator.Hasil hasil = validator.validasi(
                sekolahId, request, kartu, saldo, belanjaHariIni,
                menuId -> menuLookup.cari(sekolahId, menuId));
        if (!hasil.valid()) {
            throw new ConflictException(hasil.getValidasi().getPesan());
        }
        return new Siap(kartu, hasil);
    }

    /** Nilai antara: kartu + hasil validasi (belum mengubah state). */
    private record Siap(InfoKartu kartu, TapValidator.Hasil hasil) {
    }

    /** Simpan tap sebagai pending (konfirmasi manual) tanpa memotong saldo/stok. */
    private TapResponse buatPending(Long sekolahId, Long petugasId, TapRequest request, Siap siap,
                                    String rfidUid) {
        OffsetDateTime now = jam.sekarang();
        Long pendingId = idGenerator.berikutnyaLong();

        TransaksiTertunda pending = TransaksiTertunda.builder()
                .id(pendingId)
                .sekolahId(sekolahId)
                .idempotencyKey(request.getIdempotencyKey())
                .rfidUid(rfidUid)
                .titikKasirId(request.getTitikKasirId())
                .siswaId(siap.kartu().getSiswaId())
                .petugasId(petugasId)
                .total(siap.hasil().getTotal())
                .itemsJson(tulisItems(request.getItems()))
                .status(StatusPendingTap.MENUNGGU)
                .izinkanStokMinus(Boolean.TRUE.equals(request.getIzinkanStokMinus()))
                .alasanStokMinus(request.getAlasanStokMinus())
                .dibuatAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
        pendingRepo.save(pending);

        log.info("Tap menunggu konfirmasi pendingId={} sekolah={} total={}",
                pendingId, sekolahId, siap.hasil().getTotal());
        return bangunMenunggu(pending);
    }

    /**
     * Konfirmasi tap pending (PRD §6.1) — eksekusi commit (potong saldo/stok).
     * <b>Idempoten</b>: pending yang sudah dikonfirmasi mengembalikan transaksi lama.
     *
     * <p>Item divalidasi ulang (stok/saldo bisa berubah antara tap &amp; konfirmasi).
     */
    public TapResponse konfirmasi(Long sekolahId, Long pendingId, IdentitasKantin identitas) {
        Long petugasId = identitas.aktorIdWajib();
        return txTemplate.execute(status -> {
            TransaksiTertunda pending = pendingRepo.kunciUntukUpdate(sekolahId, pendingId)
                    .orElseThrow(() -> new NotFoundEntity("Permintaan konfirmasi tidak ditemukan"));
            if (!pending.getSekolahId().equals(sekolahId)) {
                throw new NotFoundEntity("Permintaan konfirmasi tidak ditemukan");
            }
            if (pending.getStatus() == StatusPendingTap.DIKONFIRMASI) {
                // Idempoten: kembalikan transaksi yang sudah dibuat.
                return bangunReplay(sekolahId, pending.getIdempotencyKey());
            }
            if (pending.getStatus() == StatusPendingTap.DIBATALKAN) {
                throw new InvalidOperationException("Permintaan konfirmasi sudah dibatalkan");
            }

            TapRequest request = dariPending(pending);
            // UID dari baris pending sudah kanonik; normalisasi ulang idempoten
            // agar alur konfirmasi memakai jalur yang sama tanpa memutasi DTO.
            String rfidUid = UidKartu.normalisasi(request.getRfidUid());
            Siap siap = siapkan(sekolahId, request, rfidUid);
            TapResponse resp = commit(sekolahId, petugasId, request, siap, rfidUid);

            pending.setStatus(StatusPendingTap.DIKONFIRMASI);
            pending.setTransaksiId(resp.getTransaksiId());
            pending.setUpdatedAt(jam.sekarang());
            pendingRepo.save(pending);
            return resp;
        });
    }

    /** Batalkan tap pending (PRD §6.1) — tidak jadi transaksi. Idempoten. */
    public void batal(Long sekolahId, Long pendingId, IdentitasKantin identitas) {
        Long petugasId = identitas.aktorIdWajib();
        txTemplate.executeWithoutResult(status -> {
            TransaksiTertunda pending = pendingRepo.kunciUntukUpdate(sekolahId, pendingId)
                    .orElseThrow(() -> new NotFoundEntity("Permintaan konfirmasi tidak ditemukan"));
            if (!pending.getSekolahId().equals(sekolahId)) {
                throw new NotFoundEntity("Permintaan konfirmasi tidak ditemukan");
            }
            if (pending.getStatus() == StatusPendingTap.DIKONFIRMASI) {
                throw new InvalidOperationException("Permintaan sudah dikonfirmasi — tidak dapat dibatalkan");
            }
            if (pending.getStatus() == StatusPendingTap.DIBATALKAN) {
                return; // idempoten
            }
            pending.setStatus(StatusPendingTap.DIBATALKAN);
            pending.setUpdatedAt(jam.sekarang());
            pendingRepo.save(pending);
            log.info("Tap pending {} dibatalkan oleh={}", pendingId, petugasId);
        });
    }

    /** Daftar tap pending yang masih menunggu konfirmasi untuk satu sekolah. */
    public List<TransaksiTertunda> daftarPending(Long sekolahId) {
        return pendingRepo.findBySekolahIdAndStatusOrderByIdAsc(sekolahId, StatusPendingTap.MENUNGGU);
    }

    /** Bangun respons ringkas untuk baris pending (dipakai endpoint daftar menunggu). */
    public TapResponse keResponse(TransaksiTertunda pending) {
        return bangunMenunggu(pending);
    }

    /** Commit transaksi: sesi → stok → transaksi + item → debit saldo → notifikasi. */
    private TapResponse commit(Long sekolahId, Long petugasId, TapRequest request, Siap siap,
                               String rfidUid) {
        InfoKartu kartu = siap.kartu();
        TapValidator.Hasil hasil = siap.hasil();

        // Sesi kasir hari ini (buka bila belum ada).
        SesiKasir sesi = sesiKasir.sesiTerbukaAtauBuka(sekolahId, request.getTitikKasirId(), petugasId);

        OffsetDateTime now = jam.sekarang();
        Long transaksiId = idGenerator.berikutnyaLong();

        // Uang tunai wajib menutup total (C.2a). Diperiksa di commit — bukan di
        // validator — karena nominal final baru pasti setelah harga/stok dibaca,
        // dan commit juga dipanggil dari jalur konfirmasi pending.
        //
        // Pesan dibedakan: "belum diisi" bukan "kurang", supaya kasir tidak
        // mencari kekurangan uang yang sebenarnya belum diinput.
        boolean tunai = request.tunai();
        long uangDiterima = 0L;
        if (tunai) {
            if (request.getUangDiterima() == null) {
                throw new ConflictException("Uang diterima wajib diisi untuk pembayaran tunai");
            }
            uangDiterima = request.getUangDiterima();
            if (uangDiterima < hasil.getTotal()) {
                throw new ConflictException(
                        "Uang tunai kurang Rp " + (hasil.getTotal() - uangDiterima));
            }
        }

        // Nama pembeli hanya bermakna untuk tunai tanpa kartu (C.2a); untuk
        // pembayaran saldo identitas sudah ada di siswaId.
        String namaPembeli = tunai ? rapikanNama(request.getNamaPembeli()) : null;

        Transaksi trx = Transaksi.builder()
                .id(transaksiId)
                .idempotencyKey(request.getIdempotencyKey())
                .sekolahId(sekolahId)
                .sesiKasirId(sesi.getId())
                .titikKasirId(request.getTitikKasirId())
                .siswaId(kartu.getSiswaId())
                .metodeBayar(request.metodeBayarEfektif())
                .uangDiterima(tunai ? uangDiterima : null)
                .namaPembeli(namaPembeli)
                .kartuUid(rfidUid)
                .petugasId(petugasId)
                .total(hasil.getTotal())
                .totalHpp(0L)
                .status(StatusTransaksi.SUKSES)
                .waktu(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // Potong stok tiap item (FOR UPDATE) + snapshot HPP & harga.
        long totalHpp = 0L;
        List<String> namaItem = new ArrayList<>();
        for (TapValidator.Baris baris : hasil.getBaris()) {
            HasilMutasiStok mutasiStok = ledgerStok.keluarPenjualan(
                    sekolahId, baris.getMenuId(), baris.getQty(), transaksiId,
                    Boolean.TRUE.equals(request.getIzinkanStokMinus()),
                    request.getAlasanStokMinus(), petugasId);
            long hppSnapshot = mutasiStok.getHppSetelah();
            totalHpp += hppSnapshot * baris.getQty();

            TransaksiItem item = TransaksiItem.builder()
                    .id(idGenerator.berikutnyaLong())
                    .menuId(baris.getMenuId())
                    .namaMenu(baris.getMenu().getNama())
                    .kategoriId(baris.getMenu().getKategoriId())
                    .hargaJual(baris.getMenu().getHargaJual())
                    .qty(baris.getQty())
                    .hppSnapshot(hppSnapshot)
                    .subtotal(baris.getSubtotal())
                    .createdAt(now)
                    .build();
            trx.tambahItem(item);
            namaItem.add(baris.getMenu().getNama() + " x" + baris.getQty());
        }
        trx.setTotalHpp(totalHpp);

        // Simpan transaksi + item (cascade) SEBELUM debit, agar FK aman & baris ada.
        transaksiRepo.save(trx);

        // Debit saldo (FOR UPDATE) — atomik dengan langkah di atas. <b>Dilewati
        // untuk pembayaran tunai</b> (C.2a): uang sudah diterima kasir, saldo
        // siswa tidak boleh berubah walau kartunya terbaca.
        Long saldoSisa = null;
        if (!tunai) {
            var hasilDebit = ledgerSaldo.debit(PerintahMutasiSaldo.builder()
                    .sekolahId(sekolahId)
                    .siswaId(kartu.getSiswaId())
                    .jenis(JenisMutasiSaldo.PENJUALAN)
                    .nominal(hasil.getTotal())
                    .idempotencyKey(request.getIdempotencyKey())
                    .transaksiId(transaksiId)
                    .referensiTipe("TRANSAKSI")
                    .referensiId(String.valueOf(transaksiId))
                    .aktorId(petugasId)
                    .build());
            saldoSisa = hasilDebit.getSaldoSetelah();

            log.info("Tap sukses trx={} sekolah={} subjek={}/{} total={} saldoSisa={}",
                    transaksiId, sekolahId, kartu.getSubjekTipe(), kartu.getSiswaId(),
                    hasil.getTotal(), saldoSisa);
        } else {
            long kembalian = uangDiterima - hasil.getTotal();
            log.info("Tap TUNAI trx={} sekolah={} total={} uangDiterima={} kembalian={}",
                    transaksiId, sekolahId, hasil.getTotal(), uangDiterima, kembalian);
        }

        // Notifikasi ke ortu (PRD §8.4) — best-effort/fail-open: kegagalan
        // pengiriman TIDAK membatalkan tap yang sudah tercatat di ledger.
        // Hanya untuk transaksi SALDO: notifikasi memuat "saldo setelah" dan
        // tidak ada saldo yang berubah pada pembayaran tunai.
        if (!tunai && kartu.getSiswaId() != null) {
            notifikasi.kirim(PerintahNotifikasi.builder()
                    .sekolahId(sekolahId)
                    .jenis(JenisNotifikasi.BELANJA)
                    .subjekTipe(SubjekTipe.SISWA)
                    .siswaId(kartu.getSiswaId())
                    .nominal(hasil.getTotal())
                    .saldoSetelah(saldoSisa)
                    .referensiId(String.valueOf(transaksiId))
                    .ringkasan((kartu.getNama() == null ? "Siswa" : kartu.getNama())
                            + " belanja Rp" + hasil.getTotal() + " di kantin")
                    .aktorId(petugasId)
                    .waktu(now)
                    .build());
        }

        return TapResponse.builder()
                .transaksiId(transaksiId)
                .nama(kartu.getNama())
                .kelas(kartu.getKelas())
                .fotoUrl(kartu.getFotoUrl())
                .total(hasil.getTotal())
                .saldoSisa(saldoSisa)
                .namaItem(namaItem)
                .metode(MetodeRequestKartu.UID)
                .menungguKonfirmasi(false)
                // C.2a: cara bayar + uang/kembalian untuk struk tunai.
                .metodeBayar(request.metodeBayarEfektif())
                .uangDiterima(tunai ? uangDiterima : null)
                .kembalian(tunai ? uangDiterima - hasil.getTotal() : null)
                .namaPembeli(namaPembeli)
                // Data struk: titik kasir + nomor + waktu (dipakai layar kasir).
                .titikKasir(namaTitikKasir(sekolahId, request.getTitikKasirId()))
                .nomorStruk(NomorStruk.dari(transaksiId, now))
                .waktu(waktuStruk(now))
                .build();
    }

    /** Waktu untuk struk: detik saja, tanpa pecahan nanodetik. */
    private static OffsetDateTime waktuStruk(OffsetDateTime waktu) {
        return waktu == null ? null : waktu.truncatedTo(ChronoUnit.SECONDS);
    }

    /** Trim nama pembeli; kosong/blank dianggap tidak diisi (null). */
    private static String rapikanNama(String nama) {
        if (nama == null) {
            return null;
        }
        String rapi = nama.trim();
        return rapi.isEmpty() ? null : rapi;
    }

    /** Nama titik kasir (null bila id kosong/tidak ada di sekolah ini). */
    private String namaTitikKasir(Long sekolahId, Long titikKasirId) {
        if (titikKasirId == null || sekolahId == null) {
            return null;
        }
        return titikKasirRepo.findByIdAndSekolahId(titikKasirId, sekolahId)
                .map(com.asqi.scholia_kantin_be.model.TitikKasir::getNama)
                .orElse(null);
    }

    /** Respons "menunggu konfirmasi" (belum memotong saldo/stok). */
    private TapResponse bangunMenunggu(TransaksiTertunda pending) {
        InfoKartu kartu = pending.getRfidUid() == null ? null
                : kartuLookup.cariBerdasarkanUid(pending.getSekolahId(), pending.getRfidUid());
        return TapResponse.builder()
                .transaksiId(null)
                .nama(kartu == null ? null : kartu.getNama())
                .kelas(kartu == null ? null : kartu.getKelas())
                .fotoUrl(kartu == null ? null : kartu.getFotoUrl())
                .total(pending.getTotal())
                .saldoSisa(null)
                .namaItem(null)
                .metode(MetodeRequestKartu.UID)
                .menungguKonfirmasi(true)
                .pendingId(pending.getId())
                .build();
    }

    /**
     * Bangun respons untuk permintaan idempoten (tidak membuat transaksi baru).
     * Dipanggil <b>di dalam</b> transaksi agar relasi {@code items} (lazy) bisa
     * dimuat untuk menampilkan rincian item.
     */
    private TapResponse bangunReplay(Long sekolahId, String idempotencyKey) {
        Transaksi trx = transaksiRepo.findBySekolahIdAndIdempotencyKey(sekolahId, idempotencyKey)
                .orElseThrow(() -> new InvalidOperationException(
                        "Transaksi idempoten tidak ditemukan: " + idempotencyKey));

        // Tenant scoping: key dibuat klien, jadi transaksi yang ditemukan WAJIB
        // milik sekolah pemanggil. Bila bukan → 404 (jangan bocorkan data
        // sekolah lain lewat jalur idempotency — PRD §11.4).
        if (!sekolahId.equals(trx.getSekolahId())) {
            throw new com.asqi.scholia_kantin_be.component.exception.NotFoundEntity(
                    "Transaksi tidak ditemukan");
        }

        // Saldo hanya bermakna untuk pembayaran SALDO; tunai tidak menyentuh ledger
        // sehingga saldoSisa dibiarkan 0 (bukan saldo siswa setelah transaksi).
        boolean tunai = trx.getMetodeBayar() == MetodeBayar.TUNAI;
        long saldoSisa = 0L;
        if (!tunai && trx.getSiswaId() != null) {
            saldoSisa = ledgerSaldo.saldo(sekolahId, trx.getSiswaId());
        }
        InfoKartu kartu = trx.getKartuUid() == null ? null
                : kartuLookup.cariBerdasarkanUid(sekolahId, trx.getKartuUid());

        List<String> namaItem = new ArrayList<>();
        for (TransaksiItem item : trx.getItems()) {
            namaItem.add(item.getNamaMenu() + " x" + item.getQty());
        }

        return TapResponse.builder()
                .transaksiId(trx.getId())
                .nama(kartu == null ? null : kartu.getNama())
                .kelas(kartu == null ? null : kartu.getKelas())
                .fotoUrl(kartu == null ? null : kartu.getFotoUrl())
                .total(trx.getTotal())
                .saldoSisa(saldoSisa)
                .namaItem(namaItem)
                .metode(MetodeRequestKartu.UID)
                .menungguKonfirmasi(false)
                // C.2a: cara bayar + uang/kembalian (kembalian dihitung ulang dari
                // uang_diterima yang tersimpan, bukan dari state kasir saat ini).
                .metodeBayar(trx.getMetodeBayar() == null ? MetodeBayar.SALDO : trx.getMetodeBayar())
                .uangDiterima(tunai ? trx.getUangDiterima() : null)
                .kembalian(tunai && trx.getUangDiterima() != null
                        ? trx.getUangDiterima() - trx.getTotal() : null)
                .namaPembeli(tunai ? trx.getNamaPembeli() : null)
                // Data struk: titik kasir + nomor + waktu (dipakai layar kasir).
                .titikKasir(namaTitikKasir(sekolahId, trx.getTitikKasirId()))
                .nomorStruk(NomorStruk.dari(trx.getId(), trx.getWaktu()))
                .waktu(waktuStruk(trx.getWaktu()))
                .build();
    }

    /** Serialisasi item tap ke JSON untuk disimpan di baris pending. */
    private String tulisItems(List<TapRequest.ItemTap> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            throw new IllegalStateException("Gagal menyimpan item tap pending", e);
        }
    }

    /** Rekonstruksi permintaan tap dari baris pending (untuk konfirmasi). */
    private TapRequest dariPending(TransaksiTertunda pending) {
        TapRequest request = new TapRequest();
        request.setRfidUid(pending.getRfidUid());
        request.setTitikKasirId(pending.getTitikKasirId());
        request.setIdempotencyKey(pending.getIdempotencyKey());
        request.setIzinkanStokMinus(pending.isIzinkanStokMinus());
        request.setAlasanStokMinus(pending.getAlasanStokMinus());
        try {
            request.setItems(objectMapper.readValue(pending.getItemsJson(),
                    new TypeReference<List<TapRequest.ItemTap>>() { }));
        } catch (Exception e) {
            throw new IllegalStateException("Gagal membaca item tap pending", e);
        }
        return request;
    }
}
