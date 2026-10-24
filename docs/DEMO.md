# Demo guide (5 minutes)

## Start
    cd library-system
    .\mvnw spring-boot:run
Open http://localhost:8082. All settings live in `config/application.properties` (nothing to type in the terminal).

## Two logins
| Login | Sees | Use it to show |
|-------|------|----------------|
| `staff` | Dashboard, RFID Counter, Members, Books, Transactions, Overdue | the everyday librarian view: simple |
| `admin` | everything above + Fines, Payments, Blacklisted, Reports/audit log, deactivate/blacklist actions | the manager view |
(The passwords are the ones in `config/application.properties`.)

## Script
1. **Problem:** libraries lose time on manual lending. We use NFC cards for members and RFID tags for books.
2. **Architecture (one sentence):** Arduino + PN532 -> USB -> Spring Boot (owns the serial port) -> MySQL; the browser shows results live.
3. **Register** a member and a book: press *Scan card/tag with reader* and tap it (the UID fills in).
4. **Borrow:** RFID Counter shows one big panel that always says what to do next. Tap the member card (it shows the name), then the book: "Successfully Borrowed", due in 14 days.
5. **Return:** tap the book: "Successfully Returned". A late book shows the days late and the fine.
6. **Fines (admin):** show Outstanding fines, Blacklisted and Reports. Rule: LKR 20 per late day; blacklisted above LKR 500.
7. **Security talking point:** log in as `staff` and try opening `/reports`: access is refused.

## If something goes wrong
* Reader badge not green: close the Arduino IDE Serial Monitor, press *Reconnect* (Advanced on the counter page).
* No reader at all: Advanced -> "Type a UID instead of scanning" runs the same workflow.


## Reset the data before each demo
Everything is stored in MySQL (database `smart_library`). Two scripts in the `database` folder clear it. Run one of them
**before** you start the application (or press *Clear* on the counter afterwards).

| Script | What it does | Use it when |
|--------|--------------|-------------|
| `03_reset_loans_only.sql` | Removes all loans, fines, payments and the audit log; every book is AVAILABLE again; nobody is blacklisted. **Keeps** members and books. | You want to repeat "borrow, return, fine" with the same people and books. |
| `04_reset_everything.sql` | Also removes all members and books. **Keeps** the admin and staff logins. | You want to start from an empty library and register everything live. |

How to run (either way):
* **MySQL Workbench:** File -> Open SQL Script -> choose the file -> press the lightning button.
* **Terminal:**
      mysql -u library_user -p smart_library < database\04_reset_everything.sql
  (if `mysql` is not recognised, use the full path, e.g. `"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"`).

After a full reset the dashboard shows zeros and the green "Welcome" checklist, which is a nice way to open the demo.
These scripts are deliberately NOT a button in the application, so nobody can wipe the data by accident.
