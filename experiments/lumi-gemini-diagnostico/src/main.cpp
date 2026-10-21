#include <Arduino.h>
#include <WiFi.h>
#include <WiFiMulti.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include <Preferences.h>
#include <ArduinoJson.h>
#include <time.h>

// PROVA DE CONCEITO: apenas Wi-Fi + pedidos de TEXTO à API Gemini.
// Não inicializa áudio, ecrã nem wake word: NÃO FLASHAR NA LUMI DE PRODUÇÃO.

static constexpr char kModel[] = "gemini-3.5-flash-lite";
static constexpr char kEndpoint[] =
    "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent";
static const char kRootCA[] = R"PEM(-----BEGIN CERTIFICATE-----
MIIFVzCCAz+gAwIBAgINAgPlk28xsBNJiGuiFzANBgkqhkiG9w0BAQwFADBHMQsw
CQYDVQQGEwJVUzEiMCAGA1UEChMZR29vZ2xlIFRydXN0IFNlcnZpY2VzIExMQzEU
MBIGA1UEAxMLR1RTIFJvb3QgUjEwHhcNMTYwNjIyMDAwMDAwWhcNMzYwNjIyMDAw
MDAwWjBHMQswCQYDVQQGEwJVUzEiMCAGA1UEChMZR29vZ2xlIFRydXN0IFNlcnZp
Y2VzIExMQzEUMBIGA1UEAxMLR1RTIFJvb3QgUjEwggIiMA0GCSqGSIb3DQEBAQUA
A4ICDwAwggIKAoICAQC2EQKLHuOhd5s73L+UPreVp0A8of2C+X0yBoJx9vaMf/vo
27xqLpeXo4xL+Sv2sfnOhB2x+cWX3u+58qPpvBKJXqeqUqv4IyfLpLGcY9vXmX7w
Cl7raKb0xlpHDU0QM+NOsROjyBhsS+z8CZDfnWQpJSMHobTSPS5g4M/SCYe7zUjw
TcLCeoiKu7rPWRnWr4+wB7CeMfGCwcDfLqZtbBkOtdh+JhpFAz2weaSUKK0Pfybl
qAj+lug8aJRT7oM6iCsVlgmy4HqMLnXWnOunVmSPlk9orj2XwoSPwLxAwAtcvfaH
szVsrBhQf4TgTM2S0yDpM7xSma8ytSmzJSq0SPly4cpk9+aCEI3oncKKiPo4Zor8
Y/kB+Xj9e1x3+naH+uzfsQ55lVe0vSbv1gHR6xYKu44LtcXFilWr06zqkUspzBmk
MiVOKvFlRNACzqrOSbTqn3yDsEB750Orp2yjj32JgfpMpf/VjsPOS+C12LOORc92
wO1AK/1TD7Cn1TsNsYqiA94xrcx36m97PtbfkSIS5r762DL8EGMUUXLeXdYWk70p
aDPvOmbsB4om3xPXV2V4J95eSRQAogB/mqghtqmxlbCluQ0WEdrHbEg8QOB+DVrN
VjzRlwW5y0vtOUucxD/SVRNuJLDWcfr0wbrM7Rv1/oFB2ACYPTrIrnqYNxgFlQID
AQABo0IwQDAOBgNVHQ8BAf8EBAMCAYYwDwYDVR0TAQH/BAUwAwEB/zAdBgNVHQ4E
FgQU5K8rJnEaK0gnhS9SZizv8IkTcT4wDQYJKoZIhvcNAQEMBQADggIBAJ+qQibb
C5u+/x6Wki4+omVKapi6Ist9wTrYggoGxval3sBOh2Z5ofmmWJyq+bXmYOfg6LEe
QkEzCzc9zolwFcq1JKjPa7XSQCGYzyI0zzvFIoTgxQ6KfF2I5DUkzps+GlQebtuy
h6f88/qBVRRiClmpIgUxPoLW7ttXNLwzldMXG+gnoot7TiYaelpkttGsN/H9oPM4
7HLwEXWdyzRSjeZ2axfG34arJ45JK3VmgRAhpuo+9K4l/3wV3s6MJT/KYnAK9y8J
ZgfIPxz88NtFMN9iiMG1D53Dn0reWVlHxYciNuaCp+0KueIHoI17eko8cdLiA6Ef
MgfdG+RCzgwARWGAtQsgWSl4vflVy2PFPEz0tv/bal8xa5meLMFrUKTX5hgUvYU/
Z6tGn6D/Qqc6f1zLXbBwHSs09dR2CQzreExZBfMzQsNhFRAbd03OIozUhfJFfbdT
6u9AWpQKXCBfTkBdYiJ23//OYb2MI3jSNwLgjt7RETeJ9r/tSQdirpLsQBqvFAnZ
0E6yove+7u7Y/9waLd64NnHi/Hm3lCXRSHNboTXns5lndcEZOitHTtNCjv0xyBZm
2tIMPNuzjsmhDYAPexZ3FL//2wmUspO8IFgV6dtxQ/PeEMMA3KgqlbbC1j+Qa3bb
bP6MvPJwNQzcmRk13NfIRmPVNnGuV/u3gm3c
-----END CERTIFICATE-----
)PEM";

Preferences preferences;
WiFiMulti wifiMulti;
String wifiName1, wifiPass1, wifiName2, wifiPass2, apiKey;
String lineBuffer;
unsigned long lastWifiCheck = 0;
bool wifiConfigured = false;

void connectWifi() {
  wifiMulti = WiFiMulti();
  wifiConfigured = false;
  if (wifiName1.length()) {
    wifiMulti.addAP(wifiName1.c_str(), wifiPass1.c_str());
    wifiConfigured = true;
  }
  if (wifiName2.length()) {
    wifiMulti.addAP(wifiName2.c_str(), wifiPass2.c_str());
    wifiConfigured = true;
  }
  if (!wifiConfigured) {
    Serial.println("Sem Wi-Fi. Introduz WIFI1 ssid|password e RESTART.");
    return;
  }
  WiFi.mode(WIFI_STA);
  Serial.print("A ligar ao Wi-Fi");
  unsigned long start = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - start < 20000) {
    wifiMulti.run();
    Serial.print('.');
    delay(300);
  }
  Serial.println();
  if (WiFi.status() == WL_CONNECTED) {
    Serial.print("Wi-Fi OK. IP: ");
    Serial.println(WiFi.localIP());
  } else {
    Serial.println("Wi-Fi indisponível. Verifica as redes.");
  }
}

void sayStatus() {
  Serial.printf("Wi-Fi: %s | modelo: %s | API: %s\n",
                WiFi.status() == WL_CONNECTED ? "ligado" : "desligado",
                kModel, apiKey.isEmpty() ? "por configurar" : "configurada");
}

void askGemini(const String& question) {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("ERRO: Wi-Fi desligado.");
    return;
  }
  if (apiKey.isEmpty()) {
    Serial.println("ERRO: falta KEY <chave_api>.");
    return;
  }

  // Certificados HTTPS requerem relógio sincronizado.
  if (time(nullptr) < 1700000000) {
    Serial.println("A sincronizar relógio NTP...");
    configTime(0, 0, "pool.ntp.org", "time.google.com");
    unsigned long t0 = millis();
    while (time(nullptr) < 1700000000 && millis() - t0 < 12000) {
      delay(250);
    }
    if (time(nullptr) < 1700000000) {
      Serial.println("ERRO: relógio sem hora válida para TLS.");
      return;
    }
  }

  WiFiClientSecure tls;
  tls.setCACert(kRootCA);
  HTTPClient http;
  http.setTimeout(25000);
  if (!http.begin(tls, kEndpoint)) {
    Serial.println("ERRO: não foi possível abrir HTTPS.");
    return;
  }
  http.addHeader("Content-Type", "application/json");
  http.addHeader("x-goog-api-key", apiKey);

  JsonDocument request;
  request["contents"][0]["parts"][0]["text"] =
      "Responde em português de Portugal, em tom natural e numa frase curta, "
      "como a assistente LUMI. " + question;
  String body;
  serializeJson(request, body);

  Serial.println("A consultar o Gemini...");
  int status = http.POST(body);
  if (status == HTTP_CODE_OK) {
    JsonDocument response;
    DeserializationError error = deserializeJson(response, http.getStream());
    if (error) {
      Serial.printf("ERRO JSON: %s\n", error.c_str());
    } else {
      String answer = response["candidates"][0]["content"]["parts"][0]["text"] | "";
      Serial.println(answer.isEmpty() ? "Sem texto na resposta." : answer);
    }
  } else {
    Serial.printf("ERRO Gemini HTTP %d\n", status);
    String error = http.getString();
    // Nunca imprimir tokens, credenciais ou resposta extensa.
    if (error.length() > 240) error = error.substring(0, 240);
    Serial.println(error);
  }
  http.end();
}

void handleCommand(String command) {
  command.trim();
  if (command == "STATUS") {
    sayStatus();
  } else if (command == "RESTART") {
    ESP.restart();
  } else if (command.startsWith("WIFI1 ") || command.startsWith("WIFI2 ")) {
    bool second = command.startsWith("WIFI2 ");
    String data = command.substring(6);
    int separator = data.indexOf('|');
    if (separator <= 0 || separator == data.length() - 1) {
      Serial.println("Formato: WIFI1 ssid|password");
      return;
    }
    preferences.putString(second ? "ssid2" : "ssid1", data.substring(0, separator));
    preferences.putString(second ? "pass2" : "pass1", data.substring(separator + 1));
    Serial.println("Rede guardada. Executa RESTART.");
  } else if (command.startsWith("KEY ")) {
    String key = command.substring(4);
    key.trim();
    if (key.isEmpty()) {
      Serial.println("Chave vazia.");
    } else {
      preferences.putString("gemini", key);
      apiKey = key;
      Serial.println("Chave guardada (não apresentada).");
    }
  } else if (command.startsWith("ASK ")) {
    askGemini(command.substring(4));
  } else {
    Serial.println("Comandos: STATUS, WIFI1, WIFI2, KEY, ASK, RESTART.");
  }
}

void setup() {
  Serial.begin(115200);
  delay(1700);
  preferences.begin("lumi", false);
  wifiName1 = preferences.getString("ssid1", "");
  wifiPass1 = preferences.getString("pass1", "");
  wifiName2 = preferences.getString("ssid2", "");
  wifiPass2 = preferences.getString("pass2", "");
  apiKey = preferences.getString("gemini", "");
  Serial.println("\nLUMI GEMINI — DIAGNÓSTICO DE TEXTO (NÃO É O FIRMWARE DA DESK)");
  connectWifi();
  sayStatus();
  Serial.println("Comandos: STATUS, WIFI1 ssid|password, WIFI2 ssid|password, KEY chave, ASK pergunta, RESTART.");
}

void loop() {
  while (Serial.available()) {
    char c = static_cast<char>(Serial.read());
    if (c == '\n' || c == '\r') {
      if (!lineBuffer.isEmpty()) {
        handleCommand(lineBuffer);
        lineBuffer.clear();
      }
    } else if (lineBuffer.length() < 512) {
      lineBuffer += c;
    }
  }
  if (wifiConfigured && WiFi.status() != WL_CONNECTED &&
      millis() - lastWifiCheck > 15000) {
    lastWifiCheck = millis();
    wifiMulti.run();
  }
  delay(15);
}
