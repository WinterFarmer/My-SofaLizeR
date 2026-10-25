# 🎧 My SofaLizeR

**My SofaLizeR** is a lightweight Android application designed to render multichannel audio from `.mkv` files into immersive binaural stereo surround sound using the **sofalizer** audio filter (HRTF), with optional **AutoEQ** integration.

Built for personal use to enjoy high-quality spatial audio on a modest Android setup without needing real-time heavy processing during playback.

---

## ✨ Key Features

- **Binaural Surround Rendering:** Converts multichannel audio tracks (5.1, 7.1, etc.) into 2-channel binaural stereo using custom `.sofa` HRTF files.
- **AutoEQ Support:** Apply parametric/graphic EQ configurations (`.txt`) directly during the rendering process to match your specific headphones/IEMs.
- **Flexible Output Formats:**
  - **MKV:** Remuxes the newly rendered stereo audio back into the video container.
  - **FLAC:** Extracts and outputs a standalone, lossless stereo audio file (`.flac`).

---

## 🚀 How to Use

Using the app is straightforward and only takes a few steps:

1. **Select Input Video:** Pick your source `.mkv` file containing multichannel audio.
2. **Load HRTF File (`.sofa`):** Select your preferred `.sofa` HRTF dataset. 
   > 💡 *You can download various HRTF `.sofa` files from [sofaconvention.org](https://www.sofaconventions.org/).*
3. **Load AutoEQ Config (`.txt`) *(Optional)*:** Select a `.txt` equalization file tailored for your headphone/IEM model.
   > 💡 *You can generate and download your EQ profile from [autoeq.app](https://autoeq.app/).*
4. **Choose Output Mode:** Select whether you want the final output saved back as a video (`.mkv`) or as a standalone lossless audio file (`.flac`).
5. **Render:** Tap **Render** and wait for the process to finish.

---

## 📥 Download & Installation

No need to build from source! The pre-built `My SofaLizeR.apk` is ready to install:

👉 **[Download the latest APK from Releases](../../releases)**

---

## 📝 Notes

- Rendering speed depends on your device's CPU performance and the duration of the source `.mkv` file.
- For the best binaural 3D audio experience, listen to the rendered output using stereo headphones or IEMs (disable any built-in phone Dolby/spatial effects to avoid double-processing).
