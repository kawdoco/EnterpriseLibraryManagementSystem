package com.library.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

/** Binds every {@code library.*} property. Business rules are configurable here, not hardcoded. */
@Validated
@ConfigurationProperties(prefix = "library")
public class LibraryProperties {

    @Min(1)
    private int borrowDays = 14;

    @Min(1)
    private int maxActiveLoans = 5;

    @DecimalMin("0.00")
    private BigDecimal fineRatePerDay = new BigDecimal("20.00");

    /** Member is blacklisted when outstanding fines are strictly GREATER than this value. */
    @DecimalMin("0.00")
    private BigDecimal blacklistThreshold = new BigDecimal("500.00");

    private String currency = "LKR";

    private final Contact contact = new Contact();
    private final Serial serial = new Serial();
    private final Reminders reminders = new Reminders();
    private final Admin admin = new Admin();
    private final Counter counter = new Counter();

    public int getBorrowDays() { return borrowDays; }
    public void setBorrowDays(int v) { this.borrowDays = v; }
    public int getMaxActiveLoans() { return maxActiveLoans; }
    public void setMaxActiveLoans(int v) { this.maxActiveLoans = v; }
    public BigDecimal getFineRatePerDay() { return fineRatePerDay; }
    public void setFineRatePerDay(BigDecimal v) { this.fineRatePerDay = v; }
    public BigDecimal getBlacklistThreshold() { return blacklistThreshold; }
    public void setBlacklistThreshold(BigDecimal v) { this.blacklistThreshold = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { this.currency = v; }
    public Contact getContact() { return contact; }
    public Serial getSerial() { return serial; }
    public Reminders getReminders() { return reminders; }
    public Admin getAdmin() { return admin; }
    public Counter getCounter() { return counter; }

    public static class Contact {
        private String name = "Smart Library";
        private String email = "";
        private String phone = "";
        public String getName() { return name; }
        public void setName(String v) { this.name = v; }
        public String getEmail() { return email; }
        public void setEmail(String v) { this.email = v; }
        public String getPhone() { return phone; }
        public void setPhone(String v) { this.phone = v; }
    }

    public static class Serial {
        private boolean enabled = true;
        private String port = "";
        private int baudRate = 9600;
        private long reconnectDelayMs = 5000;
        private long heartbeatTimeoutMs = 6000;
        private long duplicateWindowMs = 2000;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public String getPort() { return port; }
        public void setPort(String v) { this.port = v; }
        public int getBaudRate() { return baudRate; }
        public void setBaudRate(int v) { this.baudRate = v; }
        public long getReconnectDelayMs() { return reconnectDelayMs; }
        public void setReconnectDelayMs(long v) { this.reconnectDelayMs = v; }
        public long getHeartbeatTimeoutMs() { return heartbeatTimeoutMs; }
        public void setHeartbeatTimeoutMs(long v) { this.heartbeatTimeoutMs = v; }
        public long getDuplicateWindowMs() { return duplicateWindowMs; }
        public void setDuplicateWindowMs(long v) { this.duplicateWindowMs = v; }
    }

    public static class Reminders {
        private boolean enabled = true;
        private String cron = "0 0 8 * * *";
        private String zone = "Asia/Colombo";
        private String from = "";
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public String getCron() { return cron; }
        public void setCron(String v) { this.cron = v; }
        public String getZone() { return zone; }
        public void setZone(String v) { this.zone = v; }
        public String getFrom() { return from; }
        public void setFrom(String v) { this.from = v; }
    }

    public static class Admin {
        private String initialUsername = "";
        private String initialPassword = "";
        private String initialStaffUsername = "";
        private String initialStaffPassword = "";
        public String getInitialStaffUsername() { return initialStaffUsername; }
        public void setInitialStaffUsername(String v) { this.initialStaffUsername = v; }
        public String getInitialStaffPassword() { return initialStaffPassword; }
        public void setInitialStaffPassword(String v) { this.initialStaffPassword = v; }
        public String getInitialUsername() { return initialUsername; }
        public void setInitialUsername(String v) { this.initialUsername = v; }
        public String getInitialPassword() { return initialPassword; }
        public void setInitialPassword(String v) { this.initialPassword = v; }
    }

    public static class Counter {
        /** A scanned member card is forgotten after this many idle seconds. */
        private int memberTimeoutSeconds = 60;
        private int historySize = 25;
        public int getMemberTimeoutSeconds() { return memberTimeoutSeconds; }
        public void setMemberTimeoutSeconds(int v) { this.memberTimeoutSeconds = v; }
        public int getHistorySize() { return historySize; }
        public void setHistorySize(int v) { this.historySize = v; }
    }
}
