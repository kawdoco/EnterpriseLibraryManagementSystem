/*
  Smart Library - PN532 USB serial reader
  Board : Arduino Uno          Reader: PN532 module in I2C mode
  Wiring: SDA -> A4, SCL -> A5, VCC -> 5V, GND -> GND, IRQ -> D2, RESET -> D3
          (if your working sketch uses different IRQ/RESET pin numbers, set them below to match)
  Library: "Adafruit PN532" (Library Manager)  -  Serial speed: 9600 baud

  PROTOCOL (one message per line, newline-terminated, nothing else is ever printed):
      UID:04A1B2C3D4    a card/tag was presented (uppercase hex, no separators)
      HB                heartbeat every 2 s, proves the reader is alive
      STATUS:READY      PN532 found and configured
      STATUS:ERROR      PN532 not found (check wiring and the I2C switch settings)

  A card is reported ONCE per presentation. Remove it for about a second, present it again,
  and it is reported again.
*/
#include <Wire.h>
#include <Adafruit_PN532.h>

#define PN532_IRQ   2
#define PN532_RESET 3

Adafruit_PN532 nfc(PN532_IRQ, PN532_RESET);   // hardware I2C: SDA=A4, SCL=A5 on the Uno

const unsigned long HEARTBEAT_MS   = 2000;   // how often HB is sent
const unsigned long CARD_ABSENT_MS = 1200;   // card must be gone this long before it counts as removed
const uint16_t      READ_TIMEOUT   = 100;    // ms to wait for a card in each poll

bool readerOk = false;
bool cardPresent = false;
uint8_t lastUid[7];
uint8_t lastUidLength = 0;
unsigned long lastSeenMs = 0;
unsigned long lastHeartbeatMs = 0;
unsigned long lastInitAttemptMs = 0;

bool initReader() {
  nfc.begin();
  uint32_t version = nfc.getFirmwareVersion();
  if (!version) return false;
  nfc.SAMConfig();                // configure the PN532 to read ISO14443A cards
  return true;
}

void sendUid(const uint8_t *uid, uint8_t length) {
  Serial.print(F("UID:"));
  for (uint8_t i = 0; i < length; i++) {
    if (uid[i] < 0x10) Serial.print('0');   // always two digits per byte
    Serial.print(uid[i], HEX);              // HEX prints uppercase
  }
  Serial.println();
}

void setup() {
  Serial.begin(9600);
  readerOk = initReader();
  Serial.println(readerOk ? F("STATUS:READY") : F("STATUS:ERROR"));
  lastInitAttemptMs = millis();
}

void loop() {
  unsigned long now = millis();

  if (!readerOk) {                              // keep reporting the problem and retry
    if (now - lastInitAttemptMs >= HEARTBEAT_MS) {
      lastInitAttemptMs = now;
      readerOk = initReader();
      Serial.println(readerOk ? F("STATUS:READY") : F("STATUS:ERROR"));
    }
    return;
  }

  uint8_t uid[7];
  uint8_t uidLength = 0;
  bool found = nfc.readPassiveTargetID(PN532_MIFARE_ISO14443A, uid, &uidLength, READ_TIMEOUT);

  if (found && uidLength > 0 && uidLength <= 7) {
    bool sameCard = cardPresent && uidLength == lastUidLength && memcmp(uid, lastUid, uidLength) == 0;
    lastSeenMs = now;
    if (!sameCard) {                            // new presentation: report exactly once
      sendUid(uid, uidLength);
      memcpy(lastUid, uid, uidLength);
      lastUidLength = uidLength;
      cardPresent = true;
    }
  } else if (cardPresent && (now - lastSeenMs) > CARD_ABSENT_MS) {
    cardPresent = false;                        // card removed: the next presentation is a new scan
  }

  if (now - lastHeartbeatMs >= HEARTBEAT_MS) {
    lastHeartbeatMs = now;
    Serial.println(F("HB"));
  }
}
