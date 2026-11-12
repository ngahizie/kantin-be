package com.asqi.scholia_kantin_be.config.integrasi;

import com.asqi.scholia_kantin_be.enums.JenisNotifikasi;
import com.asqi.scholia_kantin_be.enums.MetodeBukuKas;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.enums.TipeBukuKas;
import com.asqi.scholia_kantin_be.service.integrasi.AktivasiModulInfo;
import com.asqi.scholia_kantin_be.service.integrasi.HasilKirimNotifikasi;
import com.asqi.scholia_kantin_be.service.integrasi.HasilJurnalBukuKas;
import com.asqi.scholia_kantin_be.service.integrasi.InfoKartu;
import com.asqi.scholia_kantin_be.service.integrasi.PerintahBukuKas;
import com.asqi.scholia_kantin_be.service.integrasi.PerintahNotifikasi;
import com.asqi.scholia_kantin_be.service.kasir.LedgerSaldoService;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Uji <b>kontrak</b> adapter REST integrasi memakai <b>WireMock</b> (issue #160).
 *
 * <p>Membuktikan tiap adapter memetakan respons dengan benar untuk:
 * <b>sukses</b>, <b>5xx</b> (fail-open/fail-safe), <b>timeout</b>, dan
 * <b>payload salah</b> — tanpa menyentuh layanan nyata. WireMock berjalan
 * embedded (tanpa Docker) sehingga ikut di {@code mvn test}.
 *
 * <p>Karena adapter hanya hidup bila {@code kantin.integrasi.*.enabled=true},
 * test ini membuat adapter secara manual dengan properti yang diarahkan ke port
 * WireMock — tidak menyentuh konteks Spring.
 */
@DisplayName("Adapter REST integrasi — uji kontrak WireMock (#160)")
class AdapterRestWireMockTest {

    private WireMockServer wm;

    @BeforeEach
    void mulai() {
        wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wm.start();
    }

    @AfterEach
    void henti() {
        if (wm != null) {
            wm.stop();
        }
    }

    private IntegrasiRestProperties props() {
        return new IntegrasiRestProperties();
    }

    // ------------------------------------------------------------------
    // BukuKas (#160, Q3)
    // ------------------------------------------------------------------

    private BukuKasRestClient bukuKas(int maxAttempts) {
        IntegrasiRestProperties p = props();
        var e = p.getBukukas();
        e.setEnabled(true);
        e.setBaseUrl(wm.baseUrl());
        e.setMaxAttempts(maxAttempts);
        e.setBackoffMs(10);
        return new BukuKasRestClient(p);
    }

    private PerintahBukuKas perintahBukuKas() {
        return PerintahBukuKas.builder()
                .sekolahId(1L)
                .tanggal(OffsetDateTime.parse("2026-10-12T10:00:00+07:00"))
                .tipe(TipeBukuKas.MASUK)
                .kategori("Pendapatan Kantin")
                .jumlah(BigDecimal.valueOf(50_000L))
                .metode(MetodeBukuKas.NON_TUNAI)
                .keterangan("Tutup sesi #1")
                .refId("KANTIN-SESI-1")
                .refModul("KANTIN")
                .build();
    }

    @Test
    @DisplayName("BukuKas: sukses 200 → SUKSES + kirim refId deterministik")
    void bukukasSukses() {
        wm.stubFor(post(urlEqualTo("/api/buku-kas/catat"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":200,\"message\":\"OK\"}")));

        HasilJurnalBukuKas hasil = bukuKas(1).catat(perintahBukuKas());

        assertThat(hasil.status()).isEqualTo(HasilJurnalBukuKas.Status.SUKSES);
        wm.verify(postRequestedFor(urlEqualTo("/api/buku-kas/catat"))
                .withRequestBody(matchingJsonPath("$.refId", equalTo("KANTIN-SESI-1")))
                .withRequestBody(matchingJsonPath("$.jumlah", equalTo("50000")))
                .withRequestBody(matchingJsonPath("$.tipe", equalTo("MASUK"))));
    }

    @Test
    @DisplayName("BukuKas: 5xx → GAGAL (fail-open, sesi tidak dibatalkan)")
    void bukukasGagal5xx() {
        wm.stubFor(post(urlEqualTo("/api/buku-kas/catat"))
                .willReturn(aResponse().withStatus(500).withBody("boom")));

        HasilJurnalBukuKas hasil = bukuKas(1).catat(perintahBukuKas());

        assertThat(hasil.status()).isEqualTo(HasilJurnalBukuKas.Status.GAGAL);
        assertThat(hasil.sukses()).isFalse();
    }

    @Test
    @DisplayName("BukuKas: 5xx lalu sukses → retry berhasil (max-attempts=2)")
    void bukukasRetry() {
        wm.stubFor(post(urlEqualTo("/api/buku-kas/catat"))
                .inScenario("retry")
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("ok"));
        wm.stubFor(post(urlEqualTo("/api/buku-kas/catat"))
                .inScenario("retry")
                .whenScenarioStateIs("ok")
                .willReturn(aResponse().withStatus(200)));

        HasilJurnalBukuKas hasil = bukuKas(2).catat(perintahBukuKas());

        assertThat(hasil.status()).isEqualTo(HasilJurnalBukuKas.Status.SUKSES);
        wm.verify(2, postRequestedFor(urlEqualTo("/api/buku-kas/catat")));
    }

    @Test
    @DisplayName("BukuKas: 4xx TIDAK di-retry (salah permintaan)")
    void bukukasTidakRetry4xx() {
        wm.stubFor(post(urlEqualTo("/api/buku-kas/catat"))
                .willReturn(aResponse().withStatus(400).withBody("bad")));

        HasilJurnalBukuKas hasil = bukuKas(3).catat(perintahBukuKas());

        assertThat(hasil.status()).isEqualTo(HasilJurnalBukuKas.Status.GAGAL);
        wm.verify(1, postRequestedFor(urlEqualTo("/api/buku-kas/catat")));
    }

    // ------------------------------------------------------------------
    // Notifikasi (#160, Q5) — fail-open
    // ------------------------------------------------------------------

    private NotifikasiRestClient notifikasi() {
        IntegrasiRestProperties p = props();
        var e = p.getNotifikasi();
        e.setEnabled(true);
        e.setBaseUrl(wm.baseUrl());
        e.setBackoffMs(10);
        return new NotifikasiRestClient(p);
    }

    private PerintahNotifikasi perintahNotif() {
        return PerintahNotifikasi.builder()
                .sekolahId(1L)
                .jenis(JenisNotifikasi.BELANJA)
                .subjekTipe(SubjekTipe.SISWA)
                .siswaId(7L)
                .nominal(8_000L)
                .ringkasan("Budi belanja Rp8.000")
                .build();
    }

    @Test
    @DisplayName("Notifikasi: sukses 200 → TERKIRIM + body sesuai kontrak Q5")
    void notifikasiSukses() {
        wm.stubFor(post(urlEqualTo("/api/internal/notifikasi"))
                .willReturn(aResponse().withStatus(200).withBody("{\"code\":200}")));

        HasilKirimNotifikasi hasil = notifikasi().kirim(perintahNotif());

        assertThat(hasil.status()).isEqualTo(HasilKirimNotifikasi.Status.TERKIRIM);
        wm.verify(postRequestedFor(urlEqualTo("/api/internal/notifikasi"))
                .withRequestBody(matchingJsonPath("$.siswaId", equalTo("7")))
                .withRequestBody(matchingJsonPath("$.jenis", equalTo("BELANJA"))));
    }

    @Test
    @DisplayName("Notifikasi: 5xx → GAGAL tapi tidak melempar (best-effort)")
    void notifikasiGagal5xx() {
        wm.stubFor(post(urlEqualTo("/api/internal/notifikasi"))
                .willReturn(aResponse().withStatus(500)));

        HasilKirimNotifikasi hasil = notifikasi().kirim(perintahNotif());

        assertThat(hasil.status()).isEqualTo(HasilKirimNotifikasi.Status.GAGAL);
    }

    @Test
    @DisplayName("Notifikasi: timeout → GAGAL (best-effort, tidak melempar)")
    void notifikasiTimeout() {
        wm.stubFor(post(urlEqualTo("/api/internal/notifikasi"))
                .willReturn(aResponse().withFixedDelay(1500).withStatus(200)));

        IntegrasiRestProperties p = props();
        var e = p.getNotifikasi();
        e.setEnabled(true);
        e.setBaseUrl(wm.baseUrl());
        e.setTimeoutMs(300); // lebih pendek dari delay
        NotifikasiRestClient klien = new NotifikasiRestClient(p);

        HasilKirimNotifikasi hasil = klien.kirim(perintahNotif());

        assertThat(hasil.status()).isEqualTo(HasilKirimNotifikasi.Status.GAGAL);
    }

    // ------------------------------------------------------------------
    // KartuLookup (#160, Q7) — tanpa cache status blokir
    // ------------------------------------------------------------------

    private KartuLookupRestClient kartuLookup() {
        IntegrasiRestProperties p = props();
        var e = p.getKartuLookup();
        e.setEnabled(true);
        e.setBaseUrl(wm.baseUrl());
        e.setBackoffMs(10);
        // Lookup hanya ke admin-be (kartu tamu tidak lagi punya jalur saldo sendiri).
        return new KartuLookupRestClient(p);
    }

    @Test
    @DisplayName("KartuLookup: 200 siswa → dikenal, SISWA, data terisi")
    void kartuLookupSiswa() {
        wm.stubFor(get(urlPathEqualTo("/api/siswa/cek-kartu/UID-SISWA"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"code":200,"data":{"id":7,"namaLengkap":"Budi",
                                "fotoAnakUrl":"http://x/f.jpg","kelas":{"id":3,"namaKelas":"5A"}}}
                                """)));

        InfoKartu info = kartuLookup().cariBerdasarkanUid(1L, "UID-SISWA");

        assertThat(info.isDikenal()).isTrue();
        assertThat(info.getSubjekTipe()).isEqualTo(SubjekTipe.SISWA);
        assertThat(info.getSiswaId()).isEqualTo(7L);
        assertThat(info.getNama()).isEqualTo("Budi");
        assertThat(info.getKelas()).isEqualTo("5A");
    }

    @Test
    @DisplayName("KartuLookup: 404 → tidak dikenal (kartu tak terdaftar, bukan error)")
    void kartuLookup404() {
        wm.stubFor(get(urlPathEqualTo("/api/siswa/cek-kartu/UID-HILANG"))
                .willReturn(aResponse().withStatus(404)));

        InfoKartu info = kartuLookup().cariBerdasarkanUid(1L, "UID-HILANG");

        assertThat(info.isDikenal()).isFalse();
    }

    @Test
    @DisplayName("KartuLookup: 5xx → tidak dikenal (fail-closed, bukan crash)")
    void kartuLookup5xx() {
        wm.stubFor(get(urlPathEqualTo("/api/siswa/cek-kartu/UID-X"))
                .willReturn(aResponse().withStatus(500)));

        InfoKartu info = kartuLookup().cariBerdasarkanUid(1L, "UID-X");

        assertThat(info.isDikenal()).isFalse();
    }

    @Test
    @DisplayName("KartuLookup: payload salah (tanpa id) → tidak dikenal")
    void kartuLookupPayloadSalah() {
        wm.stubFor(get(urlPathEqualTo("/api/siswa/cek-kartu/UID-Y"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":200,\"data\":{\"namaLengkap\":\"Tanpa Id\"}}")));

        InfoKartu info = kartuLookup().cariBerdasarkanUid(1L, "UID-Y");

        assertThat(info.isDikenal()).isFalse();
    }

    // ------------------------------------------------------------------
    // AktivasiModul (#160, Q6) — fail-open
    // ------------------------------------------------------------------

    private AktivasiModulRestClient aktivasi() {
        IntegrasiRestProperties p = props();
        var e = p.getAktivasi();
        e.setEnabled(true);
        e.setBaseUrl(wm.baseUrl());
        e.setBackoffMs(10);
        return new AktivasiModulRestClient(p);
    }

    @Test
    @DisplayName("AktivasiModul: 200 aktif=false → diketahui & nonaktif")
    void aktivasiNonaktif() {
        wm.stubFor(get(urlPathEqualTo("/api/internal/modul-kantin/status"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"aktif\":false,\"fee\":{\"perTopupNominal\":500}}")));

        AktivasiModulInfo info = aktivasi().status(1L);

        assertThat(info.isDiketahui()).isTrue();
        assertThat(info.isAktif()).isFalse();
        assertThat(info.getFeeTopup()).isEqualTo(500L);
        wm.verify(getRequestedFor(urlPathEqualTo("/api/internal/modul-kantin/status"))
                .withQueryParam("sekolahId", equalTo("1")));
    }

    @Test
    @DisplayName("AktivasiModul: 5xx → tidak diketahui, dianggap AKTIF (fail-open)")
    void aktivasiFailOpen() {
        wm.stubFor(get(urlPathEqualTo("/api/internal/modul-kantin/status"))
                .willReturn(aResponse().withStatus(500)));

        AktivasiModulInfo info = aktivasi().status(1L);

        assertThat(info.isDiketahui()).isFalse();
        assertThat(info.isAktif()).isTrue(); // fail-open
    }

    // ------------------------------------------------------------------
    // StatusSiswa (#160, Q7)
    // ------------------------------------------------------------------

    private StatusSiswaRestClient statusSiswa() {
        IntegrasiRestProperties p = props();
        var e = p.getStatusSiswa();
        e.setEnabled(true);
        e.setBaseUrl(wm.baseUrl());
        e.setBackoffMs(10);
        return new StatusSiswaRestClient(p);
    }

    @Test
    @DisplayName("StatusSiswa #298: batch — satu request untuk banyak ID")
    void statusSiswaBanyak() {
        wm.stubFor(get(urlPathEqualTo("/api/siswa/status"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"code":200,"message":"ok","data":[
                                  {"id":101,"isActive":true},
                                  {"id":106,"isLulus":true}
                                ]}""")));

        var hasil = statusSiswa().tidakAktifBanyak(1L, java.util.List.of(101L, 106L));

        assertThat(hasil).containsEntry(101L, Boolean.FALSE).containsEntry(106L, Boolean.TRUE);
        wm.verify(1, getRequestedFor(urlPathEqualTo("/api/siswa/status"))
                .withQueryParam("ids", equalTo("101,106")));
    }

    @Test
    @DisplayName("StatusSiswa #298: batch gagal → peta kosong (semua 'tidak diketahui')")
    void statusSiswaBanyakGagal() {
        wm.stubFor(get(urlPathEqualTo("/api/siswa/status"))
                .willReturn(aResponse().withStatus(500)));

        assertThat(statusSiswa().tidakAktifBanyak(1L, java.util.List.of(101L))).isEmpty();
    }

    @Test
    @DisplayName("StatusSiswa: isActive=false → tidakAktif=true")
    void statusTidakAktif() {
        wm.stubFor(get(urlPathEqualTo("/api/siswa/get-by-id/9"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":200,\"data\":{\"id\":9,\"isActive\":false}}")));

        assertThat(statusSiswa().tidakAktif(1L, 9L)).isTrue();
    }

    @Test
    @DisplayName("StatusSiswa: isLulus=true → tidakAktif=true")
    void statusLulus() {
        wm.stubFor(get(urlPathEqualTo("/api/siswa/get-by-id/9"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":200,\"data\":{\"id\":9,\"isActive\":true,\"isLulus\":true}}")));

        assertThat(statusSiswa().tidakAktif(1L, 9L)).isTrue();
    }

    @Test
    @DisplayName("StatusSiswa: aktif → tidakAktif=false")
    void statusAktif() {
        wm.stubFor(get(urlPathEqualTo("/api/siswa/get-by-id/9"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":200,\"data\":{\"id\":9,\"isActive\":true}}")));

        assertThat(statusSiswa().tidakAktif(1L, 9L)).isFalse();
    }

    @Test
    @DisplayName("StatusSiswa: gagal → null (tidak diketahui, fail-safe)")
    void statusGagal() {
        wm.stubFor(get(urlPathEqualTo("/api/siswa/get-by-id/9"))
                .willReturn(aResponse().withStatus(500)));

        assertThat(statusSiswa().tidakAktif(1L, 9L)).isNull();
    }

    @Test
    @DisplayName("StatusSiswa: blokirKartu best-effort — 5xx tidak melempar")
    void blokirBestEffort() {
        wm.stubFor(post(urlEqualTo("/api/siswa/kartu/blokir"))
                .willReturn(aResponse().withStatus(500)));

        // Tidak boleh melempar:
        statusSiswa().blokirKartu(1L, 9L, "siswa keluar");
        wm.verify(postRequestedFor(urlEqualTo("/api/siswa/kartu/blokir"))
                .withRequestBody(matchingJsonPath("$.siswaId", equalTo("9"))));
    }

    // ------------------------------------------------------------------
    // UidSiswa (#160, Q7)
    // ------------------------------------------------------------------

    private UidSiswaRestClient uidSiswa() {
        IntegrasiRestProperties p = props();
        var e = p.getUidSiswa();
        e.setEnabled(true);
        e.setBaseUrl(wm.baseUrl());
        e.setBackoffMs(10);
        return new UidSiswaRestClient(p);
    }

    @Test
    @DisplayName("UidSiswa: dipakai=true → true + query sekolahId")
    void uidDipakai() {
        wm.stubFor(get(urlPathEqualTo("/api/internal/siswa/cek-uid"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"dipakai\":true}")));

        assertThat(uidSiswa().dipakaiSiswa(1L, "UID-A")).isTrue();
        wm.verify(getRequestedFor(urlPathEqualTo("/api/internal/siswa/cek-uid"))
                .withQueryParam("rfidUid", equalTo("UID-A"))
                .withQueryParam("sekolahId", equalTo("1")));
    }

    @Test
    @DisplayName("UidSiswa: gagal → null (fail-open, tidak menolak registrasi)")
    void uidGagal() {
        wm.stubFor(get(urlPathEqualTo("/api/internal/siswa/cek-uid"))
                .willReturn(aResponse().withStatus(503)));

        assertThat(uidSiswa().dipakaiSiswa(1L, "UID-A")).isNull();
    }

    // ------------------------------------------------------------------
    // CariSiswa (#164 · Q7 · #224)
    // ------------------------------------------------------------------

    private CariSiswaRestClient cariSiswa(LedgerSaldoService ledger) {
        IntegrasiRestProperties p = props();
        var e = p.getCariSiswa();
        e.setEnabled(true);
        e.setBaseUrl(wm.baseUrl());
        e.setBackoffMs(10);
        return new CariSiswaRestClient(p, ledger);
    }

    private LedgerSaldoService ledger(long saldo) {
        LedgerSaldoService l = mock(LedgerSaldoService.class);
        when(l.saldo(anyLong(), anyLong())).thenReturn(saldo);
        return l;
    }

    @Test
    @DisplayName("CariSiswa: amplop {data:[...]} → terpetakan + saldo dari ledger lokal")
    void cariSiswaAmplop() {
        wm.stubFor(get(urlPathEqualTo("/api/siswa/cari"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"code":200,"message":"ok","data":[
                                  {"id":101,"nis":"2024001","namaLengkap":"Budi Santoso",
                                   "kelas":{"namaKelas":"7A"},"tidakAktif":false}
                                ]}""")));

        var hasil = cariSiswa(ledger(15000L)).cari(1L, "budi", false, 10);

        assertThat(hasil).hasSize(1);
        assertThat(hasil.get(0).getSiswaId()).isEqualTo(101L);
        assertThat(hasil.get(0).getNis()).isEqualTo("2024001");
        assertThat(hasil.get(0).getNama()).isEqualTo("Budi Santoso");
        assertThat(hasil.get(0).getKelas()).isEqualTo("7A");
        assertThat(hasil.get(0).getTidakAktif()).isFalse();
        assertThat(hasil.get(0).getSaldo()).isEqualTo(15000L);

        wm.verify(getRequestedFor(urlPathEqualTo("/api/siswa/cari"))
                .withQueryParam("q", equalTo("budi"))
                .withQueryParam("batas", equalTo("10")));
    }

    @Test
    @DisplayName("CariSiswa: array datar & kelas string → tetap terpetakan")
    void cariSiswaArrayDatar() {
        wm.stubFor(get(urlPathEqualTo("/api/siswa/cari"))
                .willReturn(aResponse().withStatus(200)
                        .withBody("[{\"id\":102,\"nis\":\"2026002\",\"nama\":\"Siti Rahmawati\",\"kelas\":\"XI IPS 2\"}]")));

        var hasil = cariSiswa(ledger(0L)).cari(1L, "", false, 5);

        assertThat(hasil).hasSize(1);
        assertThat(hasil.get(0).getNama()).isEqualTo("Siti Rahmawati");
        assertThat(hasil.get(0).getKelas()).isEqualTo("XI IPS 2");
    }

    @Test
    @DisplayName("CariSiswa: hanyaTidakAktif → baris aktif dibuang di sisi klien")
    void cariSiswaHanyaTidakAktif() {
        wm.stubFor(get(urlPathEqualTo("/api/siswa/cari"))
                .willReturn(aResponse().withStatus(200)
                        .withBody("""
                                {"data":[
                                  {"id":101,"namaLengkap":"Budi","tidakAktif":false},
                                  {"id":106,"namaLengkap":"Maya","tidakAktif":true}
                                ]}""")));

        var hasil = cariSiswa(ledger(0L)).cari(1L, "", true, 10);

        assertThat(hasil).hasSize(1);
        assertThat(hasil.get(0).getSiswaId()).isEqualTo(106L);
        wm.verify(getRequestedFor(urlPathEqualTo("/api/siswa/cari"))
                .withQueryParam("hanyaTidakAktif", equalTo("true")));
    }

    @Test
    @DisplayName("CariSiswa: batas dipatuhi walau admin-be mengirim lebih banyak")
    void cariSiswaBatas() {
        wm.stubFor(get(urlPathEqualTo("/api/siswa/cari"))
                .willReturn(aResponse().withStatus(200)
                        .withBody("""
                                {"data":[
                                  {"id":1,"namaLengkap":"A"},
                                  {"id":2,"namaLengkap":"B"},
                                  {"id":3,"namaLengkap":"C"}
                                ]}""")));

        assertThat(cariSiswa(ledger(0L)).cari(1L, "", false, 2)).hasSize(2);
    }

    @Test
    @DisplayName("CariSiswa #298: batch — satu request untuk banyak ID, identitas terpetakan")
    void cariSiswaBanyak() {
        wm.stubFor(get(urlPathEqualTo("/api/siswa"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"code":200,"message":"ok","data":[
                                  {"id":101,"nis":"2024001","namaLengkap":"Budi Santoso","kelas":"7A"},
                                  {"id":106,"nis":"2023006","namaLengkap":"Maya Sari","kelas":{"namaKelas":"9C"},
                                   "tidakAktif":true}
                                ]}""")));

        var hasil = cariSiswa(ledger(0L)).cariBanyak(1L, java.util.List.of(101L, 106L));

        assertThat(hasil).hasSize(2);
        assertThat(hasil.get(101L).getNama()).isEqualTo("Budi Santoso");
        assertThat(hasil.get(106L).getKelas()).isEqualTo("9C");
        assertThat(hasil.get(106L).getTidakAktif()).isTrue();
        wm.verify(1, getRequestedFor(urlPathEqualTo("/api/siswa"))
                .withQueryParam("ids", equalTo("101,106")));
    }

    @Test
    @DisplayName("CariSiswa #298: batch gagal → peta kosong (fail-open), tanpa melempar")
    void cariSiswaBanyakGagal() {
        wm.stubFor(get(urlPathEqualTo("/api/siswa"))
                .willReturn(aResponse().withStatus(503)));

        assertThat(cariSiswa(ledger(0L)).cariBanyak(1L, java.util.List.of(101L, 106L))).isEmpty();
    }

    @Test
    @DisplayName("CariSiswa #297: lookup per ID → identitas + saldo lokal, satu request tepat")
    void cariSiswaById() {
        wm.stubFor(get(urlPathEqualTo("/api/siswa/101"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"code":200,"message":"ok","data":
                                  {"id":101,"nis":"2024001","namaLengkap":"Budi Santoso",
                                   "kelas":{"namaKelas":"7A"},"tidakAktif":false}}""")));

        var hasil = cariSiswa(ledger(15000L)).cariBerdasarkanId(1L, 101L);

        assertThat(hasil).isPresent();
        assertThat(hasil.get().getSiswaId()).isEqualTo(101L);
        assertThat(hasil.get().getNis()).isEqualTo("2024001");
        assertThat(hasil.get().getNama()).isEqualTo("Budi Santoso");
        assertThat(hasil.get().getKelas()).isEqualTo("7A");
        assertThat(hasil.get().getSaldo()).isEqualTo(15000L);
        wm.verify(1, getRequestedFor(urlPathEqualTo("/api/siswa/101")));
        wm.verify(0, getRequestedFor(urlPathEqualTo("/api/siswa/cari")));
    }

    @Test
    @DisplayName("CariSiswa #297: 404 → Optional kosong (bukan error)")
    void cariSiswaByIdTidakDitemukan() {
        wm.stubFor(get(urlPathEqualTo("/api/siswa/999"))
                .willReturn(aResponse().withStatus(404)));

        assertThat(cariSiswa(ledger(0L)).cariBerdasarkanId(1L, 999L)).isEmpty();
    }

    @Test
    @DisplayName("CariSiswa #297: 5xx → Optional kosong (fail-open, riwayat tetap tampil)")
    void cariSiswaByIdGagal() {
        wm.stubFor(get(urlPathEqualTo("/api/siswa/101"))
                .willReturn(aResponse().withStatus(503)));

        assertThat(cariSiswa(ledger(0L)).cariBerdasarkanId(1L, 101L)).isEmpty();
    }

    @Test
    @DisplayName("CariSiswa #297: ID null → tidak memanggil admin-be")
    void cariSiswaByIdNull() {
        assertThat(cariSiswa(ledger(0L)).cariBerdasarkanId(1L, null)).isEmpty();
        wm.verify(0, getRequestedFor(urlPathMatching("/api/siswa/.*")));
    }

    @Test
    @DisplayName("CariSiswa #301: UID kartu per siswa dibaca dari field rfidUid")
    void cariUidSiswa() {
        wm.stubFor(get(urlPathEqualTo("/api/siswa/101"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"code":200,"message":"ok","data":
                                  {"id":101,"nis":"2024001","namaLengkap":"Budi Santoso",
                                   "rfidUid":"DA100001"}}""")));

        assertThat(cariSiswa(ledger(0L)).cariUidSiswa(1L, 101L)).contains("DA100001");
        wm.verify(1, getRequestedFor(urlPathEqualTo("/api/siswa/101")));
        // UID tidak boleh diambil dari endpoint daftar.
        wm.verify(0, getRequestedFor(urlPathEqualTo("/api/siswa/cari")));
    }

    @Test
    @DisplayName("CariSiswa #301: toleran terhadap field snake_case rfid_uid")
    void cariUidSiswaSnakeCase() {
        wm.stubFor(get(urlPathEqualTo("/api/siswa/102"))
                .willReturn(aResponse().withStatus(200)
                        .withBody("{\"data\":{\"id\":102,\"rfid_uid\":\"DA100002\"}}")));

        assertThat(cariSiswa(ledger(0L)).cariUidSiswa(1L, 102L)).contains("DA100002");
    }

    @Test
    @DisplayName("CariSiswa #301: admin-be tidak mengirim UID → Optional kosong (tidak dikarang)")
    void cariUidSiswaTidakAdaField() {
        wm.stubFor(get(urlPathEqualTo("/api/siswa/103"))
                .willReturn(aResponse().withStatus(200)
                        .withBody("{\"data\":{\"id\":103,\"namaLengkap\":\"Andi Wijaya\"}}")));

        assertThat(cariSiswa(ledger(0L)).cariUidSiswa(1L, 103L)).isEmpty();
    }

    @Test
    @DisplayName("CariSiswa #301: 404 → Optional kosong (bukan error)")
    void cariUidSiswaTidakDitemukan() {
        wm.stubFor(get(urlPathEqualTo("/api/siswa/999"))
                .willReturn(aResponse().withStatus(404)));

        assertThat(cariSiswa(ledger(0L)).cariUidSiswa(1L, 999L)).isEmpty();
    }

    @Test
    @DisplayName("CariSiswa #301: ID null → tidak memanggil admin-be")
    void cariUidSiswaIdNull() {
        assertThat(cariSiswa(ledger(0L)).cariUidSiswa(1L, null)).isEmpty();
        wm.verify(0, getRequestedFor(urlPathMatching("/api/siswa/.*")));
    }

    @Test
    @DisplayName("CariSiswa: 5xx → daftar kosong (fail-open, layar TU tidak error)")
    void cariSiswaGagal() {
        wm.stubFor(get(urlPathEqualTo("/api/siswa/cari"))
                .willReturn(aResponse().withStatus(503)));

        assertThat(cariSiswa(ledger(0L)).cari(1L, "budi", false, 10)).isEmpty();
    }

    @Test
    @DisplayName("CariSiswa: payload bukan JSON → daftar kosong, tidak melempar")
    void cariSiswaPayloadRusak() {
        wm.stubFor(get(urlPathEqualTo("/api/siswa/cari"))
                .willReturn(aResponse().withStatus(200).withBody("bukan-json")));

        assertThat(cariSiswa(ledger(0L)).cari(1L, "", false, 10)).isEmpty();
    }

    // ------------------------------------------------------------------
    // Keamanan: header HMAC bila secret diisi (#29)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Header HMAC-SHA256 terkirim bila secret diisi (pola #29)")
    void headerHmac() {
        wm.stubFor(get(urlPathEqualTo("/api/internal/siswa/cek-uid"))
                .willReturn(aResponse().withStatus(200).withBody("{\"dipakai\":false}")));

        IntegrasiRestProperties p = props();
        var e = p.getUidSiswa();
        e.setEnabled(true);
        e.setBaseUrl(wm.baseUrl());
        e.setSecret("rahasia-uji");
        UidSiswaRestClient klien = new UidSiswaRestClient(p);

        assertThat(klien.dipakaiSiswa(1L, "UID-A")).isFalse();
        wm.verify(getRequestedFor(urlPathEqualTo("/api/internal/siswa/cek-uid"))
                .withHeader("X-Internal-Timestamp", com.github.tomakehurst.wiremock.client.WireMock.matching(".+"))
                .withHeader("X-Internal-Signature", com.github.tomakehurst.wiremock.client.WireMock.matching("sha256=[0-9a-f]{64}")));
    }

    @Test
    @DisplayName("Bearer token terkirim bila diisi")
    void headerBearer() {
        wm.stubFor(get(urlPathEqualTo("/api/internal/siswa/cek-uid"))
                .willReturn(aResponse().withStatus(200).withBody("{\"dipakai\":false}")));

        IntegrasiRestProperties p = props();
        var e = p.getUidSiswa();
        e.setEnabled(true);
        e.setBaseUrl(wm.baseUrl());
        e.setBearerToken("token-abc");
        UidSiswaRestClient klien = new UidSiswaRestClient(p);

        klien.dipakaiSiswa(1L, "UID-A");
        wm.verify(getRequestedFor(urlPathEqualTo("/api/internal/siswa/cek-uid"))
                .withHeader("Authorization", equalTo("Bearer token-abc")));
    }
}
