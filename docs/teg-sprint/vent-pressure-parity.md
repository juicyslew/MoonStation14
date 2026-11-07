# Vent pump pressure release parity

The pinned `GasVentPumpComponent.cs` defaults map to the local resolved `GasVentPump` profile: pump power 2, checks `EXTERNAL`, and internal bound 0 kPa. Its `GasVentPumpSystem.OnAtmosDeviceUpdate` uses `dt * TargetPressureChange`, gates external release by `min(pipePressure * PumpPower, ExternalPressureBound) - roomPressure`, and computes internal removable moles from the source pipe temperature. Local live target pressure remains an intentional Minecraft safety cap; the editable external-pressure setting supplies the external bound. With external checks disabled, that bound does not constrain destination pressure. Local pipe volume, finite transaction, and paid-interval ownership remain authoritative.

Sources: pinned SS14 `Content.Shared/Atmos/Gas/GasVentPumpComponent.cs` and `Content.Server/Atmos/Piping/Unary/GasVentPumpSystem.cs`; reference revision `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
