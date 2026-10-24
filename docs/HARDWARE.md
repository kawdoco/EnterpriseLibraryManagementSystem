# Hardware guide: Arduino Uno + PN532

## 1. Wiring (I2C)
| PN532 | Arduino Uno |
|-------|-------------|
| SDA   | A4          |
| SCL   | A5          |
| VCC   | 5V          |
| GND   | GND         |
| IRQ   | D2          |
| RESET | D3          |

Set the PN532 board's mode switches to **I2C** (commonly SEL0 = ON, SEL1 = OFF; check the silkscreen of your board).
If your working sketch used other IRQ/RESET pins, change `PN532_IRQ` / `PN532_RESET` at the top of the firmware to match.

## 2. Upload the firmware
1. Arduino IDE -> Library Manager -> install **Adafruit PN532**.
2. Open `arduino/pn532_reader/pn532_reader.ino`, board **Arduino Uno**, pick the COM port, **Upload**.
3. (Optional) Serial Monitor at 9600 baud should show `STATUS:READY`, then `HB` every 2 s, and `UID:...` when you tap a card.
4. **Close the Serial Monitor.** Only one program can use a COM port at a time; if the IDE holds it, the application cannot connect.

The backend also understands the old example sketch (`UID Value: 0xE1 0xCE ...`), so scanning works before re-flashing, but the new firmware adds heartbeats and one-message-per-presentation.

## 3. How the pieces communicate
```
 card/tag --RF--> PN532 --I2C--> Arduino --USB serial 9600 baud--> Spring Boot (SerialPortService)
                                                                        | RfidScanEvent (Spring event)
                                                                        v
 Browser (Thymeleaf page + JS) <--SSE / JSON-- RfidApiController <-- RfidCounterService --> BorrowService --> MySQL
```
Browsers cannot safely open a serial port on their own (the Web Serial API needs Chrome/Edge, a per-visit permission prompt and
HTTPS or localhost, and cannot work for several screens). Therefore the Java backend owns the port and the page only displays results.

## 4. Using the counter
* Open **RFID Counter** from the sidebar and keep it open. **Scans only borrow/return while a counter page is open**, so tapping a tag while editing a member can never lend a book by accident.
* Register cards/tags first: on a member or book form press **Scan card/tag with reader**, tap it, then save.
* Borrow: tap member card, then the book. Return: tap the book with no member selected (Auto mode) or use Return mode.

## 5. Troubleshooting
| Symptom (reader badge / message) | Cause and fix |
|---|---|
| DISCONNECTED "No Arduino found" | Cable is charge-only or unplugged; try another cable/port. Choose the port manually on the counter page. |
| DISCONNECTED "Cannot open COMx" | Another program holds the port: close Arduino IDE Serial Monitor/Plotter. |
| CONNECTING for a long time | The Uno resets when the port opens (about 2 s). Wait, or press Reconnect. |
| NO DATA | Wrong sketch, or baud is not 9600 (`library.serial.baud-rate`). |
| READER ERROR | Arduino cannot see the PN532: check SDA/SCL/5V/GND, I2C switches, IRQ/RESET pin numbers. |
| ONLINE but scan "Unknown tag" | The tag is not registered. Use the Scan buttons on the Members/Books forms. |
| Scan seen in log but nothing happens | No counter page is open (by design), or the same UID was repeated inside the duplicate window (`library.serial.duplicate-window-ms`, default 2000). |
| Works in Arduino IDE but not in the app | `SERIAL_ENABLED=false` still set in the environment. Unset it or set it to `true`. |
