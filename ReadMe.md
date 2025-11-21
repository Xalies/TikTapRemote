# **TikTap Remote** 

# **Turn any Bluetooth remote or physical button into a touch automation tool.**

TikTap Remote is an Android application that allows users to map hardware inputs (such as Bluetooth camera shutters, volume keys, Gimbals or headset buttons) to simulate touch gestures on the screen. It is designed for hands-free interaction with apps like TikTok, Instagram Reels, Tinder, or ebook readers or any other app that you could like to semi automate simulating gestures.

## **Core Features**

### **1\. Hardware Key Mapping**

* Intercepts hardware key events (Volume Up/Down, Media Keys, Bluetooth Shutter buttons).  
* **Exclusive Mode:** Can block the original system function (e.g., prevents the volume slider from appearing) so the button acts *only* as a trigger. Profiles with this enabled get a notification to toggle for system control

### **2\. Gesture Engine**

The app can perform a wide variety of Accessibility gestures:

* **Single Tap:** Simulates a finger tap at a specific X,Y coordinate.  
* **Double Tap:** Simulates a quick double-tap.  
* **Swipes:** Perform swipes (Up, Down, Left, Right) for scrolling feeds.  
* **Gesture Recording:** Users can draw complex paths on the screen, save them, and replay them via the remote. (Upto 5 seconds)

### **3\. App-Specific Profiles**

* **Global Profile:** Default actions that apply everywhere. (app profiles take priority)  
* **App Profiles:** Define different behaviors for specific apps.
  * *Example:* "Volume Up" can mean "Swipe Up" in TikTok, or "Doube Tap" in another app.  
* **Smart Detection:** The app automatically switches profiles when you open a target app (supports fullscreen/immersive games).

### **4\. Visual Targeting System**

* **Drag-and-Drop Targeting:** Uses a system overlay (crosshair) to precisely set where the simulated tap/swipe should occur .  
* **Live Recording Overlay:** Draws over other apps to record custom gesture paths in real-time.

### **5\. Advanced Automation**

* **Double Press Triggers:** Map a different action to double-clicking the hardware button, have two fuctions on the one button.  
* **Repeat Mode:** Holding your selected input for 1 second toggles repeat mode to perform the action every few seconds (configurable interval, 3-30 seconds).

## **Technical Architecture**

* **Language:** Kotlin  
* **UI Framework:** Jetpack Compose  
* **Target SDK:** Android 12+ (API 31+)  
* **Core Mechanism:** Android AccessibilityService

### **How it Works**

1. **KeyEvent Interception:** The Accessibility Service listens for specific key codes. If a profile matches the active app, it consumes the event.  
2. **Gesture Dispatch:** Using dispatchGesture and GestureDescription, the app injects touch events directly into the system.  
3. **Overlay Manager:** Uses WindowManager to draw targeting crosshairs and recording canvases directly over external apps, ensuring they remain visible even during app transitions or in immersive mode.

## **User Guide**

### **Setup**

1. **Permissions:** On launch, grant **Overlay Permission** (to draw the targeter) and enable the **TikTap Accessibility Service** (to perform clicks).  
2. **Service Toggle:** Ensure the master switch in the Main Menu is **ON**.

### **Creating a Profile**

1. Tap the **(+)** button on the main screen.  
2. Select the app you want to control (e.g., TikTok).  
3. **Select Input:** Press the button on your Bluetooth remote to map it.  
4. **Choose Action:** Select "Swipe Up" (for scrolling) or "Tap".  
5. **Set Point (If Tapping):**  
   * Click "Set Point".  
   * The target app will launch with a Crosshair Overlay.  
   * Drag the crosshair to the desired location (e.g., the "Like" heart).  
   * Tap "Confirm Target".

### **Recording a Gesture**

1. In the Profile setup, select **Record** as the action.  
2. Tap **Record Gesture**.  
3. The target app opens with a drawing canvas.  
4. Draw your swipe or pattern on the screen.  
5. Tap **Save**.

## **Tier System**

The app operates on a tiered feature model:

| Feature | Free | Essentials | Pro Saver / Pro |
| :---- | :---- | :---- | :---- |
| **Global Profile** | ✅ | ✅ | ✅ |
| **App Profiles** | No | 2 Profiles | Unlimited |
| **Input Trigger** | Volume Up (most remotes) | Configurable | Configurable |
| **Triggers** | Single Press | Single \+ Double | Single \+ Double |
| **Actions** | Tap, Swipe Up | \+ Swipe Down/Double Tap | \+ Swipe L/R, **Recording** |
| **Repeat Mode** | ❌ | ❌ | ✅ |
| **Ads** | Yes | No | Yes / No |

## **Troubleshooting & Notes**

* **"I can't see the overlay":**  
  * Ensure "Display over other apps" permission is granted.  
  * The app uses a high-priority window type to ensure it appears over games.  
* **"Volume still changes":**  
  * Enable **Exclusive Mode** in the profile settings. This tells the system to ignore the key press after TikTap handles it.  
* **"Not working in games":**  
  * The service is configured to detect interactiveWindows. If a game runs in deep immersive mode, try tapping the screen once to wake the UI, then use the remote.