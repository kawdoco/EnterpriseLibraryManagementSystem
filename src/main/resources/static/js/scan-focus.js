// Keeps focus on the RFID input so a USB HID scanner (which just "types"
// the tag and hits Enter) can be used continuously without clicking back
// into the field between scans.
document.addEventListener('DOMContentLoaded', () => {
    const rfidInput = document.getElementById('rfidTag');
    if (!rfidInput) {
        return;
    }
    rfidInput.focus();
    document.addEventListener('click', (event) => {
        if (event.target.tagName !== 'SELECT' && event.target.tagName !== 'OPTION') {
            setTimeout(() => rfidInput.focus(), 150);
        }
    });
});
