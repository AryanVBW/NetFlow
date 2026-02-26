# Google Play Policy Compliance & Testing Documentation

**Last Updated: 2026-02-26**

## 1. Policy Compliance Overview

NetFlow Predict adheres to the Google Play Developer Program Policies. Below is a breakdown of how we meet specific requirements:

### 1.1. User Data & Privacy
*   **Personal and Sensitive Information:** We do not collect PII. All network logs are stored locally.
*   **Privacy Policy:** A comprehensive Privacy Policy is accessible within the app (planned) and linked in the store listing. It explicitly discloses the use of `VpnService` for local monitoring.
*   **Prominent Disclosure:** A consent dialog appears on the first launch, explaining the use of the VPN service and local data processing.

### 1.2. Permissions
*   **`BIND_VPN_SERVICE`:** Used strictly for core functionality (network traffic analysis). The app does not function without it.
*   **`QUERY_ALL_PACKAGES`:** Used to attribute network traffic to specific apps. This is a core feature (dashboard visibility) and falls under the "Device search" and "Antivirus/Security" permissible use cases.
*   **`POST_NOTIFICATIONS`:** Used for the mandatory foreground service notification.

### 1.3. VpnService Policy
*   The app uses `VpnService` to create a local tunnel.
*   **Encryption:** While traffic is not tunneled to a remote server, internal handling uses secure memory buffers.
*   **Disclosure:** The app's description and first-run dialog clearly state that a local VPN is used for monitoring purposes.

### 1.4. Monetization
*   The app is currently free with no ads.
*   No deceptive ads or subscription traps are present.

## 2. Testing Checklist

### 2.1. Functional Testing
- [ ] **VPN Activation:** Verify VPN starts successfully and the key icon appears in the status bar.
- [ ] **Traffic Capture:** Verify real-time traffic (DNS queries) appears in the "Live Traffic" tab.
- [ ] **App Attribution:** Verify traffic is correctly linked to the generating app (e.g., Chrome, YouTube).
- [ ] **Background Stability:** Ensure the VPN remains active when the app is minimized or the screen is off.
- [ ] **Reboot Persistence:** Verify the VPN (if configured to auto-start) resumes after a device reboot.

### 2.2. Performance Testing
- [ ] **Throughput:** Measure internet speed with VPN ON vs. OFF. (Expect minimal degradation due to local processing).
- [ ] **Battery Usage:** Monitor battery drain over a 2-hour period of active monitoring.
- [ ] **Memory Usage:** Ensure the app does not exceed 150MB of RAM during heavy traffic.
- [ ] **Latency:** Check for introduced latency in DNS resolution.

### 2.3. Security & Error Handling
- [ ] **Malformed Packets:** Send garbage data to the TUN interface (simulated) and verify the service does not crash.
- [ ] **Permission Denial:** Revoke VPN permission in system settings and verify the app handles it gracefully (stops service, updates UI).
- [ ] **Consent Flow:** Verify the app does not start monitoring until the user accepts the consent dialog.

### 2.4. Policy Verification
- [ ] **Data Exfiltration:** Inspect network traffic (using a separate proxy) to ensure NO data is sent to NetFlow servers.
- [ ] **Notification:** Verify the persistent notification is visible and cannot be dismissed while the VPN is active.
- [ ] **Child Safety:** Ensure no inappropriate content is displayed (content is strictly technical network logs).

## 3. Implementation Details

### 3.1. Error Handling Improvements
*   Added `try-catch` blocks in `VpnPacketLoop` to handle buffer overflows and malformed IP headers.
*   Implemented strict bounds checking in `PacketParser`.
*   Added logging for critical failures to aid in local debugging (without uploading logs).

### 3.2. User Experience
*   Added a "Privacy & Data Usage" consent dialog on first launch.
*   Improved the clarity of the VPN notification string.
