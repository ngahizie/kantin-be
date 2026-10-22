package com.asqi.scholia_kantin_be.component;

import com.asqi.scholia_kantin_be.component.exception.*;
import com.asqi.scholia_kantin_be.component.logging.ErrorLogger;
import com.asqi.scholia_kantin_be.payload.response.CommonResponse;
import com.asqi.scholia_kantin_be.payload.response.SimpleResponse;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Penanganan exception terpusat (parity {@code admin-be}, dengan perbaikan).
 *
 * <p><b>Perbaikan dari admin-be:</b>
 * <ul>
 *   <li>{@code ConstraintViolationException} (validasi {@code @Validated} pada
 *       parameter) ditangani → 400, bukan 500.</li>
 *   <li>{@code DataIntegrityViolationException} (mis. bentrok UNIQUE
 *       {@code idempotency_key}, CHECK saldo minus) dipetakan → 409 dengan pesan
 *       ramah, bukan 500 bocor SQL.</li>
 *   <li>{@code AccessDeniedException} RBAC → 403 (sebelumnya jatuh ke 500).</li>
 * </ul>
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private SimpleResponse buildSimpleResponse(String... messages) {
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.setStatus(0);
        simpleResponse.setMessage(new ArrayList<>(Arrays.asList(messages)));
        return simpleResponse;
    }

    // ────────────────────────────────────────────────────────────────
    // VALIDASI INPUT → HTTP 400 (issue #251)
    //
    // Dulu error validasi dijawab HTTP 200 (body `status:0` untuk binding
    // dan `code=100` untuk body). Akibatnya klien tak bisa membedakan
    // sukses dari gagal lewat status code, dan request cacat pun "lolos"
    // sebagai 200 sehingga jalur RBAC-nya tak pernah diuji.
    //
    // Sekarang: HTTP 400 + body `{code, message, timestamp}` — bentuk yang
    // sudah dibaca FE tetap dipertahankan, hanya status HTTP-nya yang
    // diperbaiki. `kantin.validation.http-400=false` mengembalikan seluruh
    // respons validasi ke HTTP 200 (saklar mundur, lihat ValidasiContractConfig).
    // ────────────────────────────────────────────────────────────────

    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<?> handleServletRequestBindingException(ServletRequestBindingException ex) {
        return CommonResponse.badRequest("Permintaan tidak lengkap: " + ex.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<?> handleMissingParam(MissingServletRequestParameterException ex) {
        return CommonResponse.badRequest("Parameter wajib tidak ada: " + ex.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return CommonResponse.badRequest(ex.getName() + " Type Data Salah");
    }

    /** Validasi parameter kendali (@RequestParam/@PathVariable) → 400. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<?> handleMethodValidation(HandlerMethodValidationException ex) {
        Map<String, String> pesan = new LinkedHashMap<>();
        for (ParameterValidationResult hasil : ex.getParameterValidationResults()) {
            String nama = hasil.getMethodParameter().getParameterName();
            for (MessageSourceResolvable galat : hasil.getResolvableErrors()) {
                pesan.put(nama, galat.getDefaultMessage());
            }
        }
        return CommonResponse.validationError(pesan);
    }

    /** Constraint jakarta.validation pada parameter → 400. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<?> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> pesan = new LinkedHashMap<>();
        for (ConstraintViolation<?> pelanggaran : ex.getConstraintViolations()) {
            pesan.put(pelanggaran.getPropertyPath().toString(), pelanggaran.getMessage());
        }
        return CommonResponse.validationError(pesan);
    }

    /** Body JSON rusak/tidak terbaca → 400 (dulu jatuh ke 500 generik). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleBodyTidakTerbaca(HttpMessageNotReadableException ex) {
        return CommonResponse.badRequest("Body permintaan tidak valid");
    }

    // ────────────────────────────────────────────────────────────────
    // DOMAIN
    // ────────────────────────────────────────────────────────────────

    /** Data tidak ada ATAU milik sekolah lain → 404 (PRD §11.4). */
    @ExceptionHandler(NotFoundEntity.class)
    public ResponseEntity<?> notFoundEntityExceptionHandler(NotFoundEntity e) {
        ErrorLogger.printFilteredStackTrace(e);
        return CommonResponse.noData(e.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<?> unauthorizedExceptionHandler(UnauthorizedException e) {
        ErrorLogger.printFilteredStackTrace(e);
        return CommonResponse.unauthenticated(e.getMessage());
    }

    @ExceptionHandler(JWTExpiredException.class)
    public ResponseEntity<?> handleJWTExpiredException(JWTExpiredException ex) {
        return CommonResponse.unauthenticated("Token kedaluwarsa");
    }

    @ExceptionHandler(TokenRefreshException.class)
    public ResponseEntity<?> handleTokenRefreshException(TokenRefreshException ex) {
        return CommonResponse.unauthenticated(ex.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<?> forbiddenExceptionHandler(ForbiddenException e) {
        ErrorLogger.printFilteredStackTrace(e);
        return CommonResponse.forbidden(e.getMessage());
    }

    @ExceptionHandler(InvalidOperationException.class)
    public ResponseEntity<?> invalidOperationExceptionHandler(InvalidOperationException e) {
        ErrorLogger.printFilteredStackTrace(e);
        return CommonResponse.badRequest(e.getMessage());
    }

    @ExceptionHandler({ConflictException.class, IdempotencyConflictException.class})
    public ResponseEntity<?> conflictExceptionHandler(RuntimeException e) {
        ErrorLogger.printFilteredStackTrace(e);
        return CommonResponse.conflict(e.getMessage());
    }

    // ────────────────────────────────────────────────────────────────
    // KEAMANAN
    // ────────────────────────────────────────────────────────────────

    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<?> expiredJwtExceptionHandler(ExpiredJwtException e) {
        return CommonResponse.unauthenticated("Token kedaluwarsa");
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<SimpleResponse> handleBadCredentialsException(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(buildSimpleResponse("Kredensial tidak valid"));
    }

    /** RBAC gagal → 403 (bukan 500 seperti admin-be). */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> handleAccessDenied(AccessDeniedException ex) {
        return CommonResponse.forbidden("Anda tidak berhak mengakses aksi ini");
    }

    // ────────────────────────────────────────────────────────────────
    // VALIDASI INPUT
    // ────────────────────────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationException(MethodArgumentNotValidException ex) {
        BindingResult result = ex.getBindingResult();
        List<FieldError> fieldErrors = result.getFieldErrors();

        Map<String, String> validationMessages = new HashMap<>();
        for (FieldError fieldError : fieldErrors) {
            String errorMessage;
            if (Objects.equals(fieldError.getCode(), "typeMismatch")) {
                errorMessage = "Type data salah";
            } else {
                errorMessage = fieldError.getDefaultMessage();
            }
            validationMessages.put(fieldError.getField(), errorMessage);
        }
        return CommonResponse.validationError(validationMessages);
    }

    // ────────────────────────────────────────────────────────────────
    // DATABASE
    // ────────────────────────────────────────────────────────────────

    /**
     * Pelanggaran integritas DB → 409 dengan pesan ramah.
     *
     * <p>Validator terakhir ada di DB (CHECK saldo tidak minus, UNIQUE
     * idempotency_key). Bila kode lupa menangkapnya, jangan bocorkan SQL — ubah
     * jadi konflik yang bisa dipahami.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> handleDataIntegrity(DataIntegrityViolationException ex) {
        ErrorLogger.printFilteredStackTrace(ex);
        return CommonResponse.conflict("Operasi ditolak oleh aturan data (kemungkinan data ganda atau melanggar batas)");
    }

    // ────────────────────────────────────────────────────────────────
    // FALLBACK
    // ────────────────────────────────────────────────────────────────

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<?> noResourceFoundExceptionHandler(NoResourceFoundException e) {
        return CommonResponse.noEndpoint("endpoint tidak ditemukan: " + e.getResourcePath());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> maxUploadSizeExceededException(MaxUploadSizeExceededException ex) {
        return CommonResponse.badRequest(
                "Jumlah upload file terlalu besar, harus kurang dari batas yang ditentukan");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> exceptionHandler(Exception e) {
        ErrorLogger.printFilteredStackTrace(e);
        return CommonResponse.serverError("Terjadi kesalahan pada server");
    }
}
