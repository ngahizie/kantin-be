package com.asqi.scholia_kantin_be.service.laporan;

import com.asqi.scholia_kantin_be.dto.RekapSetoranItem;
import com.asqi.scholia_kantin_be.service.saldo.SetoranService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Laporan setoran kas TU (PRD §9.2 &amp; §9.5, issue #143).
 *
 * <p>Sumber kebenaran rekap = {@link SetoranService#rekap} (ledger
 * {@code TOPUP_TUNAI} + {@code setoran_tu} append-only), sehingga laporan
 * modul laporan &amp; endpoint operasional selalu konsisten.
 *
 * <p>Tenant-scoped; baca-saja. {@code tanggal} {@code null} = hari ini
 * (zona kantin). Akses PRD §9.5: <b>Bendahara</b> (dipetakan ke
 * {@code TU_SEKOLAH}) — kepsek <b>tidak</b> berhak.
 */
@Service
@RequiredArgsConstructor
public class LaporanSetoranService {

    private final SetoranService setoranTuService;

    /**
     * Laporan setoran kas TU per tanggal.
     *
     * @param sekolahId tenant pemanggil
     * @param tanggal   tanggal acuan; {@code null} = hari ini (zona kantin)
     * @return rekap setoran per petugas
     */
    @Transactional(readOnly = true)
    public List<RekapSetoranItem> laporanSetoran(Long sekolahId, LocalDate tanggal) {
        return setoranTuService.rekap(sekolahId, tanggal);
    }

    /**
     * Laporan setoran kas TU dengan dukungan rentang tanggal opsional
     * {@code [dari, sampai)}.
     *
     * @param sekolahId tenant pemanggil
     * @param tanggal   tanggal acuan; {@code null} = hari ini (zona kantin)
     * @param dari      awal rentang (opsional)
     * @param sampai    akhir rentang (opsional)
     * @return rekap setoran per petugas
     */
    @Transactional(readOnly = true)
    public List<RekapSetoranItem> laporanSetoran(Long sekolahId, LocalDate tanggal,
                                                 OffsetDateTime dari, OffsetDateTime sampai) {
        return setoranTuService.rekap(sekolahId, tanggal, dari, sampai);
    }
}