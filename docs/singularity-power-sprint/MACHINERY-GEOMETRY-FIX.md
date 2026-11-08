# Machinery geometry diagnosis

Field-generator adapters split the accepted nine named elements into a three-element
base and six independently selected overlays. The base alone was always present;
the multipart omitted all four field lobes/caps at field=0, the power lens at
power=0, and the activation lens when inactive. The new `field0_lobes`,
`power0_lens`, and `inactive_lens` adapter models use the corresponding named
elements from the accepted `generation_singularity_containment/default` model.
Their geometry, bounds, and face/UV data are preserved; the inactive lens is
unshaded. Existing powered/active overlays and runtime property mappings are
unchanged. Every state tuple now composes the same nine physical elements once.

The PA FaceBakery test also checks baked vertex winding against packed normals,
but was not executed in this change. Thus no PA winding cause or fix is claimed;
it still requires native test execution. No global culling or render-state change
was made.
