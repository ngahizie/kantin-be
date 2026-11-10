package com.asqi.scholia_kantin_be.service.kasir;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import com.asqi.scholia_kantin_be.dto.CekKartuResponse;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.service.integrasi.InfoKartu;
import com.asqi.scholia_kantin_be.service.integrasi.KartuLookupPort;
import com.asqi.scholia_kantin_be.service.kartu.KontrolKartuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Baca identitas kartu untuk layar kasir — <b>tanpa</b> memotong saldo,
 * tanpa transaksi, tanpa efek samping.
 *
 * <p>Alasannya: kasir perlu melihat <i>milik siapa</i> kartu yang dibaca
 * sebelum uang dipotong. Kalau identitas baru muncul setelah pembayaran,
 * kartu tertukar sudah terlanjur dibayar.
 *
 * <p>Blokir kartu &amp; limit dibaca ulang dari kantin-be setiap panggilan
 * (tanpa cache) supaya blokir yang baru disimpan langsung terlihat — sama
 * seperti perlakuan pada {@link TapService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CekKartuService {

    private final KartuLookupPort kartuLookup;
    private final KontrolKartuService kontrolKartu;
    private final LedgerSaldoService ledgerSaldo;
    private final JamKantin jam;

    /**
     * Identitas + saldo pemilik kartu.
     *
     * @param sekolahId tenant pemanggil (dari token, bukan dari klien)
     * @param rfidUid   UID kartu hasil pembacaan
     * @throws InvalidOperationException UID kosong
     * @throws NotFoundEntity            kartu tidak terdaftar di sekolah ini
     */
    @Transactional(readOnly = true)
    public CekKartuResponse cek(Long sekolahId, String rfidUid) {
        String uid = rfidUid == null ? "" : rfidUid.trim();
        if (uid.isEmpty()) {
            throw new InvalidOperationException("UID kartu wajib diisi");
        }

        InfoKartu kartu = kontrolKartu.terapkan(sekolahId, kartuLookup.cariBerdasarkanUid(sekolahId, uid));
        if (!kartu.isDikenal()) {
            throw new NotFoundEntity("Kartu tidak dikenali");
        }

        long saldo = 0L;
        long belanjaHariIni = 0L;
        if (kartu.getSubjekTipe() != null && kartu.getSubjekId() != null) {
            saldo = ledgerSaldo.saldo(sekolahId, kartu.getSubjekTipe(), kartu.getSubjekId());
            if (kartu.siswa()) {
                belanjaHariIni = ledgerSaldo.belanjaHariIni(
                        sekolahId, kartu.getSubjekTipe(), kartu.getSubjekId());
            }
        }

        Long limitHarian = kartu.getLimitHarian();
        Long sisaLimit = limitHarian == null ? null : Math.max(0L, limitHarian - belanjaHariIni);
        boolean bisaBelanja = !kartu.isDiblokir()
                && saldo > 0
                && (sisaLimit == null || sisaLimit > 0);

        return new CekKartuResponse(
                uid.toUpperCase(),
                kartu.getSubjekTipe(),
                kartu.getSubjekId(),
                kartu.getNama(),
                kartu.getKelas(),
                kartu.getFotoUrl(),
                saldo,
                belanjaHariIni,
                limitHarian,
                sisaLimit,
                kartu.isDiblokir(),
                bisaBelanja,
                jam.sekarang());
    }
}
