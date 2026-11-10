#pragma once
// Diagnostico apenas: memoria interna (RAM/DRAM) durante o arranque.
// Nao reinicia, apaga, grava na flash nem modifica a configuracao ESP-SR.
#include <esp_heap_caps.h>
#include <esp_log.h>

static inline void LumiHeapProbe(const char* phase) {
    // A assert em TLSF e na memoria interna. Evitamos percorrer 8MB de PSRAM
    // em cada etapa para reduzir risco de watchdog induzido pelo diagnostico.
    const bool ok = heap_caps_check_integrity(MALLOC_CAP_INTERNAL, true);
    ESP_LOGW("LUMI_HEAP_DIAG",
             "checkpoint=%s internal=%s free=%u largest=%u min=%u psram_free=%u",
             phase, ok ? "OK" : "CORRUPT",
             (unsigned)heap_caps_get_free_size(MALLOC_CAP_INTERNAL),
             (unsigned)heap_caps_get_largest_free_block(MALLOC_CAP_INTERNAL),
             (unsigned)heap_caps_get_minimum_free_size(MALLOC_CAP_INTERNAL),
             (unsigned)heap_caps_get_free_size(MALLOC_CAP_SPIRAM));
    if (!ok) ESP_LOGE("LUMI_HEAP_DIAG", "CORRUPT FIRST OBSERVED AT OR BEFORE %s", phase);
}
