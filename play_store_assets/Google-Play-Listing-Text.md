# NetFlow - Google Play Store Listing Details

## App Identity
**App Name:** NetFlow: Secure Local VPN & Traffic Monitor
**(Alternative Name if too long):** NetFlow: Network Analytics

**Short Description (80 chars max):**
See every connection. Predict network risks with a secure, local-only VPN monitor.

## Long Description (4000 chars max)
**NetFlow** is your ultimate open-source application for understanding and predicting network traffic risks directly on your device. Designed with complete transparency and an absolute commitment to your privacy, NetFlow reveals exactly what your installed apps are doing behind the scenes.

Using a secure, on-device transparent proxy, NetFlow monitors all incoming and outgoing connections in real-time. It analyzes bandwidth usage, uncovers hidden data transfers, and employs intelligent risk prediction to keep you safe—all without ever sending your data to remote servers.

### 🛡️ Core Features
*   **Live Traffic Monitoring:** Watch your TCP, UDP, and DNS connections in real-time. See live upload and download speeds, active sessions, and detailed payload statistics.
*   **Per-App Analytics:** Wondering which app is draining your data? NetFlow maps every connection back to the specific application, giving you total visibility into your device's network behavior.
*   **Predictive Risk Analysis:** NetFlow uses algorithmic scoring to identify potentially dangerous ports, suspicious IP addresses, and unusual behavioral patterns, warning you before risks escalate.
*   **Ad & Tracker Blocking (Optional):** Define custom rules or use pre-configured lists to block known domains associated with ads, tracking, and malware directly at the network level.
*   **Dark Mode & Theming:** A beautiful, responsive interface built with Material Design 3, including full Light/Dark mode support.

### 🔒 Absolute Privacy Guarantee
NetFlow was built on the principle that your data belongs to you. 
*   **Zero Remote Proxying:** The app utilizes the Android `VpnService` exclusively as a *local loopback* to capture and analyze packets directly on your phone. Your traffic is never routed to external VPN servers.
*   **Zero Data Logging:** We do not collect, store, or transmit your IP address, browsing history, or personal information. 
*   **100% Open Source:** Don't just take our word for it—verify our claims. Our entire codebase is publicly available for auditing.

### 📋 Important Permissions & Policy Transparency
To function as a network monitor, NetFlow strictly requires the following:
*   **VPN Service:** NetFlow establishes a local VPN tunnel (`VpnService`) strictly to intercept and analyze network packets locally for the purpose of traffic monitoring and risk prediction.
*   **Query All Packages:** We require the `QUERY_ALL_PACKAGES` permission solely to match the intercepted network traffic to the specific apps installed on your device, allowing you to see which app is making which connection.

### 🤝 Connect With Us
*   **Source Code & Auditing:** [https://github.com/AryanVBW/NetFlow](https://github.com/AryanVBW/NetFlow)
*   **Support & Feedback:** aryanvbw@gmail.com

Take control of your device's network today. Download NetFlow and see what you've been missing.
