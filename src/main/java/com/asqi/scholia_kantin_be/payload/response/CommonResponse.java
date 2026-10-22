package com.asqi.scholia_kantin_be.payload.response;

import com.asqi.scholia_kantin_be.helper.Constants;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.validation.Errors;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Pabrik respons standar kantin-be — parity {@code CommonResponse} admin-be.
 *
 * <p><b>Perbaikan dari admin-be (temuan bug):</b> di admin-be, sebagian method
 * menyetel HTTP status via {@code ResponseEntity.status(...)} tetapi metode lain
 * hanya mengisi {@code body.code} dan tetap mengembalikan HTTP 200. Akibatnya
 * klien tidak bisa membedakan sukses vs gagal dari status HTTP saja. Di kantin-be:
 * <ul>
 *   <li>{@code success/data/paginated} → <b>HTTP 200</b>.</li>
 *   <li>Seluruh error → <b>HTTP status yang sesuai</b> (400/401/403/404/409/429/500)
 *       dengan {@code code} yang sama di body, plus {@code message} dan
 *       {@code timestamp}. FE tetap bisa membaca {@code body.code}.</li>
 * </ul>
 * Jangan mengubah kontrak ini tanpa ADR — FE bergantung padanya.
 */
public class CommonResponse {

    private CommonResponse() {
    }

    // ────────────────────────────────────────────────────────────────
    // SUKSES (HTTP 200)
    // ────────────────────────────────────────────────────────────────

    public static <T> ResponseEntity<Response<T>> success() {
        Response<T> r = new Response<>();
        r.setResponseCode(ResponseCode.SUCCESS);
        return ResponseEntity.ok(r);
    }

    public static <T> ResponseEntity<Response<Void>> success(String message) {
        Response<Void> r = new Response<>();
        r.setResponseCode(ResponseCode.SUCCESS);
        r.setMessage(message);
        return ResponseEntity.ok(r);
    }

    public static <T> ResponseEntity<Response<T>> data(T data) {
        Response<T> r = new Response<>();
        r.setResponseCode(ResponseCode.SUCCESS);
        r.setData(data);
        return ResponseEntity.ok(r);
    }

    public static <T> ResponseEntity<Response<T>> data(T data, String message) {
        Response<T> r = new Response<>();
        r.setResponseCode(ResponseCode.SUCCESS);
        r.setData(data);
        r.setMessage(message);
        return ResponseEntity.ok(r);
    }

    public static <P> ResponseEntity<Response<Page<P>>> paginated(Page<P> page) {
        Response<Page<P>> r = new Response<>();
        r.setResponseCode(ResponseCode.SUCCESS);
        r.setPageData(page);
        return ResponseEntity.ok(r);
    }

    public static <T, D> ResponseEntity<Response<Page<T>>> paginated(Page<T> page, List<D> dtoList) {
        Response<Page<T>> r = new Response<>();
        r.setResponseCode(ResponseCode.SUCCESS);
        r.setData(page, dtoList);
        return ResponseEntity.ok(r);
    }

    public static <T> ResponseEntity<Response<Void>> stored(String entityName) {
        return success(entityName.concat(" berhasil disimpan"));
    }

    public static <T> ResponseEntity<Response<Void>> stored(Class<T> tClass) {
        return success(Constants.pascalToSentenceCase(tClass.getSimpleName()).concat(" berhasil disimpan"));
    }

    public static <T> ResponseEntity<Response<Void>> updated(String entityName) {
        return success(entityName.concat(" berhasil diperbarui"));
    }

    public static <T> ResponseEntity<Response<Void>> updated(Class<T> tClass) {
        return success(Constants.pascalToSentenceCase(tClass.getSimpleName()).concat(" berhasil diperbarui"));
    }

    public static <T> ResponseEntity<Response<Void>> softDeleted(String entityName) {
        return success(entityName.concat(" berhasil dihapus (soft delete)"));
    }

    public static <T> ResponseEntity<Response<Void>> softDeleted(Class<T> tClass) {
        return success(Constants.pascalToSentenceCase(tClass.getSimpleName()).concat(" berhasil dihapus (soft delete)"));
    }

    public static <T> ResponseEntity<Response<Void>> restored(String entityName) {
        return success(entityName.concat(" berhasil direstore"));
    }

    public static <T> ResponseEntity<Response<Void>> restored(Class<T> tClass) {
        return success(Constants.pascalToSentenceCase(tClass.getSimpleName()).concat(" berhasil direstore"));
    }

    // ────────────────────────────────────────────────────────────────
    // VALIDASI
    // ────────────────────────────────────────────────────────────────

    /**
     * Kontrak validasi: <b>HTTP 400</b> + {@code code=100} (atau {@code code=400}
     * untuk error parameter), body tetap {@code {code, message, timestamp}}.
     *
     * <p>Dulu default-nya HTTP 200 (warisan admin-be) — menyulitkan klien modern
     * (harus baca body untuk tahu sukses/gagal) dan membuat request cacat
     * "lolos" tanpa diuji RBAC. Diubah ke 400 pada issue #251.
     *
     * <p>Saklar mundur: {@code kantin.validation.http-400=false} mengembalikan
     * seluruh respons validasi ke HTTP 200 (lihat {@code ValidasiContractConfig}).
     */
    private static boolean validasiHttp400 = true;

    public static void setValidasiHttp400(boolean aktif) {
        validasiHttp400 = aktif;
    }

    private static <T> ResponseEntity<Response<T>> bungkusValidasi(Response<T> response) {
        return validasiHttp400
                ? ResponseEntity.badRequest().body(response)
                : ResponseEntity.ok(response);
    }

    public static <T> ResponseEntity<Response<T>> validationError(Errors errors) {
        Response<T> response = new Response<>();
        response.setResponseCode(ResponseCode.VALIDATION);
        response.setValidation(Constants.validateErrorMessage(errors));
        return bungkusValidasi(response);
    }

    public static <T> ResponseEntity<Response<T>> validationError(Map<String, String> validationErrorMessages) {
        Response<T> response = new Response<>();
        response.setResponseCode(ResponseCode.VALIDATION);
        response.setValidation(validationErrorMessages);
        return bungkusValidasi(response);
    }

    // ────────────────────────────────────────────────────────────────
    // ERROR — memakai HTTP status yang benar (perbaikan dari admin-be)
    // ────────────────────────────────────────────────────────────────

    /** 404 — data tidak ditemukan / data sekolah lain (PRD §11.4). */
    public static <T> ResponseEntity<Response<T>> noData(String message) {
        Response<T> r = new Response<>();
        r.setCode(404);
        r.setMessage(message);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(r);
    }

    public static <T> ResponseEntity<Response<T>> notFound(Class<T> entityClass) {
        return noData(entityClass.getSimpleName() + " tidak ditemukan.");
    }

    /** 401 — belum/ gagal autentikasi. */
    public static <T> ResponseEntity<Response<T>> unauthenticated() {
        return unauthenticated("Unauthenticated");
    }

    public static <T> ResponseEntity<Response<T>> unauthenticated(String message) {
        Response<T> r = new Response<>();
        r.setResponseCode(ResponseCode.UNAUTHORIZED);
        r.setCode(401);
        r.setMessage(message);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(r);
    }

    /** 403 — terautentikasi tapi tidak berhak (BUKAN untuk data sekolah lain). */
    public static <T> ResponseEntity<Response<T>> forbidden() {
        Response<T> r = new Response<>();
        r.setResponseCode(ResponseCode.FORBIDDEN);
        r.setCode(403);
        r.setMessage("Akses ditolak");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(r);
    }

    public static <T> ResponseEntity<Response<T>> forbidden(String message) {
        Response<T> r = new Response<>();
        r.setResponseCode(ResponseCode.FORBIDDEN);
        r.setCode(403);
        r.setMessage(message);
        // ⚠️ Perbaikan: sebelumnya ResponseEntity.ok(...) → HTTP 200 meski body 403.
        // Itu memaksa FE memeriksa body untuk membedakan sukses/gagal (rawan bug).
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(r);
    }

    /** 400 — permintaan tidak valid secara operasional. */
    public static <T> ResponseEntity<Response<T>> badRequest(String message) {
        Response<T> r = new Response<>();
        r.setCode(400);
        r.setMessage(message);
        return ResponseEntity.badRequest().body(r);
    }

    /** 409 — konflik (mis. saldo kurang, idempotency bentrok). */
    public static <T> ResponseEntity<Response<T>> conflict(String message) {
        Response<T> r = new Response<>();
        r.setCode(409);
        r.setResponseCode(ResponseCode.CONFLICT);
        r.setMessage(message);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(r);
    }

    /**
     * 429 — terlalu banyak permintaan (rate limit, SECURITY.md §7).
     *
     * <p>Klien disarankan menghormati header {@code Retry-After} yang
     * disertakan filter rate limit.
     */
    public static <T> ResponseEntity<Response<T>> tooManyRequests(String message) {
        Response<T> r = new Response<>();
        r.setCode(429);
        r.setResponseCode(ResponseCode.TOO_MANY_REQUESTS);
        r.setMessage(message);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(r);
    }

    /**
     * 500 — kesalahan server.
     *
     * <p><b>⚠️ Jangan pakai varian {@code serverError(Exception)} (B22):</b>
     * varian itu membocorkan {@code e.getMessage()} ke klien (bisa memuat detail
     * SQL/stack internal). Selalu kirim pesan generik ke klien dan catat detail
     * asli lewat {@code ErrorLogger} di server.
     */
    public static <T> ResponseEntity<Response<T>> serverError(Exception e) {
        // B22: pesan generik — detail asli HANYA di log server, bukan ke klien.
        return serverError("Terjadi kesalahan pada server");
    }

    public static <T> ResponseEntity<Response<T>> serverError(String message) {
        Response<T> r = new Response<>();
        r.setResponseCode(ResponseCode.SERVER_ERROR);
        r.setMessage(message);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(r);
    }

    /**
     * ⚠️ <b>DIHAPUS dari pemakaian (B22).</b> Method ini membocorkan
     * {@code e.getMessage()} (detail SQL) ke klien. {@code GlobalExceptionHandler}
     * sudah memetakan {@code DataIntegrityViolationException} → 409 dan exception
     * lain → 500 dengan pesan generik. Dipertahankan hanya agar tak ada yang
     * menyalin polanya — <b>jangan dipakai</b>.
     *
     * @deprecated bocor detail DB ke klien; pakai {@link #conflict(String)} untuk
     * pelanggaran integritas atau {@link #serverError(String)} untuk 500 generik.
     */
    @Deprecated(since = "audit-B22", forRemoval = true)
    public static <T> ResponseEntity<Response<T>> databaseError(Exception e) {
        Response<T> r = new Response<>();
        r.setResponseCode(ResponseCode.DATABASE_ERROR);
        r.setMessage("Terjadi kesalahan pada server");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(r);
    }

    /** 404 — endpoint tidak ada. */
    public static <T> ResponseEntity<Response<T>> noEndpoint(String message) {
        Response<T> r = new Response<>();
        r.setCode(404);
        r.setResponseCode(ResponseCode.NO_END_POINT);
        r.setMessage(message);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(r);
    }

    // ────────────────────────────────────────────────────────────────
    // LAIN-LAIN
    // ────────────────────────────────────────────────────────────────

    public static <T> ResponseEntity<T> directResponse(T data) {
        return ResponseEntity.ok(data);
    }

    public static ResponseEntity<InputStreamResource> fileResponse(InputStreamResource inputStreamResource, MediaType mediaType) throws IOException {
        String filename = inputStreamResource.getFilename();
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline;filename=\"" + filename + "\"");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(mediaType)
                .body(inputStreamResource);
    }
}
