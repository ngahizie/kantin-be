package com.asqi.scholia_kantin_be.payload.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Penjaga regresi issue #361: {@link CommonResponse} adalah pabrik respons murni
 * — tidak boleh menyimpan state yang bisa berubah dan menyebar ke seluruh JVM
 * (dulu {@code static boolean validasiHttp400}, yang membuat test saling
 * memengaruhi tergantung urutan eksekusi).
 */
class CommonResponseStaticStateTest {

    @Test
    @DisplayName("CommonResponse tidak punya field static yang bisa diubah")
    void tidakAdaStaticMutable() {
        List<String> pelanggaran = new ArrayList<>();
        for (Field f : CommonResponse.class.getDeclaredFields()) {
            int m = f.getModifiers();
            if (Modifier.isStatic(m) && !Modifier.isFinal(m)) {
                pelanggaran.add(f.getName());
            }
        }

        assertThat(pelanggaran)
                .as("field static non-final pada CommonResponse")
                .isEmpty();
    }
}
