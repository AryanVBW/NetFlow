# Privacy Policy for NetFlow Predict

**Last Updated: 2026-02-26**

This Privacy Policy explains how NetFlow Predict ("we," "our," or "the App") collects, uses, and protects your information. NetFlow Predict is designed as a privacy-first network monitoring tool that processes data locally on your device.

## 1. Data Collection and Usage

### 1.1. Local Processing
NetFlow Predict operates primarily on your device. All network traffic analysis, app identification, and risk assessment are performed locally. We do **not** upload your browsing history, DNS queries, or traffic logs to any cloud server.

### 1.2. VPN Service Usage
The App uses the Android `VpnService` API to create a local Virtual Private Network (VPN) interface. This is required to:
*   Capture network packets for traffic analysis.
*   Identify which apps are consuming data.
*   Block connections to known tracking or malicious domains (if enabled).

**Important:** This local VPN does **not** route your traffic to a remote server. It is a loopback interface used solely for on-device monitoring.

### 1.3. Information We Collect
We do not collect personal information (PII) such as your name, email address, or phone number.

The App stores the following data **locally on your device** in a secure database:
*   **App Usage Stats:** Package names, data usage (bytes sent/received), and connection counts.
*   **Network Logs:** Timestamps, destination IP addresses, domain names (DNS queries), and port numbers.
*   **Risk Assessments:** Generated risk scores for apps based on their traffic patterns.

### 1.4. Third-Party Services
The App does not integrate with third-party analytics or advertising SDKs (e.g., Google Analytics, Firebase Crashlytics, AdMob). No user data is shared with third parties for marketing or tracking purposes.

## 2. Permissions

The App requests the following sensitive permissions:
*   **VPN Service (`BIND_VPN_SERVICE`):** Essential for capturing and analyzing network traffic.
*   **Query All Packages (`QUERY_ALL_PACKAGES`):** Required to map network traffic to specific installed applications (displaying app names and icons instead of just UIDs).
*   **Notifications (`POST_NOTIFICATIONS`):** Used to display the persistent VPN status notification, which is a requirement of the Android system for foreground services.

## 3. Data Retention
You have full control over your data.
*   **Retention Period:** By default, logs are kept for 30 days. You can adjust this period in the App settings.
*   **Data Deletion:** You can clear all stored data at any time via the "Clear Data" option in Settings. Uninstalling the App will also permanently remove all local data.

## 4. Security
We implement industry-standard security measures to protect your local data.
*   The App's database is sandboxed within the application's private storage.
*   Network traffic is analyzed in memory and discarded (unless logged for statistics), ensuring minimal exposure of sensitive payloads.

## 5. Changes to This Policy
We may update our Privacy Policy from time to time. We will notify you of any changes by posting the new Privacy Policy on this page and updating the "Last Updated" date.

## 6. Contact Us
If you have any questions or suggestions about our Privacy Policy, do not hesitate to contact us at privacy@netflowpredict.com.
