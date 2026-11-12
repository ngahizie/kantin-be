package com.asqi.scholia_kantin_be.service.integrasi;

import com.asqi.scholia_kantin_be.model.Kartu;
import com.asqi.scholia_kantin_be.model.OrangTua;
import com.asqi.scholia_kantin_be.model.Siswa;
import com.asqi.scholia_kantin_be.repository.KartuRepository;
import com.asqi.scholia_kantin_be.repository.OrangTuaRepository;
import com.asqi.scholia_kantin_be.repository.SiswaRepository;
import com.asqi.scholia_kantin_be.service.kasir.LedgerSaldoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implementasi {@link CariSiswaPort} yang membaca <b>tabel lokal skema v2</b>
 * ({@code siswa}, {@code orang_tua}, {@code kartu}).
 *
 * <p><b>Kenapa ada.</b> Di v1 master siswa hanya milik admin-be sehingga
 * kantin-be memakai {@code CariSiswaDemo} (data tiruan) atau REST ke admin-be.
 * ERD v2 menaikkan siswa + wali + kartu menjadi tabel nyata di kantin-be, jadi
 * identitas bisa dibaca langsung dari DB: nama, NIS, kelas, foto, dan nama
 * orang tua tampil tanpa menunggu kontrak integrasi (Q7).
 *
 * <p><b>Kapan aktif.</b> Default aktif ({@code kantin.siswa.sumber=lokal});
 * matikan dengan {@code kantin.siswa.sumber=integrasi} bila admin-be sudah
 * menjadi sumber tunggal. {@code @Primary} agar menang atas
 * {@link CariSiswaFallback}; {@code CariSiswaDemo} harus dinonaktifkan
 * ({@code kantin.demo.enabled=false}) supaya tidak ada dua bean utama.
 *
 * <p><b>Fail-open (PRD §8.4).</b> Siswa/kartu tidak ditemukan → hasil kosong,
 * bukan error: layar kasir &amp; riwayat tetap berjalan dengan identitas null.
 * Saldo selalu dari ledger lokal kantin-be ({@link LedgerSaldoService}), bukan
 * dari tabel {@code saldo} yang hanya cache.
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "kantin.siswa", name = "sumber",
        havingValue = "lokal", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class CariSiswaLokal implements CariSiswaPort {

    private final SiswaRepository siswaRepo;
    private final OrangTuaRepository orangTuaRepo;
    private final KartuRepository kartuRepo;
    private final LedgerSaldoService ledgerSaldo;

    @Override
    public List<InfoSiswa> cari(Long sekolahId, String kataKunci, boolean hanyaTidakAktif, int batas) {
        String kata = kataKunci == null ? "" : kataKunci.trim();
        Boolean aktif = hanyaTidakAktif ? Boolean.FALSE : Boolean.TRUE;
        int batasAman = Math.min(Math.max(batas, 1), 200);

        List<Siswa> siswa = siswaRepo.cari(sekolahId, kata, aktif, PageRequest.of(0, batasAman));
        if (siswa.isEmpty()) {
            return List.of();
        }
        Map<Long, OrangTua> wali = waliUtama(sekolahId,
                siswa.stream().map(Siswa::getId).toList());

        return siswa.stream()
                .map(s -> keInfo(s, wali.get(s.getId())))
                .toList();
    }

    @Override
    public Optional<InfoSiswa> cariBerdasarkanId(Long sekolahId, Long siswaId) {
        if (siswaId == null) {
            return Optional.empty();
        }
        return siswaRepo.findByIdAndSekolahId(siswaId, sekolahId)
                .map(s -> keInfo(s, waliUtama(sekolahId, List.of(siswaId)).get(siswaId)));
    }

    @Override
    public Map<Long, InfoSiswa> cariBanyak(Long sekolahId, Collection<Long> siswaIds) {
        if (siswaIds == null || siswaIds.isEmpty()) {
            return Map.of();
        }
        List<Siswa> siswa = siswaRepo.findByIdInAndSekolahId(siswaIds, sekolahId);
        if (siswa.isEmpty()) {
            return Map.of();
        }
        Map<Long, OrangTua> wali = waliUtama(sekolahId,
                siswa.stream().map(Siswa::getId).toList());
        Map<Long, InfoSiswa> hasil = new HashMap<>();
        siswa.forEach(s -> hasil.put(s.getId(), keInfo(s, wali.get(s.getId()))));
        return hasil;
    }

    @Override
    public Optional<String> cariUidSiswa(Long sekolahId, Long siswaId) {
        if (siswaId == null) {
            return Optional.empty();
        }
        return kartuRepo.findFirstBySekolahIdAndSiswaIdAndAktifTrue(sekolahId, siswaId)
                .map(Kartu::getUid);
    }

    /** Wali utama per siswa: utamakan {@code AYAH}, lalu {@code IBU}, lalu wali lain. */
    private Map<Long, OrangTua> waliUtama(Long sekolahId, Collection<Long> siswaIds) {
        List<OrangTua> semua = orangTuaRepo.findBySiswaIdInAndSekolahId(siswaIds, sekolahId);
        return semua.stream()
                .filter(OrangTua::isAktif)
                .collect(Collectors.toMap(
                        OrangTua::getSiswaId,
                        Function.identity(),
                        (a, b) -> prioritas(a) <= prioritas(b) ? a : b));
    }

    private int prioritas(OrangTua o) {
        String h = o.getHubungan() == null ? "" : o.getHubungan().toUpperCase();
        return switch (h) {
            case "AYAH" -> 0;
            case "IBU" -> 1;
            default -> 2;
        };
    }

    private InfoSiswa keInfo(Siswa s, OrangTua wali) {
        return InfoSiswa.builder()
                .siswaId(s.getId())
                .nis(s.getNis())
                .nama(s.getNama())
                .kelas(s.getKelas())
                .saldo(ledgerSaldo.saldo(s.getSekolahId(), s.getId()))
                .tidakAktif(!s.isAktif())
                .fotoUrl(s.getFotoUrl())
                .namaOrangTua(wali == null ? null : wali.getNama())
                .teleponOrangTua(wali == null ? null : wali.getTelepon())
                .build();
    }
}
