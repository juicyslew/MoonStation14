# Native model resource loading corrections

Minecraft model IDs omit the `models/` directory; the native loader inserts it
when resolving an ID. Canister component IDs and collector BER IDs/plans now use
`moonstation14:block/...`, matching `GasCanisterRenderer.additionalModels()` and
the existing `AirlockBlockEntityRenderer.drawComponent` path.

The PA end cap has only accepted static completed art, so all four facings and
all seven registered visual enum values bind to that same accepted completed
model. This is presentation fallback only. The gas-canister proxy inventory
model reuses the existing imported plasma-tank inventory icon; the entity continues to draw
its selected canister appearance through its existing renderer.

`en_us.json` supplies the missing item translation entries for the registered
radiation suit and hood items (the held ItemStack name derives from those item
translation keys; prototype composition is unchanged).
