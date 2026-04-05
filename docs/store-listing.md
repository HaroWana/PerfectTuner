# PerfectTuner — Play Store Listing

---

## Section 1: Short Description (max 80 characters)

> Precise strobe guitar tuner. Free tunings included, full library unlock available.

**Character count: 79 characters** ✓

---

## Section 2: Long Description (max 4000 characters)

> Most guitar tuners show you a needle and a number. PerfectTuner shows you a strobe — the same visual tuning reference that studio techs and luthiers have used for decades. When the ring stops moving, you're in tune.
>
> **What's included:**
>
> - Strobe ring display — rotating rings slow to a stop as you approach perfect pitch; instant visual feedback at a glance
> - Auto string detection — the app identifies which string you're playing automatically; no manual selection needed
> - Accurate to ±0.1 cents — more than enough precision for professional intonation work
> - Adjustable A4 reference pitch (430–450 Hz) — match any ensemble or instrument standard
> - Chromatic mode — tune any instrument, not just guitar
>
> **Included free:**
>
> - Standard (EADGBe)
> - Drop D
> - Open G
> - Open D
> - DADGAD
> - Eb Standard
>
> **Full library unlock (one-time purchase, never expires):**
>
> 35+ alternate and open tunings — CGDGCD, Open E, Open A, Double Drop D, Nashville Tuning, and many more. Buy once, use forever, no subscription.
>
> **What's not here:**
>
> - No ads. Not ever.
> - No subscription. One-time unlock, no expiry.
> - No account required. No sign-in, no email.
> - No data collected. Microphone audio is processed on-device and never stored or transmitted.
>
> Built for players who care about intonation.

**Approximate character count: ~1350 characters** ✓ (well under 4000)

---

## Section 3: Screenshot Capture Specification

Capture the following screens in order. Minimum required: screenshots 1 and 2.

### Screenshot 1 — Main tuner (in-tune state)
- **What to show:** Strobe ring in a stable/stopped state, cent display reading "0¢" or close (±2¢), string indicator showing "E2" (or any detected string)
- **Dimensions:** 1080 × 1920 px (9:16 portrait)
- **Format:** JPEG or 24-bit PNG, no alpha channel
- **File label:** `screenshot-01-in-tune.png`
- **Tip:** Play the low E string on a guitar, let the tuner stabilize, then take the screenshot while the ring is barely moving or stopped

### Screenshot 2 — Tuning picker (locked + free rows)
- **What to show:** Bottom sheet open, Standard tuning row with a checkmark (currently selected), locked tuning rows below with lock icons visible
- **Dimensions:** 1080 × 1920 px (9:16 portrait)
- **Format:** JPEG or 24-bit PNG, no alpha channel
- **File label:** `screenshot-02-tuning-picker.png`
- **Tip:** Tap the tuning name on the main screen to open the sheet, then screenshot before selecting anything

### Screenshot 3 — Settings screen (optional)
- **What to show:** Settings screen with A4 reference slider and "Restore Purchases" row visible
- **Dimensions:** 1080 × 1920 px (9:16 portrait)
- **Format:** JPEG or 24-bit PNG, no alpha channel
- **File label:** `screenshot-03-settings.png`
- **Tip:** Navigate to Settings from the main screen

**Device:** Any Android phone with a 1080 × 1920 screen, or an Android emulator configured to that resolution.

---

## Section 4: Feature Graphic Specification

The feature graphic is **required** to publish — Play Console will not let you submit without it.

- **Dimensions:** 1024 × 500 px
- **Format:** JPEG or 24-bit PNG, no alpha channel
- **Suggested content:** Dark background (#121212), "PerfectTuner" centered in white text (large, clean sans-serif), optional strobe ring graphic element beneath or around the title
- **File label:** `feature-graphic-1024x500.png`
- **Tools:** Canva, Figma, or any image editor. Export at exactly 1024 × 500 px.

> Note: Keep the design simple and readable — Play Store displays this graphic at various sizes and it must remain legible when scaled down.

---

## Section 5: Play Console Setup Checklist

Follow these steps in Play Console to complete the store listing and prepare for submission.

1. **Create app** → Music & Audio → Free → Contains in-app purchases
2. **Create in-app product:**
   - Products → In-app products → Create product
   - Product ID: `unlock_full_library` _(must match exactly — this is what the app code checks)_
   - Name: "Full Tuning Library"
   - Description: "Unlock 35+ alternate and open tunings. One-time purchase, never expires."
   - Set price (e.g. $2.99 / €2.99 — your choice)
   - Status: **Active** _(product must be Active before purchases can be tested)_
3. **Add test account:**
   - Play Console → Settings → License testing
   - Add your Google account (the one on your test device) as a license tester
   - This allows you to test the purchase flow without real charges
4. **Upload signed AAB to Internal Testing track:**
   - Required before in-app purchases can be tested end-to-end
   - Build → Generate Signed Bundle/APK → Android App Bundle → release keystore
   - Upload at Testing → Internal testing → Create new release
5. **Complete Data Safety form:**
   - Go to App content → Data safety
   - Answer: Does your app collect or share any of the required user data types? → **No**
   - Microphone: select "Not collected" — audio is processed on-device only and never stored or transmitted
6. **Add privacy policy URL:**
   - App content → Privacy policy
   - Enter the GitHub Pages URL for `privacy-policy.html` (see Section 6 below)
7. **Complete IARC content rating questionnaire:**
   - App content → App ratings → Start questionnaire
   - Expected rating: **Everyone** (no mature content, no user-generated content, no ads)
8. **Upload store assets:**
   - Main store listing → Graphics
   - Upload at least 2 screenshots (screenshots 1 and 2 from Section 3)
   - Upload feature graphic (1024 × 500 px from Section 4)
   - Confirm app icon (512 × 512 px) is present — should already be set
9. **Add store listing text:**
   - Main store listing → Store listing details
   - Paste short description from Section 1
   - Paste long description from Section 2
10. **Submit for review:**
    - Publishing → Countries/regions → Add countries (select all, or target regions)
    - Release → Internal testing → Promote to Production (or submit directly to Production)
    - First review typically takes **1–3 business days**

---

## Section 6: Privacy Policy Hosting (GitHub Pages)

The `docs/privacy-policy.html` file is ready to be hosted on GitHub Pages at no cost.

**Steps to enable:**

1. Push the `docs/` folder to the `main` branch of your GitHub repository
   ```
   git add docs/
   git commit -m "docs: add privacy policy for Play Store"
   git push origin main
   ```
2. In your GitHub repository, go to **Settings → Pages**
3. Under "Source", select: **Deploy from a branch**
4. Branch: `main` | Folder: `/docs`
5. Click **Save** — GitHub will deploy within a minute or two
6. Your privacy policy URL will be:
   ```
   https://[your-github-username].github.io/[repo-name]/privacy-policy.html
   ```
   Example: `https://jlorber.github.io/PerfectTuner/privacy-policy.html`

7. Enter this URL in Play Console under **App content → Privacy policy**

> Before going live, update the placeholder email `privacy@perfecttuner.app` in the HTML file to a real address you monitor.
