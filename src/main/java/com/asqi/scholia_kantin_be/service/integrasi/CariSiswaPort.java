package com.asqi.scholia_kantin_be.service.integrasi;

import java.util.List;
import java.util.Optional;

/**
 * Port pencarian siswa (PRD §9.2) — jembatan ke data siswa milik {@code admin-be}.
 *
 * <p>Kantin-be <b>tidak menyimpan master siswa</b>; yang dimiliki hanya ledger
 * saldo (per {@code subjekId}). Pencarian nama/NIS harus bertanya ke SKOOLIA.
 * Karena kontrak nyatanya belum final (Q7, lihat #164), port ini punya:
 * <ul>
 *   <li>{@link CariSiswaFallback} — default aman (daftar kosong) saat belum dikonfigurasi;</li>
 *   <li>{@code CariSiswaDemo} — data tiruan saat profil {@code demo} aktif, agar
 *       FE/alur TU bisa diuji tanpa data SKOOLIA.</li>
 * </ul>
 *
 * <p>Implementasi REST nyata ditambahkan saat kontrak Q7 admin-be tersedia
 * (pola sama seperti {@link KartuLookupPort}).
 */
public interface CariSiswaPort {

    /**
     * Cari siswa dalam satu sekolah berdasarkan kata kunci (nama atau NIS).
     *
     * @param sekolahId tenant dari JWT (wajib — data lintas sekolah tidak boleh bocor)
     * @param kataKunci teks pencarian (nama/NIS); boleh kosong → daftar awal
     * @param hanyaTidakAktif bila {@code true}, hanya siswa nonaktif (untuk refund §9.3)
     * @param batas     jumlah maksimum hasil
     * @return daftar siswa (bisa kosong; tidak pernah {@code null})
     */
    List<InfoSiswa> cari(Long sekolahId, String kataKunci, boolean hanyaTidakAktif, int batas);

    /**
     * Ambil identitas <b>satu</b> siswa berdasarkan ID (issue #297).
     *
     * <p>Dipakai untuk mengisi profil riwayat saldo: endpoint riwayat sudah tahu
     * {@code siswaId} dari ledger, jadi identitas harus diambil dengan lookup
     * tepat per ID — <b>bukan</b> menarik daftar hasil pencarian lalu memfilter,
     * karena siswa bisa berada di luar batas hasil.
     *
     * <p><b>Tenant.</b> {@code sekolahId} wajib berasal dari JWT; implementasi
     * REST mengandalkan token mesin-ke-mesin admin-be untuk pembatasan tenant,
     * dan pemanggil hanya boleh meneruskan ID yang sudah terbukti milik sekolah
     * tersebut (di sini: baris {@code saldo_cache} tenant yang bersangkutan).
     *
     * <p><b>Fail-open (PRD §8.4).</b> Siswa tidak ditemukan, 404, timeout, atau
     * kegagalan integrasi dikembalikan sebagai {@link Optional#empty()} — bukan
     * error — supaya riwayat saldo tetap tampil walau identitas tidak tersedia.
     *
     * @param sekolahId tenant dari JWT (wajib)
     * @param siswaId   ID siswa yang sudah divalidasi milik tenant
     * @return identitas siswa bila tersedia; {@link Optional#empty()} bila tidak
     */
    Optional<InfoSiswa> cariBerdasarkanId(Long sekolahId, Long siswaId);
}
