# Library Management System (Spring Boot MVC + RFID)

A production-ready RFID-enabled library management system built with Spring
Boot MVC, Spring Data JPA/Hibernate, MySQL, and Thymeleaf + Bootstrap 5.

## Features

- **RFID scan station**: a single input field toggles between borrowing and
  returning a book depending on its current status — works with USB HID
  keyboard-emulation RFID readers (no special driver needed) or a manual
  form/REST POST.
- **Automatic due dates**: every borrow gets a 14-day due date (configurable).
- **Automatic fine calculation**: `days late * daily rate` (default LKR 50/day),
  applied the moment a late book is scanned back in.
- **Automatic blacklisting**: a member is flagged `isBlacklisted = true` the
  moment a single fine exceeds the configured threshold (default LKR 500),
  and blacklisted members are blocked from borrowing until resolved.
- **Popular books analytics**: dashboard widget showing the top 5
  most-borrowed titles.
- **Seed data**: on first run against an empty database, a few demo users
  and books (with ready-to-scan RFID tags `RFID-0001`..`RFID-0005`) are
  created automatically so you can try it immediately.

## Tech stack

| Layer          | Technology                                  |
|----------------|----------------------------------------------|
| Language       | Java 17                                       |
| Framework      | Spring Boot 3.3.x (Web MVC)                   |
| Persistence    | Spring Data JPA + Hibernate                   |
| Database       | MySQL 8                                       |
| View layer     | Thymeleaf + Bootstrap 5                       |
| Build tool     | Maven                                         |
| Boilerplate    | Lombok                                        |

## Project structure

```
src/main/java/com/library/lms/
├── LmsApplication.java          # Spring Boot entry point
├── model/                       # JPA entities: User, Book, Transaction
├── repository/                  # Spring Data JPA repositories
├── service/                     # LibraryService interface + impl (business logic)
├── controller/                  # LibraryController (MVC web layer)
├── dto/                         # ScanResult DTO
└── config/                      # DataInitializer (demo data seeder)

src/main/resources/
├── application.properties       # DB, JPA, Thymeleaf, business-rule config
├── templates/index.html         # Bootstrap dashboard
└── static/                      # CSS + JS for the dashboard
```

## Prerequisites

- JDK 17+
- Maven 3.8+
- MySQL 8 running locally (or update the connection URL)

## Setup

1. Create the database (or let it auto-create — see below):
   ```sql
   CREATE DATABASE library_db;
   ```
2. Update credentials in `src/main/resources/application.properties` if
   they differ from the defaults (`root` with an empty password):
   ```properties
   spring.datasource.url=jdbc:mysql://localhost:3306/library_db?useSSL=false&serverTimezone=UTC&createDatabaseIfNotExist=true
   spring.datasource.username=root
   spring.datasource.password=
   ```
   `createDatabaseIfNotExist=true` means step 1 is optional on most local
   MySQL setups.

3. Build and run:
   ```bash
   mvn clean install
   mvn spring-boot:run
   ```
4. Open **http://localhost:8080/** in your browser.

## Using the scan station

The dashboard's RFID input stays focused, so you can either:

- Plug in a USB HID RFID reader — scanning a tag "types" it into the field
  and submits automatically (most readers send an Enter keystroke after the
  tag).
- Type/paste a tag manually (try `RFID-0001` from the seed data) and press
  Enter or click **Process Scan**.

Scanning an `AVAILABLE` book's tag borrows it for the selected member.
Scanning the same tag again (while it's `BORROWED`) returns it and applies a
late fine if the due date has passed.

## Configuration

Business rules are externalized in `application.properties`:

```properties
library.loan.duration-days=14
library.fine.daily-rate=50.0
library.fine.blacklist-threshold=500.0
```

## Notes for a real production deployment

- Replace `spring.jpa.hibernate.ddl-auto=update` with a proper migration
  tool (Flyway or Liquibase) and set it to `validate`.
- Externalize DB credentials via environment variables or a secrets
  manager rather than committing them to `application.properties`.
- Add authentication/authorization (e.g. Spring Security) to distinguish
  ADMIN vs MEMBER actions — the `role` field on `User` is already in place
  for this.
