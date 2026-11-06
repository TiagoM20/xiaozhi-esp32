#include "lumi_sd_media.h"

#include <algorithm>
#include <cerrno>
#include <cstdio>
#include <cstring>
#include <dirent.h>
#include <memory>
#include <sys/stat.h>
#include <vector>

#include <driver/sdmmc_host.h>
#include <esp_err.h>
#include <esp_heap_caps.h>
#include <esp_log.h>
#include <esp_system.h>
#include <esp_vfs_fat.h>
#include <freertos/FreeRTOS.h>
#include <freertos/task.h>
#include <sdmmc_cmd.h>

#include "application.h"
#include "audio_service.h"
#include "board.h"
#include "device_state.h"
#include "lang_config.h"
#include "lvgl_image.h"
#include "ogg_demuxer.h"

static constexpr const char* kTag = "LUMI_SD";
static constexpr const char* kRoot = "/sdcard/LUMI/";
static constexpr size_t kAudioFileLimit = 64 * 1024 * 1024;
static constexpr size_t kImageFileLimit = 360 * 1024;

bool LumiSdMedia::SafeName(const std::string& value) {
    if (value.empty() || value.size() > 62 || value[0] == '.' ||
        value.find("..") != std::string::npos) {
        return false;
    }
    for (const unsigned char c : value) {
        if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
              (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.')) {
            return false;
        }
    }
    return true;
}

bool LumiSdMedia::AllowedExtension(const std::string& name, bool image) {
    auto pos = name.rfind('.');
    if (pos == std::string::npos) return false;
    std::string suffix = name.substr(pos);
    std::transform(suffix.begin(), suffix.end(), suffix.begin(),
                   [](unsigned char x) { return static_cast<char>(std::tolower(x)); });
    return image ? (suffix == ".jpg" || suffix == ".jpeg" || suffix == ".png")
                 : suffix == ".ogg";
}

void LumiSdMedia::SetError(const std::string& error) {
    std::lock_guard<std::mutex> guard(mutex_);
    last_error_ = error;
    ESP_LOGW(kTag, "%s", error.c_str());
}

std::string LumiSdMedia::Status() {
    // No GPIO setup, IO, probing or mounting here; must be safe even at boot.
    std::lock_guard<std::mutex> guard(mutex_);
    std::string info = mounted_ ? "Cartao FAT montado em /sdcard"
                                : "microSD desligado. Pede 'ativa o cartao SD' para testar.";
    info += "; ultima mensagem: " + last_error_;
    if (mounted_ && card_ != nullptr) {
        const uint64_t mb = (static_cast<uint64_t>(card_->csd.capacity) *
                             card_->csd.sector_size) / (1024 * 1024);
        info += "; capacidade aproximada: " + std::to_string(mb) + " MB";
    }
    return info;
}

std::string LumiSdMedia::Mount() {
    // Called ONLY from explicit user-requested MCP tool, AFTER boot.
    std::lock_guard<std::mutex> guard(mutex_);
    if (mounted_) return "O microSD ja esta montado. Podes pedir para listar os sons e imagens.";

    ESP_LOGI(kTag, "User explicitly requested SDMMC mount (never automatic)");
    sdmmc_host_t host = SDMMC_HOST_DEFAULT();
    host.max_freq_khz = 10000;

    // Pin mapping from Waveshare's own ESP-IDF 1.54 BSP, 1-bit slot mode.
    sdmmc_slot_config_t slot = SDMMC_SLOT_CONFIG_DEFAULT();
    slot.width = 1;
    slot.clk = GPIO_NUM_16;
    slot.cmd = GPIO_NUM_15;
    slot.d0 = GPIO_NUM_17;
    slot.d1 = GPIO_NUM_NC;
    slot.d2 = GPIO_NUM_NC;
    slot.d3 = GPIO_NUM_NC;
    slot.cd = SDMMC_SLOT_NO_CD;
    slot.wp = SDMMC_SLOT_NO_WP;
    slot.flags = 0;

    esp_vfs_fat_sdmmc_mount_config_t config = {};
    config.format_if_mount_failed = false; // NEVER erase the user's card.
    config.max_files = 5;
    config.allocation_unit_size = 16 * 1024;

    esp_err_t err = esp_vfs_fat_sdmmc_mount("/sdcard", &host, &slot, &config, &card_);
    if (err != ESP_OK) {
        card_ = nullptr;
        mounted_ = false;
        last_error_ = std::string("Falha ao montar microSD: ") + esp_err_to_name(err);
        ESP_LOGW(kTag, "%s; assistant may continue without SD", last_error_.c_str());
        return last_error_ + ". Podes preparar o cartao em FAT32 no LUMI Windows CORE.";
    }
    mounted_ = true;
    last_error_ = "microSD montado a pedido do utilizador";
    for (auto folder : {"/sdcard/LUMI", "/sdcard/LUMI/sons", "/sdcard/LUMI/audios",
                        "/sdcard/LUMI/imagens", "/sdcard/LUMI/diagnostico"}) {
        if (mkdir(folder, 0755) != 0 && errno != EEXIST) {
            ESP_LOGW(kTag, "Could not create %s, errno=%d", folder, errno);
        }
    }
    FILE* log = fopen("/sdcard/LUMI/diagnostico/arranques.txt", "a");
    if (log != nullptr) {
        fprintf(log, "sd mount requested; reset_reason=%d; psram_free=%u\n",
                (int)esp_reset_reason(),
                (unsigned)heap_caps_get_free_size(MALLOC_CAP_SPIRAM));
        fclose(log);
    }
    ESP_LOGI(kTag, "Optional microSD mounted after boot");
    return "microSD ativo. Pastas: sons, audios, imagens e diagnostico. "
           "Podes pedir 'lista sons do cartao' ou 'mostra imagem'.";
}

std::string LumiSdMedia::List(const std::string& folder) {
    if (folder != "sons" && folder != "audios" && folder != "imagens") {
        return "Pasta invalida. Escolhe sons, audios ou imagens.";
    }
    std::lock_guard<std::mutex> guard(mutex_);
    if (!mounted_) return "O microSD ainda nao esta montado. Pede 'ativa o cartao SD'.";
    const std::string path = std::string(kRoot) + folder;
    DIR* dir = opendir(path.c_str());
    if (!dir) return "Nao foi possivel listar a pasta " + folder;
    std::string names;
    size_t count = 0;
    struct dirent* item;
    while ((item = readdir(dir)) != nullptr && count < 20) {
        const std::string name(item->d_name);
        if (!SafeName(name) || !AllowedExtension(name, folder == "imagens")) continue;
        if (!names.empty()) names += ", ";
        names += name;
        ++count;
    }
    closedir(dir);
    if (count == 0) return "Ainda nao existem ficheiros compativeis na pasta " + folder;
    return "Ficheiros da pasta " + folder + ": " + names +
           (count == 20 ? ". Lista limitada a 20 entradas." : ".");
}

bool LumiSdMedia::WaitUntilIdle(int max_seconds) {
    for (int i = 0; i < max_seconds * 10; ++i) {
        if (cancel_playback_.load()) return false;
        if (Application::GetInstance().GetDeviceState() == kDeviceStateIdle) return true;
        vTaskDelay(pdMS_TO_TICKS(100));
    }
    return false;
}

void LumiSdMedia::MediaTask(void* context) {
    std::unique_ptr<MediaJob> job(static_cast<MediaJob*>(context));
    auto* self = job->self;
    if (self->WaitUntilIdle(23)) {
        if (job->image) self->DisplayPicture(job->path);
        else self->StreamOgg(job->path);
    } else {
        self->SetError("Tempo excedido a aguardar que a conversa termine.");
    }
    self->playing_.store(false);
    vTaskDelete(nullptr);
}

std::string LumiSdMedia::LaunchMedia(const std::string& path, bool image) {
    if (playing_.exchange(true)) {
        return "Ja existe um ficheiro em reproducao ou a ser mostrado.";
    }
    cancel_playback_.store(false);
    MediaJob* job = new (std::nothrow) MediaJob{this, path, image};
    if (job == nullptr) {
        playing_.store(false);
        return "Memoria insuficiente para iniciar o leitor.";
    }
    BaseType_t rc = xTaskCreate(MediaTask, "lumi_sd_media", 10240, job, 2, nullptr);
    if (rc != pdPASS) {
        delete job;
        playing_.store(false);
        return "Nao foi possivel criar a tarefa de media.";
    }
    return "Pedido recebido. Vou reproduzir ou mostrar quando terminar de falar.";
}

std::string LumiSdMedia::Play(const std::string& filename, bool long_audio) {
    if (!SafeName(filename) || !AllowedExtension(filename, false)) {
        return "Nome invalido: so e aceite um ficheiro .ogg da pasta indicada.";
    }
    {
        std::lock_guard<std::mutex> guard(mutex_);
        if (!mounted_) return "Ativa primeiro o microSD.";
    }
    const char* folder = long_audio ? "audios/" : "sons/";
    return LaunchMedia(std::string(kRoot) + folder + filename, false);
}

void LumiSdMedia::StreamOgg(const std::string& path) {
    FILE* file = fopen(path.c_str(), "rb");
    if (!file) {
        SetError("Nao foi possivel abrir o audio no SD: " + path);
        return;
    }
    if (fseek(file, 0, SEEK_END) != 0) {
        fclose(file);
        SetError("O ficheiro de audio nao permite leitura.");
        return;
    }
    const long file_length = ftell(file);
    rewind(file);
    if (file_length < 64 || static_cast<size_t>(file_length) > kAudioFileLimit) {
        fclose(file);
        SetError("Audio demasiado curto ou superior a 64 MB.");
        return;
    }

    auto& audio = Application::GetInstance().GetAudioService();
    OggDemuxer demuxer;
    demuxer.OnPacket([this, &audio](const uint8_t* bytes, int rate, int duration, size_t len) {
        if (cancel_playback_.load() ||
            Application::GetInstance().GetDeviceState() != kDeviceStateIdle) {
            cancel_playback_.store(true);
            return;
        }
        auto packet = std::make_unique<AudioStreamPacket>();
        packet->sample_rate = rate;
        packet->frame_duration = duration;
        packet->payload.assign(bytes, bytes + len);
        if (!audio.PushPacketToDecodeQueue(std::move(packet), true)) {
            cancel_playback_.store(true);
        }
    });
    demuxer.Reset();
    std::vector<uint8_t> block(4096);
    while (!cancel_playback_.load()) {
        const size_t bytes = fread(block.data(), 1, block.size(), file);
        if (bytes == 0) break;
        demuxer.Process(block.data(), bytes);
        vTaskDelay(pdMS_TO_TICKS(1));
    }
    const bool finished = demuxer.Finish();
    const bool cancelled = cancel_playback_.load();
    fclose(file);
    if (!finished && !cancelled) SetError("Ficheiro .ogg invalido ou incompleto.");
    ESP_LOGI(kTag, "SD Opus playback ended: complete=%d cancelled=%d", finished, cancelled);
}

std::string LumiSdMedia::ShowImage(const std::string& filename) {
    if (!SafeName(filename) || !AllowedExtension(filename, true)) {
        return "Imagem invalida; usa um JPG ou PNG com nome simples.";
    }
    {
        std::lock_guard<std::mutex> guard(mutex_);
        if (!mounted_) return "Ativa primeiro o microSD.";
    }
    return LaunchMedia(std::string(kRoot) + "imagens/" + filename, true);
}

void LumiSdMedia::DisplayPicture(const std::string& path) {
    FILE* file = fopen(path.c_str(), "rb");
    if (!file) { SetError("Imagem nao encontrada no cartao."); return; }
    if (fseek(file, 0, SEEK_END) != 0) {
        fclose(file);
        SetError("A imagem nao permite leitura.");
        return;
    }
    const long length = ftell(file);
    rewind(file);
    if (length < 16 || static_cast<size_t>(length) > kImageFileLimit) {
        fclose(file);
        SetError("A imagem deve ter no maximo 360 KB. Prepara-a no LUMI Windows CORE.");
        return;
    }
    void* data = heap_caps_malloc(length, MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT);
    if (!data) {
        fclose(file);
        SetError("Memoria insuficiente para a imagem.");
        return;
    }
    const size_t bytes = fread(data, 1, length, file);
    fclose(file);
    if (bytes != static_cast<size_t>(length)) {
        heap_caps_free(data);
        SetError("Falha ao ler a imagem.");
        return;
    }
    auto image = std::make_unique<LvglAllocatedImage>(data, bytes);
    if (!image->IsValid()) {
        SetError("O formato da imagem nao e suportado pelo visor.");
        return; // destructor releases PSRAM
    }
    auto* display = Board::GetInstance().GetDisplay();
    if (display == nullptr || !display->SupportsGuiOperations()) {
        SetError("O visor nao suporta mostrar imagens.");
        return;
    }
    display->SetPreviewImage(std::move(image));
}

std::string LumiSdMedia::Stop() {
    if (!playing_.load()) return "Nao ha reproducoes do cartao ativas.";
    cancel_playback_.store(true);
    if (Application::GetInstance().GetDeviceState() == kDeviceStateIdle) {
        Application::GetInstance().GetAudioService().ResetDecoder();
    }
    return "Leitura do cartao interrompida.";
}

void LumiSdMedia::TimerTask(void* context) {
    std::unique_ptr<TimerJob> job(static_cast<TimerJob*>(context));
    LumiSdMedia* self = job->self;
    for (int i = 0; i < job->seconds; ++i) {
        if (self->timer_generation_.load() != job->generation) {
            vTaskDelete(nullptr);
            return;
        }
        vTaskDelay(pdMS_TO_TICKS(1000));
    }
    if (self->timer_generation_.load() == job->generation) {
        Board::GetInstance().GetDisplay()->ShowNotification("LUMI: temporizador terminado", 6000);
        bool has_alarm = false;
        {
            std::lock_guard<std::mutex> guard(self->mutex_);
            if (self->mounted_) {
                struct stat st;
                has_alarm = stat("/sdcard/LUMI/sons/alarme.ogg", &st) == 0 &&
                            st.st_size > 64 && st.st_size < kAudioFileLimit;
            }
        }
        if (has_alarm && !self->playing_.load()) {
            self->LaunchMedia("/sdcard/LUMI/sons/alarme.ogg", false);
        } else if (self->WaitUntilIdle(20)) {
            Application::GetInstance().PlaySound(Lang::Sounds::OGG_SUCCESS);
        }
    }
    vTaskDelete(nullptr);
}

std::string LumiSdMedia::SetTimer(int seconds) {
    if (seconds < 1 || seconds > 14400) {
        return "O temporizador aceita de 1 segundo ate 4 horas.";
    }
    uint32_t gen = timer_generation_.fetch_add(1) + 1;
    auto* job = new (std::nothrow) TimerJob{this, gen, seconds};
    if (!job) return "Memoria insuficiente para o temporizador.";
    if (xTaskCreate(TimerTask, "lumi_sd_timer", 4096, job, 1, nullptr) != pdPASS) {
        delete job;
        return "Nao consegui iniciar o temporizador.";
    }
    return "Temporizador iniciado por " + std::to_string(seconds) +
           " segundos, com alarme local quando terminar.";
}

std::string LumiSdMedia::CancelTimer() {
    timer_generation_.fetch_add(1);
    return "Temporizador cancelado.";
}
