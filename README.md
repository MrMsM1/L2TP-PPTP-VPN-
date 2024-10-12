# VPN Connection Service (L2TP/PPTP)

This project is a VPN connection service that facilitates secure connections through L2TP and PPTP protocols. The service runs indefinitely in the background and uses a custom Android SDK to simplify access to hidden APIs, avoiding the complexity of reflection.

## How It Works

The service is responsible for establishing and managing VPN connections based on user and server input. It supports two main VPN protocols:

- **L2TP**
- **PPTP**

When the service receives a request (via MQTT), it processes the input to establish a VPN connection by:

1. Receiving user information (e.g., username, password) and the server address.
2. Based on the type of VPN (L2TP or PPTP), the service configures the VPN connection profile.
3. After setting up the profile, the service starts the VPN connection using Android's connectivity service.
4. The service continuously monitors the connection status and reconnects if it becomes disconnected.

## Custom Android SDK for Accessing Hidden APIs

One of the key components of this project is the use of a customized Android SDK to access hidden VPN APIs, such as `IConnectivityManager`. This approach ensures better integration and reduces the need for reflection, which typically makes code more complex and harder to implement.
You can find the custom SDK used in this project here: [Custom Android SDK for VPN](https://github.com/Reginer/aosp-android-jar/blob/main/android-30/android.jar).
## Service Nature

The VPN connection manager is designed as a **long-running service**. It operates in the background and restarts automatically after a reboot or disconnection. This persistent nature makes the service reliable for maintaining continuous VPN connections, even in dynamic network conditions.

## Permissions

The Android application requires several permissions, which are defined in the `AndroidManifest.xml` file:

- **INTERNET**: To establish connections over the network.
- **WRITE_SECURE_SETTINGS**: To configure and control network settings.
- **ACCESS_NETWORK_STATE**: To check the status of network connectivity.
- **CONTROL_VPN**: To control VPN connections directly.
- **NETWORK_SETTINGS**: To access advanced network settings.
- **CONTROL_ALWAYS_ON_VPN**: To manage VPNs that are always on.
- **FOREGROUND_SERVICE**: To run the service in the foreground, ensuring it stays active.
- **RECEIVE_BOOT_COMPLETED**: To restart the service after the device is rebooted.

These permissions enable the app to create, manage, and monitor VPN connections continuously in a secure and efficient manner.

## Getting Started

1. Clone the repository and open it in your preferred IDE.
2. Set up the necessary VPN server information (e.g., L2TP or PPTP credentials).
3. Ensure the required permissions are granted in the `AndroidManifest.xml` file.

## Running the Service

The service starts automatically upon boot or can be manually started from within the app's main activity. It uses MQTT to handle VPN requests and establish connections based on the incoming messages.

## Contributing

Feel free to fork this repository and make contributions via pull requests.

