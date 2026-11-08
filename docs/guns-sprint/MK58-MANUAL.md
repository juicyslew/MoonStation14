# Mk58 retest

Restart the client/server with the updated build before retesting. Use the controlled
character body, not Creative. Defaults below may be rebound.

1. Enable combat mode through **Ctrl+K → Toggle combat mode**, then close Actions.
2. Hold the Mk58, **Z** to chamber, then fire with **left-click**. Repeat several
   separate Z presses; each accepted rack should sound. A live round ejected by
    racking must remain recoverable; fired spent cases should pop out, bounce/roll,
    sound on landing and remain until cleaned.
3. Check both hands: **X** switches hands. The gun should aim forward and its ammo
   display should follow its hand. When holding a gun and pistol magazine together,
   each count should follow its own hand; the magazine should show current/capacity.
4. Tap **Left Alt** for the pointer. Right-click the active gun's hand icon. Check
   compact size, title dragging, close button, and retention while returning to
   captured-cursor movement/look. Free the cursor again to operate the window.
5. Eject the magazine. With a replacement magazine active, click the pistol in the
   other occupied hand; this should insert it, not select the pistol. Alternatively,
   drag the magazine onto the pistol from either hand. No ground placement is needed.
6. **X** back to the gun, **Z** to chamber an empty gun, then fire. Refill a removed
   magazine by using compatible pistol cartridges on its hand icon; check the count.
   An initialized empty magazine must not refill itself when reinserted.

An empty trigger should click without an empty-weapon text banner.

## Whole-set loading and controls

To lower a two-handed gun without using its rack/cycle/fire-mode action, use the
direct wield toggle (Ctrl+Z). It toggles wield and unwield; source hand and the
two-hand reservation remain server-authoritative. Plain **Z** remains the held
item's normal use (rack/cycle/mode); **Shift+Z** and **Alt+Z** retain medical
controls. Keep **K**, **Shift+K**, **Ctrl+K**, and **Ctrl+J** unchanged.

For loading, **Ctrl+Z** to unwield. Put a **shotgun shell** or **light rifle cartridge**
in the active hand and the Kammerer or Mosin in the other. Click the gun's hand icon
to insert one round; alternatively, drag the ammunition onto the gun. Repeat per
round. **X** returns to the gun; use **Ctrl+Z** to wield it again if needed. The Mosin
starts with 10 logical rounds. Cycling ejects a round; it is not required after each shot.

Energy Shotgun charge is 480 (80 lethal or 48 disabler per trigger); Retro Laser is
1000 (62.5 per trigger); Svalinn's small cell is 360 (50 per trigger). These guns do
not recharge themselves. Aim at a powered **Weapon capacitor recharger** and press
**E** with the unwielded gun/cell active to insert it. Press **E** with an empty
active hand to retrieve it. Svalinn can charge with its cell installed. A held or
examined standalone cell displays charge; see [charging.md](charging.md) for setup.
