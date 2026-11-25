package com.asqi.scholia_kantin_be.service.siswa;

import com.asqi.scholia_kantin_be.component.exception.ConflictException;
import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import com.asqi.scholia_kantin_be.component.logging.AuditLogger;
import com.asqi.scholia_kantin_be.dto.siswa.PasangKartuSiswaRequest;
import com.asqi.scholia_kantin_be.dto.siswa.SimpanSiswaAdminRequest;
import com.asqi.scholia_kantin_be.dto.siswa.SiswaManajemenResponse;
import com.asqi.scholia_kantin_be.helper.IdGenerator;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.helper.UidKartu;
import com.asqi.scholia_kantin_be.model.Kartu;
import com.asqi.scholia_kantin_be.model.OrangTua;
import com.asqi.scholia_kantin_be.model.Saldo;
import com.asqi.scholia_kantin_be.model.Siswa;
import com.asqi.scholia_kantin_be.repository.kartu.KartuRepository;
import com.asqi.scholia_kantin_be.repository.siswa.OrangTuaRepository;
import com.asqi.scholia_kantin_be.repository.saldo.SaldoRepository;
import com.asqi.scholia_kantin_be.repository.siswa.SiswaRepository;
import com.asqi.scholia_kantin_be.service.katalog.KatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manajemen sementara data Siswa, Wali, &amp; Kartu RFID khusus Admin Sekolah
 * sebelum integrasi penuh dengan layanan master {@code admin-be}.
 *
 * <p><b>Invarian akuntansi:</b> siswa baru selalu mulai dengan saldo {@code Rp 0}
 * tanpa menulis baris {@code mutasi_saldo} atau {@code saldo}. Top-up tetap wajib
 * melalui alur kas/TU resmi agar {@code Topup - Refund = Mengendap + Bersih} terjaga.
 */
@Service
@RequiredArgsConstructor
public class SiswaManajemenService {

    private static final int BATAS_DEFAULT = 100;
    private static final int BATAS_MAKSIMAL = 500;
    private static final Set<String> HUBUNGAN_SAH = Set.of("AYAH", "IBU", "WALI");

    private final SiswaRepository siswaRepo;
    private final KartuRepository kartuRepo;
    private final OrangTuaRepository orangTuaRepo;
    private final SaldoRepository saldoRepo;
    private final AuditLogger auditLogger;
    private final IdGenerator idGenerator;
    private final JamKantin jam;

    @Value("${minio.url-final:}")
    private String urlFinal;

    @Transactional(readOnly = true)
    public List<SiswaManajemenResponse> daftar(Long sekolahId, String q, Boolean aktif,
                                               Boolean punyaKartu, int limit) {
        int batas = limit <= 0 ? BATAS_DEFAULT : Math.min(limit, BATAS_MAKSIMAL);
        int batasQuery = punyaKartu == null ? batas : BATAS_MAKSIMAL;
        String kata = q == null ? "" : q.trim();

        List<Siswa> kandidat = siswaRepo.cariAdmin(sekolahId, kata, aktif, PageRequest.of(0, batasQuery));
        if (kandidat.isEmpty()) {
            return List.of();
        }

        List<Long> siswaIds = kandidat.stream().map(Siswa::getId).toList();
        Map<Long, Kartu> kartuAktifMap = petakanKartuAktif(sekolahId, siswaIds);
        Map<Long, OrangTua> waliMap = petakanWaliUtama(sekolahId, siswaIds);
        Map<Long, Long> saldoMap = petakanSaldo(sekolahId, siswaIds);

        return kandidat.stream()
                .filter(s -> {
                    if (punyaKartu == null) {
                        return true;
                    }
                    boolean adaKartu = kartuAktifMap.containsKey(s.getId());
                    return punyaKartu == adaKartu;
                })
                .limit(batas)
                .map(s -> susunResponse(
                        s,
                        kartuAktifMap.get(s.getId()),
                        waliMap.get(s.getId()),
                        saldoMap.getOrDefault(s.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public SiswaManajemenResponse detail(Long sekolahId, Long siswaId) {
        Siswa siswa = cariSiswaWajib(sekolahId, siswaId);
        return rakitDetail(sekolahId, siswa);
    }

    @Transactional
    public SiswaManajemenResponse buat(Long sekolahId, SimpanSiswaAdminRequest req, Long aktorId) {
        String nis = trimWajib(req.getNis(), "NIS wajib diisi");
        String nama = trimWajib(req.getNama(), "Nama siswa wajib diisi");
        String nisn = trimOpsional(req.getNisn());
        String kelas = trimOpsional(req.getKelas());
        String fotoUrl = trimOpsional(req.getFotoUrl());
        String uidNorm = normalisasiUidOpsional(req.getRfidUid());
        String nomorKartu = trimOpsional(req.getNomorKartu());

        KatalogService.validasiFotoUrlMilikTenant(sekolahId, fotoUrl, urlFinal);

        if (siswaRepo.existsBySekolahIdAndNis(sekolahId, nis)) {
            throw new ConflictException("NIS sudah terdaftar pada sekolah ini");
        }
        if (uidNorm != null) {
            validasiKelayakanKartu(sekolahId, null, uidNorm, nomorKartu);
        } else if (nomorKartu != null) {
            throw new InvalidOperationException("UID RFID wajib diisi jika nomor kartu disertakan");
        }

        OffsetDateTime now = jam.sekarang();
        Siswa siswa = Siswa.builder()
                .id(idGenerator.berikutnyaLong())
                .baru(true)
                .sekolahId(sekolahId)
                .nis(nis)
                .nisn(nisn)
                .nama(nama)
                .kelas(kelas)
                .fotoUrl(fotoUrl)
                .aktif(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
        siswaRepo.save(siswa);

        simpanAtauPerbaruiWali(sekolahId, siswa.getId(), req, now);

        if (uidNorm != null) {
            terapkanKartuBaru(sekolahId, siswa, uidNorm, nomorKartu, now);
        }

        auditLogger.catat(
                aktorId,
                sekolahId,
                "TAMBAH_SISWA_SEMENTARA",
                "Siswa",
                String.valueOf(siswa.getId()),
                "Pendaftaran siswa sementara oleh Admin Sekolah",
                null,
                ringkasSiswa(siswa, uidNorm));

        return rakitDetail(sekolahId, siswa);
    }

    @Transactional
    public SiswaManajemenResponse perbarui(Long sekolahId, Long siswaId,
                                           SimpanSiswaAdminRequest req, Long aktorId) {
        Siswa siswa = cariSiswaWajib(sekolahId, siswaId);
        String sebelum = ringkasSiswa(siswa,
                kartuRepo.findFirstBySekolahIdAndSiswaIdAndAktifTrue(sekolahId, siswaId)
                        .map(Kartu::getUid).orElse(null));

        String nis = trimWajib(req.getNis(), "NIS wajib diisi");
        String nama = trimWajib(req.getNama(), "Nama siswa wajib diisi");
        String nisn = trimOpsional(req.getNisn());
        String kelas = trimOpsional(req.getKelas());
        String fotoUrl = trimOpsional(req.getFotoUrl());
        String uidNorm = normalisasiUidOpsional(req.getRfidUid());
        String nomorKartu = trimOpsional(req.getNomorKartu());

        KatalogService.validasiFotoUrlMilikTenant(sekolahId, fotoUrl, urlFinal);

        if (siswaRepo.existsBySekolahIdAndNisAndIdNot(sekolahId, nis, siswaId)) {
            throw new ConflictException("NIS sudah digunakan oleh siswa lain");
        }
        if (uidNorm == null && nomorKartu != null) {
            throw new InvalidOperationException("UID RFID wajib diisi jika nomor kartu disertakan");
        }

        OffsetDateTime now = jam.sekarang();
        siswa.setNis(nis);
        siswa.setNisn(nisn);
        siswa.setNama(nama);
        siswa.setKelas(kelas);
        siswa.setFotoUrl(fotoUrl);
        siswa.setUpdatedAt(now);
        siswaRepo.save(siswa);

        simpanAtauPerbaruiWali(sekolahId, siswaId, req, now);

        if (uidNorm != null) {
            Kartu aktifSekarang = kartuRepo
                    .findFirstBySekolahIdAndSiswaIdAndAktifTrue(sekolahId, siswaId)
                    .orElse(null);
            boolean kartuSama = aktifSekarang != null
                    && uidNorm.equalsIgnoreCase(aktifSekarang.getUid())
                    && Objects.equals(nomorKartu, aktifSekarang.getNomorKartu());
            if (!kartuSama) {
                validasiKelayakanKartu(sekolahId, siswaId, uidNorm, nomorKartu);
                terapkanKartuBaru(sekolahId, siswa, uidNorm, nomorKartu, now);
            }
        }

        auditLogger.catat(
                aktorId,
                sekolahId,
                "UBAH_SISWA_SEMENTARA",
                "Siswa",
                String.valueOf(siswaId),
                "Pembaruan profil siswa sementara oleh Admin Sekolah",
                sebelum,
                ringkasSiswa(siswa, uidNorm));

        return rakitDetail(sekolahId, siswa);
    }

    @Transactional
    public SiswaManajemenResponse aturKartu(Long sekolahId, Long siswaId,
                                            PasangKartuSiswaRequest req, Long aktorId) {
        Siswa siswa = cariSiswaWajib(sekolahId, siswaId);
        OffsetDateTime now = jam.sekarang();
        String uidLama = kartuRepo.findFirstBySekolahIdAndSiswaIdAndAktifTrue(sekolahId, siswaId)
                .map(Kartu::getUid).orElse(null);

        if (Boolean.TRUE.equals(req.getNonaktifkanKartu())) {
            nonaktifkanSemuaKartuSiswa(sekolahId, siswaId, null);
            siswa.setUpdatedAt(now);
            siswaRepo.save(siswa);
            auditLogger.catat(
                    aktorId,
                    sekolahId,
                    "LEPAS_KARTU_SISWA",
                    "Kartu",
                    String.valueOf(siswaId),
                    "Penonaktifan kartu RFID siswa oleh Admin Sekolah",
                    uidLama,
                    null);
            return rakitDetail(sekolahId, siswa);
        }

        String uidNorm = normalisasiUidOpsional(req.getRfidUid());
        if (uidNorm == null) {
            throw new InvalidOperationException("UID RFID wajib diisi");
        }
        String nomorKartu = trimOpsional(req.getNomorKartu());
        validasiKelayakanKartu(sekolahId, siswaId, uidNorm, nomorKartu);
        terapkanKartuBaru(sekolahId, siswa, uidNorm, nomorKartu, now);
        siswa.setUpdatedAt(now);
        siswaRepo.save(siswa);

        auditLogger.catat(
                aktorId,
                sekolahId,
                "PASANG_KARTU_SISWA",
                "Kartu",
                String.valueOf(siswaId),
                "Pemasangan atau penggantian kartu RFID siswa oleh Admin Sekolah",
                uidLama,
                uidNorm);

        return rakitDetail(sekolahId, siswa);
    }

    @Transactional
    public SiswaManajemenResponse ubahStatus(Long sekolahId, Long siswaId,
                                             boolean aktif, Long aktorId) {
        Siswa siswa = cariSiswaWajib(sekolahId, siswaId);
        boolean statusLama = siswa.isAktif();
        siswa.setAktif(aktif);
        siswa.setUpdatedAt(jam.sekarang());
        siswaRepo.save(siswa);

        auditLogger.catat(
                aktorId,
                sekolahId,
                "UBAH_STATUS_SISWA",
                "Siswa",
                String.valueOf(siswaId),
                aktif ? "Aktivasi siswa sementara" : "Nonaktivasi siswa sementara",
                String.valueOf(statusLama),
                String.valueOf(aktif));

        return rakitDetail(sekolahId, siswa);
    }

    // ────────────────────────────────────────────────────────────────
    // HELPER INTERNAL
    // ────────────────────────────────────────────────────────────────

    private Siswa cariSiswaWajib(Long sekolahId, Long siswaId) {
        return siswaRepo.findByIdAndSekolahId(siswaId, sekolahId)
                .orElseThrow(() -> new NotFoundEntity("Siswa tidak ditemukan"));
    }

    private void validasiKelayakanKartu(Long sekolahId, Long siswaIdPemilik,
                                        String uidNorm, String nomorKartu) {
        Optional<Kartu> kartuAda = kartuRepo.findByUidIgnoreCase(uidNorm);
        if (kartuAda.isPresent()) {
            Kartu k = kartuAda.get();
            if (!Objects.equals(k.getSekolahId(), sekolahId)) {
                throw new ConflictException("UID RFID sudah terdaftar pada sekolah lain");
            }
            if (siswaIdPemilik == null || !Objects.equals(k.getSiswaId(), siswaIdPemilik)) {
                throw new ConflictException("UID RFID sudah terdaftar pada siswa lain");
            }
        }
        if (nomorKartu != null) {
            boolean samaDenganKartuIni = kartuAda.isPresent()
                    && Objects.equals(kartuAda.get().getSekolahId(), sekolahId)
                    && Objects.equals(kartuAda.get().getNomorKartu(), nomorKartu);
            if (!samaDenganKartuIni && kartuRepo.existsBySekolahIdAndNomorKartu(sekolahId, nomorKartu)) {
                throw new ConflictException("Nomor kartu sudah digunakan oleh kartu lain");
            }
        }
    }

    private void terapkanKartuBaru(Long sekolahId, Siswa siswa, String uidNorm,
                                   String nomorKartu, OffsetDateTime now) {
        nonaktifkanSemuaKartuSiswa(sekolahId, siswa.getId(), uidNorm);

        Optional<Kartu> kartuMilikSiswa = kartuRepo.findByUidIgnoreCase(uidNorm)
                .filter(k -> Objects.equals(k.getSekolahId(), sekolahId)
                        && Objects.equals(k.getSiswaId(), siswa.getId()));

        if (kartuMilikSiswa.isPresent()) {
            Kartu lama = kartuMilikSiswa.get();
            lama.setNomorKartu(nomorKartu);
            lama.setAktif(true);
            kartuRepo.save(lama);
            return;
        }

        Kartu baru = Kartu.builder()
                .uid(uidNorm)
                .sekolahId(sekolahId)
                .siswaId(siswa.getId())
                .nomorKartu(nomorKartu)
                .aktif(true)
                .createdAt(now)
                .build();
        kartuRepo.save(baru);
    }

    private void nonaktifkanSemuaKartuSiswa(Long sekolahId, Long siswaId, String kecualiUid) {
        List<Kartu> kartuSiswa = kartuRepo.findBySekolahIdAndSiswaIdOrderByCreatedAtDesc(sekolahId, siswaId);
        List<Kartu> perluNonaktif = kartuSiswa.stream()
                .filter(Kartu::isAktif)
                .filter(k -> kecualiUid == null || !kecualiUid.equalsIgnoreCase(k.getUid()))
                .toList();
        if (!perluNonaktif.isEmpty()) {
            perluNonaktif.forEach(k -> k.setAktif(false));
            kartuRepo.saveAll(perluNonaktif);
            // Wajib flush sebelum insert/reaktivasi kartu baru agar ux_kartu_aktif_per_siswa tidak bentrok
            kartuRepo.flush();
        }
    }

    private void simpanAtauPerbaruiWali(Long sekolahId, Long siswaId,
                                        SimpanSiswaAdminRequest req, OffsetDateTime now) {
        String namaWali = trimOpsional(req.getNamaOrangTua());
        String teleponWali = trimOpsional(req.getTeleponOrangTua());
        String hubunganInput = trimOpsional(req.getHubunganOrangTua());

        if (namaWali == null && teleponWali == null) {
            if (hubunganInput != null) {
                throw new InvalidOperationException("Nama orang tua/wali wajib diisi jika hubungan dipilih");
            }
            List<OrangTua> waliAktif = orangTuaRepo.findBySiswaIdAndSekolahId(siswaId, sekolahId).stream()
                    .filter(OrangTua::isAktif)
                    .toList();
            if (!waliAktif.isEmpty()) {
                waliAktif.forEach(w -> w.setAktif(false));
                orangTuaRepo.saveAll(waliAktif);
            }
            return;
        }
        if (namaWali == null) {
            throw new InvalidOperationException("Nama orang tua/wali wajib diisi jika nomor telepon diisi");
        }
        String hubungan = normalisasiHubungan(hubunganInput);

        List<OrangTua> daftarWali = orangTuaRepo.findBySiswaIdAndSekolahId(siswaId, sekolahId);
        OrangTua target = pilihWaliUtama(daftarWali);
        if (target != null) {
            target.setNama(namaWali);
            target.setTelepon(teleponWali);
            target.setHubungan(hubungan);
            target.setAktif(true);
            orangTuaRepo.save(target);
            return;
        }

        OrangTua baru = OrangTua.builder()
                .sekolahId(sekolahId)
                .siswaId(siswaId)
                .nama(namaWali)
                .telepon(teleponWali)
                .hubungan(hubungan)
                .punyaAplikasi(false)
                .aktif(true)
                .createdAt(now)
                .build();
        orangTuaRepo.save(baru);
    }

    private SiswaManajemenResponse rakitDetail(Long sekolahId, Siswa siswa) {
        Kartu kartuAktif = kartuRepo
                .findFirstBySekolahIdAndSiswaIdAndAktifTrue(sekolahId, siswa.getId())
                .orElse(null);
        OrangTua wali = pilihWaliUtama(orangTuaRepo.findBySiswaIdAndSekolahId(siswa.getId(), sekolahId));
        long saldo = saldoRepo.findBySekolahIdAndSiswaId(sekolahId, siswa.getId())
                .map(s -> s.getSaldo() == null ? 0L : s.getSaldo())
                .orElse(0L);
        return susunResponse(siswa, kartuAktif, wali, saldo);
    }

    private Map<Long, Kartu> petakanKartuAktif(Long sekolahId, List<Long> siswaIds) {
        return kartuRepo.findBySekolahIdAndSiswaIdInAndAktifTrue(sekolahId, siswaIds).stream()
                .collect(Collectors.toMap(
                        Kartu::getSiswaId,
                        k -> k,
                        (lama, baru) -> lama,
                        LinkedHashMap::new));
    }

    private Map<Long, OrangTua> petakanWaliUtama(Long sekolahId, List<Long> siswaIds) {
        Map<Long, List<OrangTua>> perSiswa = orangTuaRepo
                .findBySiswaIdInAndSekolahId(siswaIds, sekolahId).stream()
                .collect(Collectors.groupingBy(OrangTua::getSiswaId));
        Map<Long, OrangTua> hasil = new LinkedHashMap<>();
        perSiswa.forEach((sid, list) -> {
            OrangTua utama = pilihWaliUtama(list);
            if (utama != null) {
                hasil.put(sid, utama);
            }
        });
        return hasil;
    }

    private Map<Long, Long> petakanSaldo(Long sekolahId, List<Long> siswaIds) {
        return saldoRepo.findBySekolahIdAndSiswaIdIn(sekolahId, siswaIds).stream()
                .collect(Collectors.toMap(
                        Saldo::getSiswaId,
                        s -> s.getSaldo() == null ? 0L : s.getSaldo(),
                        Long::sum,
                        LinkedHashMap::new));
    }

    private static OrangTua pilihWaliUtama(List<OrangTua> daftar) {
        return daftar.stream()
                .filter(OrangTua::isAktif)
                .min(Comparator.comparingInt(SiswaManajemenService::bobotHubungan))
                .orElse(null);
    }

    private static int bobotHubungan(OrangTua ot) {
        if (ot.getHubungan() == null) {
            return 9;
        }
        return switch (ot.getHubungan().toUpperCase(Locale.ROOT)) {
            case "AYAH" -> 1;
            case "IBU" -> 2;
            case "WALI" -> 3;
            default -> 8;
        };
    }

    private static SiswaManajemenResponse susunResponse(Siswa s, Kartu kartu, OrangTua wali, long saldo) {
        return SiswaManajemenResponse.builder()
                .siswaId(s.getId())
                .nis(s.getNis())
                .nisn(s.getNisn())
                .nama(s.getNama())
                .kelas(s.getKelas())
                .fotoUrl(s.getFotoUrl())
                .aktif(s.isAktif())
                .saldo(saldo)
                .rfidUid(kartu != null ? kartu.getUid() : null)
                .nomorKartu(kartu != null ? kartu.getNomorKartu() : null)
                .namaOrangTua(wali != null ? wali.getNama() : null)
                .teleponOrangTua(wali != null ? wali.getTelepon() : null)
                .hubunganOrangTua(wali != null ? wali.getHubungan() : null)
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    private static String normalisasiUidOpsional(String uid) {
        String bersih = UidKartu.normalisasi(uid);
        return (bersih == null || bersih.isEmpty()) ? null : bersih;
    }

    private static String normalisasiHubungan(String hubungan) {
        if (hubungan == null || hubungan.isBlank()) {
            return "WALI";
        }
        String upper = hubungan.trim().toUpperCase(Locale.ROOT);
        if (!HUBUNGAN_SAH.contains(upper)) {
            throw new InvalidOperationException("Hubungan wali harus salah satu dari AYAH, IBU, atau WALI");
        }
        return upper;
    }

    private static String trimWajib(String nilai, String pesan) {
        if (nilai == null || nilai.isBlank()) {
            throw new InvalidOperationException(pesan);
        }
        return nilai.trim();
    }

    private static String trimOpsional(String nilai) {
        if (nilai == null || nilai.isBlank()) {
            return null;
        }
        return nilai.trim();
    }

    private static String ringkasSiswa(Siswa s, String uid) {
        return "nis=" + s.getNis() + ",nama=" + s.getNama()
                + (uid != null ? ",uid=" + uid : "");
    }
}
