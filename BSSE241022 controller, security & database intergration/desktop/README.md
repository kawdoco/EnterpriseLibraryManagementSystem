# Smart Library as a desktop application

The app is still Spring Boot + Thymeleaf. Desktop mode adds:
* a **system-tray icon** (Open / Exit; the tooltip shows the reader state), and
* an **application window** (Edge or Chrome in "app mode": no tabs, no address bar).

Closing the window does NOT stop the server, so the RFID reader keeps working. Use the tray menu **Exit** to quit.
Starting the app a second time just reopens the window.

## Run during development (window + tray)
    set DESKTOP_MODE=true
    mvnw spring-boot:run

## Build a double-click app (Windows)
    desktop\build-windows.bat
creates `dist\SmartLibrary\SmartLibrary.exe` with its own Java runtime included.

## Configuration of the packaged app
MySQL must be installed and running on the computer. Set these once as permanent user environment variables
(open a NEW terminal afterwards):

    setx DB_PASSWORD  "your-db-password"
    setx ADMIN_USERNAME "admin"
    setx ADMIN_PASSWORD "a-strong-password"       (only needed for the very first start)
    setx SERIAL_PORT "COM3"                       (optional; leave unset for auto-detect)

Log file: `%USERPROFILE%\.smart-library\smart-library.log`
