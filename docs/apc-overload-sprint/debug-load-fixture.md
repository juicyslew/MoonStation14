# High-Load Test Lamp: compact APC overload fixture

Creative-only debug block `high_load_test_lamp` (no survival recipe): it requests
12,000 W whenever connected to an APC/LV floor wire, **not only while lit**. Its
ordinary-lamp nearest-cable connection and LIT state emit Minecraft light level
14. The ordinary `power_lamp` remains 100 W. Both use the same existing
glowstone-backed lamp model and are visually indistinguishable except for the
label; this fixture adds no texture, bespoke art, or recipe.

```text
[25 kW fixed HV source] -- HV -- [substation, 90%] -- MV -- [APC + battery]
                                                          |
                                                  LV floor cable spine
                                                   /              \
                                            [12 kW test]      [12 kW test]
```

The source offers 25 kW, the substation delivers 22.5 kW, and two connected test
lamps request 24 kW. With charge available, the APC battery supplies the
remaining 1.5 kW; actual APC output is 24 kW, above
the strict 20 kW protection limit. After **more than** three continuous
seconds of known loaded output, the breaker trips and both lamps go dark.
Connected demand remains 24 kW even when the lamps are dark after the trip;
an open breaker does not deliver APC output. One connected 12 kW lamp alone
stays below the threshold and cannot trigger it.

Owner manual check (not yet performed): enable the power simulation; in creative,
use the ready source and charged APC battery with the matching HV/MV cables and
APC/LV floor wire. Place **two** High-Load Test Lamps near that floor wire and
APC, wait more than three seconds of known 24 kW output, and check the UI's
tripped status and open breaker. Remove one lamp, manually reclose, and confirm
the breaker stays closed with the remaining 12 kW lamp. No 298-lamp setup is
needed. Connected one- and two-lamp wired debug tests each passed individually
within full server runs; those runs did **not** pass their full required suite
because of unrelated atmosphere/cable whole-chunk assertions. See
[current-handoff.md](current-handoff.md) for the validation boundary.
